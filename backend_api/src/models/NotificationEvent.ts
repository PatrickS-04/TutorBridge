import mongoose, { Schema, Document, Types } from "mongoose";
import { NotificationCategory, Locale } from "./enums";

//This represents the «collection: notification_events» NotificationEvent stated in design document

export interface INotificationEvent extends Document {
  institutionId: Types.ObjectId;
  userId: Types.ObjectId;
  category: NotificationCategory;
  title: string;
  body: string;
  deepLink?: string;
  locale: Locale;
  pushSentAt?: Date;
  smsSentAt?: Date;
  readAt?: Date;
}

const NotificationEventSchema = new Schema<INotificationEvent>(
  {
    institutionId: { type: Schema.Types.ObjectId, ref: "Institution", required: true, index: true },
    userId: { type: Schema.Types.ObjectId, ref: "User", required: true, index: true },
    category: { type: String, enum: Object.values(NotificationCategory), required: true },
    title: { type: String, required: true },
    body: { type: String, required: true },
    deepLink: { type: String },
    locale: { type: String, enum: Object.values(Locale), default: Locale.EN },
    pushSentAt: { type: Date },
    smsSentAt: { type: Date },
    readAt: { type: Date },
  },
  { timestamps: { createdAt: true, updatedAt: false } }
);

export const NotificationEvent = mongoose.model<INotificationEvent>(
  "NotificationEvent",
  NotificationEventSchema
);
