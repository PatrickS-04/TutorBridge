import mongoose, { Schema, Document, Types } from "mongoose";
import { LessonMode, BookingStatus } from "./enums";

//This represents the «collection: bookings» Booking stated in design document
//The client only requests one and renders what comes back.

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
  /** Campus venue picked in the booking flow. Online sessions leave it unset. */
  location?: string;
  status: BookingStatus;
  isTrial: boolean;
  addressDisclosed: boolean;
  cancelledBy?: Types.ObjectId;
  cancellationReason?: string;
  agreedRatePerHour: number;
  googleCalendarEventId?: string;
  confirmedAt?: Date;
  completedAt?: Date;
}

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
    location: { type: String, trim: true },
    status: { type: String, enum: Object.values(BookingStatus), default: BookingStatus.REQUESTED },
    isTrial: { type: Boolean, default: false },
    addressDisclosed: { type: Boolean, default: false },
    cancelledBy: { type: Schema.Types.ObjectId, ref: "User" },
    cancellationReason: { type: String },
    agreedRatePerHour: { type: Number, required: true },
    googleCalendarEventId: { type: String },
    confirmedAt: { type: Date },
    completedAt: { type: Date },
  },
  { timestamps: true }
);

//CONFIRMED bookings whose window intersects the requested one.
BookingSchema.index({ tutorId: 1, status: 1, startTime: 1, endTime: 1 });

export const Booking = mongoose.model<IBooking>("Booking", BookingSchema);
