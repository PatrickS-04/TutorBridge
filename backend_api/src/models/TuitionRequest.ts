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
  /**
   * Made up by the app when the student taps Post, and sent again unchanged if
   * the app has to retry - after a lost reply, or from the offline queue. It is
   * what lets the server recognise a repeat and answer with the request it
   * already made instead of posting a second one.
   */
  clientRequestId?: string;
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
    clientRequestId: { type: String, maxlength: 100 },
  },
  { timestamps: true }
);

//One request per (student, clientRequestId). Partial, so requests posted without
//one - older app versions - are not all treated as having the same missing id.
TuitionRequestSchema.index(
  { studentId: 1, clientRequestId: 1 },
  { unique: true, partialFilterExpression: { clientRequestId: { $type: "string" } } }
);

export const TuitionRequest = mongoose.model<ITuitionRequest>("TuitionRequest", TuitionRequestSchema);
