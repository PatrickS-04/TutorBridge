import { Express, Router } from "express";
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
import notificationRoutes from "./notifications";

/**
 * Mount path -> router. A table rather than a list of app.use calls so
 * openapi.test.ts can read the real route surface back and check every
 * endpoint is documented.
 */
export const routeMounts: Record<string, Router> = {
  "/auth": authRoutes,
  "/institutions": institutionRoutes,
  "/users": userRoutes,
  "/tuition-requests": tuitionRequestRoutes,
  "/applications": applicationRoutes,
  "/bookings": bookingRoutes,
  "/threads": threadRoutes,
  "/notification-tokens": notificationTokenRoutes,
  "/reference": referenceRoutes,
  "/calendar": calendarRoutes,
  "/timesheets": timesheetRoutes,
  "/notifications": notificationRoutes,

  "/admin/subjects": createCrudRouter(Subject),
  "/admin/provinces": createCrudRouter(Province),
  "/admin/areas": createCrudRouter(Area),
};

export function registerRoutes(app: Express) {
  for (const [path, router] of Object.entries(routeMounts)) app.use(path, router);
}
