import mongoose, { Schema, Document, Types } from "mongoose";

//This represents the «collection: refresh_tokens» RefreshToken stated in design document

export interface IRefreshToken extends Document {
  userId: Types.ObjectId;
  tokenHash: string;
  deviceId?: string;
  issuedAt: Date;
  expiresAt: Date;
  revokedAt?: Date;
  revokedReason?: string;
  replacedByTokenHash?: string;
}

const RefreshTokenSchema = new Schema<IRefreshToken>({
  userId: { type: Schema.Types.ObjectId, ref: "User", required: true, index: true },
  tokenHash: { type: String, required: true, unique: true },
  deviceId: { type: String },
  issuedAt: { type: Date, default: Date.now },
  expiresAt: { type: Date, required: true },
  revokedAt: { type: Date },
  revokedReason: { type: String },
  replacedByTokenHash: { type: String },
});

RefreshTokenSchema.index({ expiresAt: 1 }, { expireAfterSeconds: 0 });

export const RefreshToken = mongoose.model<IRefreshToken>("RefreshToken", RefreshTokenSchema);
