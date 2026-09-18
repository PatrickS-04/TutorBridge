import mongoose, { Schema, Document, Types } from "mongoose";

//This represents the «collection: threads» Thread stated in design document

export interface IThread extends Document {
  institutionId: Types.ObjectId;
  bookingId?: Types.ObjectId;
  applicationId?: Types.ObjectId;
  participants: Types.ObjectId[];
  lastMessagePreview?: string;
  lastMessageAt?: Date;
  unreadCount: Map<string, number>;
}

const ThreadSchema = new Schema<IThread>(
  {
    institutionId: { type: Schema.Types.ObjectId, ref: "Institution", required: true, index: true },
    bookingId: { type: Schema.Types.ObjectId, ref: "Booking" },
    applicationId: { type: Schema.Types.ObjectId, ref: "Application" },
    participants: {
      type: [{ type: Schema.Types.ObjectId, ref: "User" }],
      validate: (v: Types.ObjectId[]) => v.length === 2,
    },
    lastMessagePreview: { type: String },
    lastMessageAt: { type: Date },
    unreadCount: { type: Map, of: Number, default: {} },
  },
  { timestamps: { createdAt: true, updatedAt: false } }
);

ThreadSchema.index({ participants: 1 });

export const Thread = mongoose.model<IThread>("Thread", ThreadSchema);
