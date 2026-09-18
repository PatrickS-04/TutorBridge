import mongoose, { Schema, Document, Types } from "mongoose";
import { UserRole, AuthProvider, VerificationStatus, Locale } from "./enums";
import { AvailabilitySlotSchema, IAvailabilitySlot } from "./embedded";

//This represents the «collection: users» User stated in design document

export interface IUser extends Document {
  institutionId: Types.ObjectId;
  role: UserRole;
  fullName: string;
  email: string;
  passwordHash?: string;
  authProvider: AuthProvider;
  firebaseUid?: string;
  phoneNumber?: string;
  profilePhotoUrl?: string;
  bio?: string;
  hourlyRate?: number;
  subjectIds: Types.ObjectId[];
  areaId?: Types.ObjectId;
  gradeLevel?: string;
  availability: IAvailabilitySlot[];
  verificationStatus: VerificationStatus;
  profileCompleteness: number;
  locale: Locale;
  address?: string;
  deletionScheduledFor?: Date;
  createdBy?: Types.ObjectId;
  mustChangePassword: boolean;
  isBookable(): boolean;
}

const UserSchema = new Schema<IUser>(
  {
    institutionId: { type: Schema.Types.ObjectId, ref: "Institution", required: true, index: true },
    role: { type: String, enum: Object.values(UserRole), required: true, immutable: true },
    fullName: { type: String, required: true, minlength: 2, maxlength: 80, trim: true },
    email: { type: String, required: true, unique: true, lowercase: true, trim: true },
    passwordHash: { type: String, select: false },
    authProvider: { type: String, enum: Object.values(AuthProvider), required: true },
    firebaseUid: { type: String },
    phoneNumber: { type: String, trim: true },
    profilePhotoUrl: { type: String },
    bio: { type: String, maxlength: 1000 },
    hourlyRate: { type: Number },
    subjectIds: [{ type: Schema.Types.ObjectId, ref: "Subject" }],
    areaId: { type: Schema.Types.ObjectId, ref: "Area" },
    gradeLevel: { type: String },
    availability: { type: [AvailabilitySlotSchema], default: [] },
    verificationStatus: {
      type: String,
      enum: Object.values(VerificationStatus),
      default: VerificationStatus.UNVERIFIED,
    },
    profileCompleteness: { type: Number, min: 0, max: 100, default: 0 },
    locale: { type: String, enum: Object.values(Locale), default: Locale.EN },
    address: { type: String, select: false },
    deletionScheduledFor: { type: Date },
    createdBy: { type: Schema.Types.ObjectId, ref: "User" },
    mustChangePassword: { type: Boolean, default: false },
  },
  { timestamps: true }
);

UserSchema.index({ institutionId: 1, role: 1 });

UserSchema.methods.isBookable = function (this: IUser): boolean {
  return (
    this.role === UserRole.TUTOR &&
    this.verificationStatus === VerificationStatus.VERIFIED &&
    !this.deletionScheduledFor
  );
};

export const User = mongoose.model<IUser>("User", UserSchema);
