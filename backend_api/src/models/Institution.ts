import mongoose, { Schema, Document, Types } from "mongoose";
import { Locale, InstitutionStatus } from "./enums";

//This represents the «collection: institutions» Institution stated in design document

export interface IInstitution extends Document {
  name: string;
  slug: string;
  contactEmail?: string;
  phoneNumber?: string;
  provinceId?: Types.ObjectId;
  addressLine?: string;
  logoUrl?: string;
  defaultLocale: Locale;
  status: InstitutionStatus;
  createdAt: Date;
  updatedAt: Date;
}

const InstitutionSchema = new Schema<IInstitution>(
  {
    name: { type: String, required: true, trim: true },
    slug: { type: String, required: true, unique: true, lowercase: true, trim: true },
    contactEmail: { type: String, trim: true, lowercase: true },
    phoneNumber: { type: String, trim: true },
    provinceId: { type: Schema.Types.ObjectId, ref: "Province" },
    addressLine: { type: String, trim: true },
    logoUrl: { type: String },
    defaultLocale: { type: String, enum: Object.values(Locale), default: Locale.EN },
    status: {
      type: String,
      enum: Object.values(InstitutionStatus),
      default: InstitutionStatus.ACTIVE,
    },
  },
  { timestamps: true }
);

export const Institution = mongoose.model<IInstitution>("Institution", InstitutionSchema);
