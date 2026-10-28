import dotenv from "dotenv";
import mongoose from "mongoose";
import admin from "firebase-admin";
import { User, NotificationToken } from "./models";
import { buildMulticast, findStaleTokens } from "./utils/push";

/**
 * Dev tool: sends one push to every device registered to a user, in exactly the
 * data-only shape notify() uses. Unlike a Firebase console test message (which
 * the system displays by itself), this only shows if TutorBridgeMessagingService
 * wakes up and builds the notification - so it is the real test for the
 * backgrounded / swiped-away cases. Nothing is written to notification_events.

 * Run this command in backend_api folder to send yourself a test push notification: npx tsx src/sendTestPush.ts (you@example.com - the current account you are logged into the app with)
 */

dotenv.config();

async function main() {
  const email = process.argv[2]?.trim().toLowerCase();
  if (!email) throw new Error("Usage: npx tsx src/sendTestPush.ts <email>");

  if (admin.apps.length === 0) admin.initializeApp({ credential: admin.credential.applicationDefault() });
  await mongoose.connect(process.env.MONGODB_URI!);

  const user = await User.findOne({ email }).select("_id").lean();
  if (!user) throw new Error(`No TutorBridge user with email ${email}`);

  const tokens = (await NotificationToken.find({ userId: user._id }).select("token").lean()).map((t) => t.token);
  if (tokens.length === 0) throw new Error("That user has no registered devices - sign in on the phone first");

  const response = await admin.messaging().sendEachForMulticast(
    buildMulticast(tokens, {
      title: "TutorBridge test",
      body: `Data-only test push sent at ${new Date().toLocaleTimeString()}. Tap to open Messages.`,
      category: "MESSAGE",
      notificationId: `test-${Date.now()}`,
      deepLink: "tutorbridge://threads/test",
    })
  );

  console.log(`Sent to ${response.successCount}/${tokens.length} device(s)`);
  response.responses.forEach((r, i) => {
    if (!r.success) console.log(`  ...${tokens[i].slice(-12)}: ${r.error?.code} ${r.error?.message}`);
  });
  const stale = findStaleTokens(tokens, response.responses);
  if (stale.length > 0) console.log(`  ${stale.length} token(s) are dead (app uninstalled or signed out); notify() prunes these.`);
}

main()
  .catch((error) => { console.error(error.message ?? error); process.exitCode = 1; })
  .finally(() => mongoose.disconnect());