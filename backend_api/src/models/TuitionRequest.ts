import mongoose, { Schema, Document, Types } from "mongoose";
import { LessonMode, TuitionRequestStatus } from "./enums";
import { RateBandSchema, IRateBand, AvailabilitySlotSchema, IAvailabilitySlot } from "./embedded";

//This represents the «collection: tuition_requests» TuitionRequest stated in design document

export interface ITuitionRequest extends Document {
  institutionId: Types.ObjectId;
  studentId: Types.ObjectId;
  subjectId: Types.ObjectId;
  areaId: Types.ObjectId;
  gradeLevel?: string;
  mode: LessonMode;
  description: string;
  preferredRateBand?: IRateBand;
  availability: IAvailabilitySlot[];
  status: TuitionRequestStatus;
  applicationCount: number;
  matchScore?: number;
}

const TuitionRequestSchema = new Schema<ITuitionRequest>(
  {
    institutionId: { type: Schema.Types.ObjectId, ref: "Institution", required: true, index: true },
    studentId: { type: Schema.Types.ObjectId, ref: "User", required: true, index: true },
    subjectId: { type: Schema.Types.ObjectId, ref: "Subject", required: true },
    areaId: { type: Schema.Types.ObjectId, ref: "Area", required: true },
    gradeLevel: { type: String },
    mode: { type: String, enum: Object.values(LessonMode), required: true },
    description: { type: String, maxlength: 1000 },
    preferredRateBand: { type: RateBandSchema },
    availability: { type: [AvailabilitySlotSchema], default: [] },
    status: {
      type: String,
      enum: Object.values(TuitionRequestStatus),
      default: TuitionRequestStatus.OPEN,
    },
    applicationCount: { type: Number, default: 0 },
    matchScore: { type: Number, min: 0, max: 100 },
  },
  { timestamps: true }
);

export const TuitionRequest = mongoose.model<ITuitionRequest>("TuitionRequest", TuitionRequestSchema);
