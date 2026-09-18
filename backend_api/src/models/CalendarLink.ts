import mongoose, { Schema, Document, Types } from "mongoose";

//This represents the «collection: google_calendar_links» CalendarLink stated in design document
//One per user 
//refreshTokenEnc is the encrypted Google OAuth refresh
//token used to write booking events and read back hours worked

export interface ICalendarLink extends Document {
  userId: Types.ObjectId;
  googleAccountEmail: string;
  calendarId: string;
  refreshTokenEnc: string;
  scopes: string[];
  syncEnabled: boolean;
  lastSyncedAt?: Date;
  lastSyncError?: string;
}

const CalendarLinkSchema = new Schema<ICalendarLink>(
  {
    userId: { type: Schema.Types.ObjectId, ref: "User", required: true, unique: true },
    googleAccountEmail: { type: String, required: true },
    calendarId: { type: String, default: "primary" },
    refreshTokenEnc: { type: String, required: true, select: false },
    scopes: { type: [String], default: ["calendar.events"] },
    syncEnabled: { type: Boolean, default: true },
    lastSyncedAt: { type: Date },
    lastSyncError: { type: String },
  },
  { timestamps: { createdAt: true, updatedAt: false } }
);

export const CalendarLink = mongoose.model<ICalendarLink>("CalendarLink", CalendarLinkSchema);
