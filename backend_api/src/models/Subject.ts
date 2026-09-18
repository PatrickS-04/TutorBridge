import mongoose, { Schema, Document } from "mongoose";

//This represents the «collection: subjects» Subject — reference data, shared across stated in design document

export interface ISubject extends Document {
  name: string;
  slug: string;
}

const SubjectSchema = new Schema<ISubject>({
  name: { type: String, required: true, trim: true },
  slug: { type: String, required: true, unique: true, lowercase: true, trim: true },
});

export const Subject = mongoose.model<ISubject>("Subject", SubjectSchema);
