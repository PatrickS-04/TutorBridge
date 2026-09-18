import { Express } from "express";
import { Subject, Province, Area } from "../models";
import { createCrudRouter } from "../utils/createCrudRouter";

import authRoutes from "./auth";
import institutionRoutes from "./institutions";
import userRoutes from "./users";
import tuitionRequestRoutes from "./tuitionRequests";
import applicationRoutes from "./applications";
import bookingRoutes from "./bookings";
import threadRoutes from "./threads";
import notificationTokenRoutes from "./notificationTokens";
import referenceRoutes from "./reference";
import calendarRoutes from "./calendar";
import timesheetRoutes from "./timesheets";

export function registerRoutes(app: Express) {
  app.use("/auth", authRoutes);
  app.use("/institutions", institutionRoutes);
  app.use("/users", userRoutes);
  app.use("/tuition-requests", tuitionRequestRoutes);
  app.use("/applications", applicationRoutes);
  app.use("/bookings", bookingRoutes);
  app.use("/threads", threadRoutes);
  app.use("/notification-tokens", notificationTokenRoutes);
  app.use("/reference", referenceRoutes);
  app.use("/calendar", calendarRoutes);
  app.use("/timesheets", timesheetRoutes);

  app.use("/admin/subjects", createCrudRouter(Subject));
  app.use("/admin/provinces", createCrudRouter(Province));
  app.use("/admin/areas", createCrudRouter(Area));
}
