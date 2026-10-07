import { Express, Router } from "express";
import { Subject, Province, Area, User, TuitionRequest } from "../models";
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

// Reference: Express.js. n.d. Routing. [Online].
//   Available at: https://expressjs.com/en/guide/routing/ [Accessed 21 September 2026].
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

  "/admin/subjects": createCrudRouter(Subject, {
    inUse: async (id) => !!(await User.exists({ subjectIds: id })) || !!(await TuitionRequest.exists({ subjectId: id })),
  }),
  "/admin/provinces": createCrudRouter(Province, {
    inUse: async (id) => !!(await Area.exists({ provinceId: id })),
  }),
  "/admin/areas": createCrudRouter(Area, {
    inUse: async (id) => !!(await User.exists({ areaId: id })) || !!(await TuitionRequest.exists({ areaId: id })),
  }),
};

export function registerRoutes(app: Express) {
  for (const [path, router] of Object.entries(routeMounts)) app.use(path, router);
}
