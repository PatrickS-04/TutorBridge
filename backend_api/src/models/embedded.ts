import { Schema } from "mongoose";
import { DayOfWeek } from "./enums";

//This represents the «embedded value object»s that are never their own
//collection stated inside the design document

export interface IRateBand {
  min: number; // Int32, ZAR per hour
  max: number; // Int32, ZAR per hour
  currency: string; // default "ZAR"
}

export const RateBandSchema = new Schema<IRateBand>(
  {
    min: { type: Number, required: true },
    max: { type: Number, required: true },
    currency: { type: String, default: "ZAR" },
  },
  { _id: false }
);

export interface IAvailabilitySlot {
  day: DayOfWeek;
  startTime: string; //"HH:mm" format using 24-hour model
  endTime: string; //"HH:mm" format using 24-hour model
}

export const AvailabilitySlotSchema = new Schema<IAvailabilitySlot>(
  {
    day: { type: String, enum: Object.values(DayOfWeek), required: true },
    startTime: { type: String, required: true },
    endTime: { type: String, required: true },
  },
  { _id: false }
);

export interface INotificationPreferences {
  newListing: boolean;
  application: boolean;
  booking: boolean;
  message: boolean;
  verification: boolean;
}

export const NotificationPreferencesSchema = new Schema<INotificationPreferences>(
  {
    newListing: { type: Boolean, default: true },
    application: { type: Boolean, default: true },
    booking: { type: Boolean, default: true },
    message: { type: Boolean, default: true },
    verification: { type: Boolean, default: true },
  },
  { _id: false }
);
