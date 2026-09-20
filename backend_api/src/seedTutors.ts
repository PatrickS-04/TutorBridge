import dotenv from "dotenv";
import fs from "fs";
import admin from "firebase-admin";
import mongoose from "mongoose";
import { Institution, User, UserRole, AuthProvider, VerificationStatus } from "./models";

/**
 * Pre-provisions tutors from a CSV so they can just sign in.
 *
 *   npm run seed:tutors -- tutors.csv
 *
 * CSV header: email,fullName,password,institution,staffNumber,department
 * (institution = the institution's exact name; no commas inside fields.)
 *
 * Per row: creates the Firebase account with the given password, then the
 * TUTOR profile in Mongo already linked to it. Idempotent: an existing
 * Firebase account is left alone, so a password the tutor has since changed
 * is never reset. Tutors change it in the app (Settings > Change password).
 *
 * The CSV holds plaintext passwords - keep it out of git (see .gitignore).
 */

async function main() {
  const file = process.argv[2];
  if (!file) throw new Error("Usage: npm run seed:tutors -- <tutors.csv>");
  const uri = process.env.MONGODB_URI;
  if (!uri) throw new Error("MONGODB_URI is not set");

  const [header, ...lines] = fs.readFileSync(file, "utf8").split(/\r?\n/).filter((l) => l.trim());
  const cols = header!.split(",").map((c) => c.trim());
  const rows = lines.map((l) => {
    const cells = l.split(",").map((c) => c.trim());
    return Object.fromEntries(cols.map((c, i) => [c, cells[i] ?? ""]));
  });

  admin.initializeApp({ credential: admin.credential.applicationDefault() });
  await mongoose.connect(uri);

  for (const r of rows) {
    const email = r.email!.toLowerCase();
    try {
      const institution = await Institution.findOne({ name: r.institution });
      if (!institution) throw new Error(`no institution named "${r.institution}"`);
      if (!r.fullName) throw new Error("fullName is required");

      let fbUser: admin.auth.UserRecord;
      let created = false;
      try {
        fbUser = await admin.auth().getUserByEmail(email);
      } catch (e: any) {
        if (e.code !== "auth/user-not-found") throw e;
        if ((r.password ?? "").length < 6) throw new Error("password must be at least 6 characters");
        fbUser = await admin.auth().createUser({ email, password: r.password, displayName: r.fullName, emailVerified: true });
        created = true;
      }

      // role is immutable in the schema, so an existing non-tutor with this email is an error, not a promotion.
      const user = await User.findOneAndUpdate(
        { email },
        {
          $set: { firebaseUid: fbUser.uid },
          $setOnInsert: {
            email,
            fullName: r.fullName,
            role: UserRole.TUTOR,
            institutionId: institution._id,
            authProvider: AuthProvider.PASSWORD,
            verificationStatus: VerificationStatus.VERIFIED,
            profileCompleteness: 40,
            staffNumber: r.staffNumber || undefined,
            department: r.department || undefined,
          },
        },
        { upsert: true, returnDocument: "after" }
      );
      if (user!.role !== UserRole.TUTOR) throw new Error(`existing user is ${user!.role}, not TUTOR`);
      console.log(`ok    ${email}  (firebase ${created ? "created" : "already existed"})`);
    } catch (e: any) {
      console.error(`FAIL  ${email}  ${e.message}`);
    }
  }

  await mongoose.disconnect();
}

dotenv.config();
main().catch((error) => {
  console.error(error);
  process.exit(1);
});
