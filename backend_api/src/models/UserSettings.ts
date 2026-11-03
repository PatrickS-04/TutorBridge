import mongoose, { Schema, Document, Types } from "mongoose";
import { Locale, Theme } from "./enums";
import { NotificationPreferencesSchema, INotificationPreferences } from "./embedded";

//This represents the «collection: user_settings» UserSettings stated in design document

export interface IUserSettings extends Document {
  userId: Types.ObjectId;
  language: Locale;
  notifications: INotificationPreferences;
  biometricEntry: boolean;
  defaultAreaId: string;
  theme: Theme;
  updatedAt: Date;
}

const UserSettingsSchema = new Schema<IUserSettings>(
  {
    userId: { type: Schema.Types.ObjectId, ref: "User", required: true, unique: true },
    language: { type: String, enum: Object.values(Locale), default: Locale.EN },
    notifications: { type: NotificationPreferencesSchema, default: () => ({}) },
    biometricEntry: { type: Boolean, default: true },
    // Pre-fills the area on a student's new tuition request. Empty means no default.
    defaultAreaId: { type: String, default: "" },
    theme: { type: String, enum: Object.values(Theme), default: Theme.SYSTEM },
  },
  { timestamps: { createdAt: false, updatedAt: true } }
);

export const UserSettings = mongoose.model<IUserSettings>("UserSettings", UserSettingsSchema);
