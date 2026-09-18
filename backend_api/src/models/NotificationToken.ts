import mongoose, { Schema, Document, Types } from "mongoose";

//This represents the «collection: notification_tokens» NotificationToken stated in design document

export interface INotificationToken extends Document {
  userId: Types.ObjectId; // -> User
  token: string; // FCM registration token
  platform: string; // "android"
  lastSeenAt: Date;
}

const NotificationTokenSchema = new Schema<INotificationToken>(
  {
    userId: { type: Schema.Types.ObjectId, ref: "User", required: true, index: true },
    token: { type: String, required: true, unique: true },
    platform: { type: String, default: "android" },
    lastSeenAt: { type: Date, default: Date.now },
  },
  { timestamps: { createdAt: true, updatedAt: false } }
);

export const NotificationToken = mongoose.model<INotificationToken>(
  "NotificationToken",
  NotificationTokenSchema
);
