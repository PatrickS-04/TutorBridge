import mongoose, { Schema, Document, Types } from "mongoose";
import { MessageStatus } from "./enums";

//This represents the «collection: messages» Message stated in design document

export interface IMessage extends Document {
  threadId: Types.ObjectId;
  senderId: Types.ObjectId;
  body: string;
  clientMessageId: string;
  status: MessageStatus;
  sentAt: Date;
  readAt?: Date;
}

const MessageSchema = new Schema<IMessage>({
  threadId: { type: Schema.Types.ObjectId, ref: "Thread", required: true, index: true },
  senderId: { type: Schema.Types.ObjectId, ref: "User", required: true },
  body: { type: String, required: true, minlength: 1, maxlength: 2000 },
  clientMessageId: { type: String, required: true },
  status: { type: String, enum: Object.values(MessageStatus), default: MessageStatus.SENT },
  sentAt: { type: Date, default: Date.now },
  readAt: { type: Date },
});

MessageSchema.index({ threadId: 1, clientMessageId: 1 }, { unique: true });

export const Message = mongoose.model<IMessage>("Message", MessageSchema);
