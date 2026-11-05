import mongoose, { Schema, Document, Types } from "mongoose";
import { ReportReason, ReportStatus } from "./enums";

/** What was said when the report was made. Copied, because erasing either account erases the thread. */
export interface IReportedMessage {
  senderId: Types.ObjectId;
  body: string;
  sentAt: Date;
}

export interface IReport extends Document {
  institutionId: Types.ObjectId;
  threadId: Types.ObjectId;
  reporterId: Types.ObjectId;
  reportedUserId: Types.ObjectId;
  reason: ReportReason;
  details?: string;
  messages: IReportedMessage[];
  status: ReportStatus;
  reviewedAt?: Date;
  reviewedBy?: Types.ObjectId;
  createdAt: Date;
}

const ReportSchema = new Schema<IReport>(
  {
    institutionId: { type: Schema.Types.ObjectId, ref: "Institution", required: true, index: true },
    threadId: { type: Schema.Types.ObjectId, ref: "Thread", required: true },
    reporterId: { type: Schema.Types.ObjectId, ref: "User", required: true },
    reportedUserId: { type: Schema.Types.ObjectId, ref: "User", required: true },
    reason: { type: String, enum: Object.values(ReportReason), required: true },
    details: { type: String, maxlength: 1000 },
    messages: {
      type: [{ _id: false, senderId: Schema.Types.ObjectId, body: String, sentAt: Date }],
      default: [],
    },
    status: { type: String, enum: Object.values(ReportStatus), default: ReportStatus.OPEN },
    reviewedAt: { type: Date },
    reviewedBy: { type: Schema.Types.ObjectId, ref: "User" },
  },
  { timestamps: { createdAt: true, updatedAt: false } }
);

export const Report = mongoose.model<IReport>("Report", ReportSchema);
