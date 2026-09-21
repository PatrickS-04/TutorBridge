import mongoose, { Schema, Document, Types } from "mongoose";
import { ApplicationStatus } from "./enums";

//This represents the «collection: applications» Application stated in design document
//The tutor's response to a TuitionRequest
//Accepting one is what a Booking gets created from

export interface IApplication extends Document {
  institutionId: Types.ObjectId;
  tuitionRequestId: Types.ObjectId;
  tutorId: Types.ObjectId;
  message: string;
  proposedRate: number;
  matchScore?: number;
  status: ApplicationStatus;
  decidedAt?: Date;
}

const ApplicationSchema = new Schema<IApplication>(
  {
    institutionId: { type: Schema.Types.ObjectId, ref: "Institution", required: true, index: true },
    tuitionRequestId: { type: Schema.Types.ObjectId, ref: "TuitionRequest", required: true, index: true },
    tutorId: { type: Schema.Types.ObjectId, ref: "User", required: true, index: true },
    message: { type: String, maxlength: 1000 },
    proposedRate: { type: Number, required: true },
    matchScore: { type: Number, min: 0, max: 100 },
    status: { type: String, enum: Object.values(ApplicationStatus), default: ApplicationStatus.PENDING },
    decidedAt: { type: Date },
  },
  { timestamps: true }
);

//The tutor may only apply once per request
ApplicationSchema.index({ tuitionRequestId: 1, tutorId: 1 }, { unique: true });

export const Application = mongoose.model<IApplication>("Application", ApplicationSchema);
