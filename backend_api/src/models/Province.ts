import mongoose, { Schema, Document } from "mongoose";

//This represents the «collection: provinces» Province stated in design document

export interface IProvince extends Document {
  name: string;
}

const ProvinceSchema = new Schema<IProvince>({
  name: { type: String, required: true, trim: true },
});

export const Province = mongoose.model<IProvince>("Province", ProvinceSchema);
