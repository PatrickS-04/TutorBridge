import mongoose, { Schema, Document, Types } from "mongoose";

//This represents the «collection: areas» Area(reference data, shared across institutions) stated in design document

export interface IArea extends Document {
  provinceId: Types.ObjectId;
  name: string;
}

const AreaSchema = new Schema<IArea>({
  provinceId: { type: Schema.Types.ObjectId, ref: "Province", required: true, index: true },
  name: { type: String, required: true, trim: true },
});

export const Area = mongoose.model<IArea>("Area", AreaSchema);
