import mongoose, { Schema, Document, Types } from "mongoose";
import { DocumentType, VerificationStatus } from "./enums";

//This represents the «collection: verification_documents» VerificationDocument stated in design document

export interface IVerificationDocument extends Document {
  institutionId: Types.ObjectId;
  userId: Types.ObjectId;
  type: DocumentType;
  requestedSubjectId?: Types.ObjectId;
  storagePath: string;
  mimeType: string;
  sizeBytes: number;
  status: VerificationStatus;
  submittedAt: Date;
  reviewedAt?: Date;
  reviewedBy?: Types.ObjectId;
  rejectionReason?: string;
}

const VerificationDocumentSchema = new Schema<IVerificationDocument>({
  institutionId: { type: Schema.Types.ObjectId, ref: "Institution", required: true, index: true },
  userId: { type: Schema.Types.ObjectId, ref: "User", required: true, index: true },
  type: { type: String, enum: Object.values(DocumentType), required: true },
  requestedSubjectId: { type: Schema.Types.ObjectId, ref: "Subject" },
  storagePath: { type: String, required: true },
  mimeType: { type: String, required: true },
  sizeBytes: { type: Number, required: true },
  status: {
    type: String,
    enum: Object.values(VerificationStatus),
    default: VerificationStatus.PENDING,
  },
  submittedAt: { type: Date, default: Date.now },
  reviewedAt: { type: Date },
  reviewedBy: { type: Schema.Types.ObjectId, ref: "User" },
  rejectionReason: { type: String },
});

export const VerificationDocument = mongoose.model<IVerificationDocument>(
  "VerificationDocument",
  VerificationDocumentSchema
);
