import mongoose, { Schema, Document, Types } from "mongoose";
import { LessonMode, BookingStatus } from "./enums";

//This represents the «collection: bookings» Booking stated in design document
//The client only requests one and renders what comes back.

/** One tick-box on the tutor's prep checklist for a session. */
export interface ITutorNoteItem {
  text: string;
  done: boolean;
}

/**
 * Proof the session happened: the student scanned or typed the tutor's check-in
 * code. The code itself is only ever stored hashed, and the select: false paths
 * keep the hash, its expiry and the wrong-guess count out of every normal query.
 */
export interface IAttendance {
    checkedInAt?: Date;
    method?: "QR" | "CODE";
    codeHash?: string;
    codeExpiresAt?: Date;
    failedAttempts?: number;
}

/** A tutor's private working notes for one session. */
export interface ITutorNotes {
  items: ITutorNoteItem[];
  /** Free-text side notes, kept alongside the checklist. */
  text: string;
}

export interface IBooking extends Document {
  institutionId: Types.ObjectId;
  /** Set when the booking came from an accepted application; unset when the
   * student booked the tutor directly from their profile. */
  applicationId?: Types.ObjectId;
  tutorId: Types.ObjectId;
  studentId: Types.ObjectId;
  subjectName: string;
  startTime: Date;
  endTime: Date;
  mode: LessonMode;
  /** Where the session happens: a campus venue, or a place the student named
   * themselves - a coffee shop, say. Online sessions leave it unset. */
  location?: string;
  status: BookingStatus;
  isTrial: boolean;
  addressDisclosed: boolean;
  cancelledBy?: Types.ObjectId;
  cancellationReason?: string;
  agreedRatePerHour: number;
  googleCalendarEventId?: string;
  //Whose calendar the event was written to - either party may link one.
  googleCalendarUserId?: Types.ObjectId;
  confirmedAt?: Date;
  completedAt?: Date;
  /** The tutor's own prep notes about this student/guardian. Private to
   * them: the `select: false` below keeps it out of every query that does not
   * ask for it by name, so the student's own booking list cannot carry it. */
  tutorNotes?: ITutorNotes;
  attendance?: IAttendance;
}

//The whole list is replaced on every save, so an item is a value rather than
//a thing with its own identity - no _id to keep in step with the client.
const TutorNoteItemSchema = new Schema<ITutorNoteItem>(
  {
    text: { type: String, required: true, trim: true, maxlength: 200 },
    done: { type: Boolean, default: false },
  },
  { _id: false }
);

const TutorNotesSchema = new Schema<ITutorNotes>(
  {
    items: { type: [TutorNoteItemSchema], default: [] },
    text: { type: String, default: "", trim: true, maxlength: 2000 },
  },
  { _id: false }
);

const AttendanceSchema = new Schema<IAttendance>(
    {
        checkedInAt: { type: Date },
        method: { type: String, enum: ["QR", "CODE"] },
        codeHash: { type: String, select: false },
        codeExpiresAt: { type: Date, select: false },
        failedAttempts: { type: Number, default: 0, select: false },
        },
        { _id: false }
        );

const BookingSchema = new Schema<IBooking>(
  {
    institutionId: { type: Schema.Types.ObjectId, ref: "Institution", required: true, index: true },
    applicationId: { type: Schema.Types.ObjectId, ref: "Application" },
    tutorId: { type: Schema.Types.ObjectId, ref: "User", required: true, index: true },
    studentId: { type: Schema.Types.ObjectId, ref: "User", required: true, index: true },
    subjectName: { type: String, required: true },
    startTime: { type: Date, required: true },
    endTime: { type: Date, required: true },
    mode: { type: String, enum: Object.values(LessonMode), required: true },
    location: { type: String, trim: true, maxlength: 120 },
    status: { type: String, enum: Object.values(BookingStatus), default: BookingStatus.REQUESTED },
    isTrial: { type: Boolean, default: false },
    addressDisclosed: { type: Boolean, default: false },
    cancelledBy: { type: Schema.Types.ObjectId, ref: "User" },
    cancellationReason: { type: String },
    agreedRatePerHour: { type: Number, required: true },
    googleCalendarEventId: { type: String },
    googleCalendarUserId: { type: Schema.Types.ObjectId, ref: "User" },
    confirmedAt: { type: Date },
    completedAt: { type: Date },
    tutorNotes: { type: TutorNotesSchema, select: false },
    attendance: { type: AttendanceSchema }
  },
  { timestamps: true }
);

//CONFIRMED bookings whose window intersects the requested one.
BookingSchema.index({ tutorId: 1, status: 1, startTime: 1, endTime: 1 });

export const Booking = mongoose.model<IBooking>("Booking", BookingSchema);
