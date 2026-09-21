import dotenv from "dotenv";
import mongoose from "mongoose";
import { Institution, InstitutionStatus, Subject, Province, Area, User, UserRole, AuthProvider, VerificationStatus, DayOfWeek } from "./models";

/**
 * Dev seed: makes the app testable.
 *
 * Without this the database has institutions and nothing else, which means the
 * sign-up screen works but every screen behind it is empty, and there is no way
 * to become a TUTOR at all: POST /auth/bootstrap only ever creates
 * GUARDIAN_LEARNER, and the one route that creates tutors
 * (POST /institutions/me/tutors) needs an INSTITUTION_ADMIN that nothing can
 * create either.
 *
 * So this seeds the reference data the request/profile screens read, plus a
 * tutor and an admin profile with no firebaseUid. Sign up in the app with one
 * of those emails and /auth/bootstrap links the Firebase identity to the
 * existing profile by email instead of making a new learner - that is the only
 * supported way into the other roles.
 *
 * Idempotent: re-run it whenever. Run with `npm run seed`.
 */

const TUTOR_EMAIL = "tutor@tutorbridge.test";
const ADMIN_EMAIL = "admin@tutorbridge.test";

const WEEKDAY_HOURS = [
  DayOfWeek.MONDAY,
  DayOfWeek.TUESDAY,
  DayOfWeek.WEDNESDAY,
  DayOfWeek.THURSDAY,
  DayOfWeek.FRIDAY,
].map((day) => ({ day, startTime: "09:00", endTime: "17:00" }));

async function main() {
  const uri = process.env.MONGODB_URI;
  if (!uri) throw new Error("MONGODB_URI is not set");
  await mongoose.connect(uri);

  // The app defaults the sign-up dropdown to the first institution by name
  // (GET /reference/institutions sorts by name), so seed into that one:
  // Find-a-Tutor only ever lists tutors at the caller's own institution.
  const institution = await Institution.findOne({ status: InstitutionStatus.ACTIVE }).sort({ name: 1 });
  if (!institution) throw new Error("No ACTIVE institution to seed into");

  const subjects = await Promise.all(
    [
      ["Mathematics", "mathematics"],
      ["Physical Sciences", "physical-sciences"],
      ["Accounting", "accounting"],
      ["Programming", "programming"],
      ["English", "english"],
    ].map(([name, slug]) =>
      Subject.findOneAndUpdate({ slug }, { $setOnInsert: { name, slug } }, { upsert: true, returnDocument: "after" })
    )
  );

  const provinces = await Promise.all(
    ["Western Cape", "Gauteng"].map((name) =>
      Province.findOneAndUpdate({ name }, { $setOnInsert: { name } }, { upsert: true, returnDocument: "after" })
    )
  );

  const areas = await Promise.all(
    ([
      ["Cape Town CBD", provinces[0]!._id],
      ["Bellville", provinces[0]!._id],
      ["Sandton", provinces[1]!._id],
      ["Pretoria East", provinces[1]!._id],
    ] as const).map(([name, provinceId]) =>
      Area.findOneAndUpdate({ name, provinceId }, { $setOnInsert: { name, provinceId } }, { upsert: true, returnDocument: "after" })
    )
  );

  // No firebaseUid and no passwordHash on purpose: Firebase owns the password,
  // and the app's sign-up claims this profile by email on first bootstrap.
  const seedUser = (email: string, fullName: string, role: UserRole, extra: Record<string, unknown> = {}) =>
    User.findOneAndUpdate(
      { email },
      {
        $setOnInsert: {
          email,
          fullName,
          role, // immutable in the schema - an existing user cannot be promoted here
          institutionId: institution._id,
          authProvider: AuthProvider.PASSWORD,
          verificationStatus: VerificationStatus.VERIFIED,
          profileCompleteness: 80,
          ...extra,
        },
      },
      { upsert: true, returnDocument: "after" }
    );

  const tutor = await seedUser(TUTOR_EMAIL, "Test Tutor", UserRole.TUTOR, {
    subjectIds: subjects.slice(0, 3).map((s) => s!._id),
    areaId: areas[0]!._id,
    hourlyRate: 250,
    department: "Faculty of Science",
    staffNumber: "T-0001",
    bio: "Seeded tutor account for local testing.",
  });
  // A tutor with no published hours has no bookable slot on any date, so the
  // student booking flow dead-ends on every day of the calendar. Only filled
  // when empty, so hours a tutor sets in the app are never clobbered.
  await User.updateOne(
    { email: TUTOR_EMAIL, availability: { $size: 0 } },
    { $set: { availability: WEEKDAY_HOURS } }
  );

  const admin = await seedUser(ADMIN_EMAIL, "Test Admin", UserRole.INSTITUTION_ADMIN);

  console.log(`institution : ${institution.name}`);
  console.log(`subjects    : ${subjects.length}   provinces: ${provinces.length}   areas: ${areas.length}`);
  console.log(`tutor       : ${tutor!.email} (role ${tutor!.role}, ${tutor!.firebaseUid ? "already claimed" : "unclaimed"})`);
  console.log(`admin       : ${admin!.email} (role ${admin!.role}, ${admin!.firebaseUid ? "already claimed" : "unclaimed"})`);
  console.log(`\nSign UP in the app with one of those emails and any password to sign in as that role.`);

  await mongoose.disconnect();
}

dotenv.config();
main().catch((error) => {
  console.error(error);
  process.exit(1);
});
