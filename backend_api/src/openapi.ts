/**
 * OpenAPI 3.0 description of the TutorBridge API, served as Swagger UI at
 * /docs by index.ts.
 *
 * Written by hand because the routes are plain Express handlers with no
 * schema layer to generate a spec from. openapi.test.ts walks the registered
 * Express routes and fails if one is missing here, so the two cannot quietly
 * drift apart. Enum lists are imported rather than copied for the same reason.
 */
import {
  ApplicationStatus,
  AuthProvider,
  BookingStatus,
  DayOfWeek,
  DocumentType,
  InstitutionStatus,
  LessonMode,
  Locale,
  MessageStatus,
  NotificationCategory,
  Theme,
  TuitionRequestStatus,
  UserRole,
  VerificationStatus,
} from "./models/enums";

const ref = (name: string) => ({ $ref: `#/components/schemas/${name}` });
const enumOf = (values: object) => ({ type: "string", enum: Object.values(values) });
const objectId = (description: string) => ({
  type: "string",
  description,
  example: "66f1a2b3c4d5e6f708192a3b",
});
const dateTime = (description: string) => ({ type: "string", format: "date-time", description });
const json = (schema: object) => ({ content: { "application/json": { schema } } });
const ok = (description: string, schema: object) => ({ description, ...json(schema) });

/** The envelope every list endpoint returns - see utils/paginate.ts. */
const pageOf = (items: object) => ({
  type: "object",
  properties: {
    data: { type: "array", items },
    page: { type: "integer", example: 1 },
    pageSize: { type: "integer", example: 20 },
    totalPages: { type: "integer", example: 3 },
  },
});

const pathId = (name: string, description: string) => ({
  name,
  in: "path",
  required: true,
  schema: { type: "string" },
  description,
});
const query = (name: string, description: string, schema: object = { type: "string" }) => ({
  name,
  in: "query",
  required: false,
  schema,
  description,
});
const pageParam = { $ref: "#/components/parameters/PageParam" };
const month = (description: string) => ({
  name: "month",
  in: "query",
  required: true,
  schema: { type: "string", pattern: "^\\d{4}-\\d{2}$", example: "2026-09" },
  description,
});

const res = {
  badRequest: { $ref: "#/components/responses/ValidationError" },
  unauthorized: { $ref: "#/components/responses/Unauthorized" },
  forbidden: { $ref: "#/components/responses/Forbidden" },
  notFound: { $ref: "#/components/responses/NotFound" },
  conflict: { $ref: "#/components/responses/Conflict" },
};
/** Every authenticated route can answer with these two. */
const authErrors = { 401: res.unauthorized, 403: res.forbidden };
const noContent = { description: "Deleted. No body." };

/**
 * The five routes utils/createCrudRouter.ts mounts for a reference
 * collection - generated here for the same reason they are generated there.
 */
function crudPaths(resource: string, schema: string) {
  const tags = ["Admin reference data"];
  const adminOnly =
    "Platform admins only: these lists are shared by every institution, so an institution admin cannot change them.";
  const noun = schema.toLowerCase();
  const a = /^[aeiou]/.test(noun) ? "an" : "a";
  return {
    [`/admin/${resource}`]: {
      get: {
        tags,
        summary: `List ${resource}`,
        parameters: [pageParam],
        responses: { 200: ok("A page of records.", pageOf(ref(schema))), ...authErrors },
      },
      post: {
        tags,
        summary: `Create ${a} ${noun}`,
        description: adminOnly,
        requestBody: { required: true, ...json(ref(schema)) },
        responses: { 201: ok("Created.", ref(schema)), ...authErrors },
      },
    },
    [`/admin/${resource}/{id}`]: {
      get: {
        tags,
        summary: `Fetch one ${noun}`,
        parameters: [pathId("id", `${schema} id.`)],
        responses: { 200: ok("The record.", ref(schema)), ...authErrors, 404: res.notFound },
      },
      put: {
        tags,
        summary: `Replace ${a} ${noun}`,
        description: adminOnly,
        parameters: [pathId("id", `${schema} id.`)],
        requestBody: { required: true, ...json(ref(schema)) },
        responses: { 200: ok("The updated record.", ref(schema)), ...authErrors, 404: res.notFound },
      },
      delete: {
        tags,
        summary: `Delete ${a} ${noun}`,
        description: `${adminOnly} A record that existing users, requests or areas still use answers 409.`,
        parameters: [pathId("id", `${schema} id.`)],
        responses: { 204: noContent, ...authErrors, 404: res.notFound, 409: res.conflict },
      },
    },
  };
}

// Reference: OpenAPI Initiative. 2020. OpenAPI Specification v3.0.3. [Online].
//   Available at: https://spec.openapis.org/oas/v3.0.3.html [Accessed 21 September 2026].
export const openapiSpec = {
  openapi: "3.0.3",
  info: {
    title: "TutorBridge API",
    version: "1.0.0",
    description: [
      "Backend for the TutorBridge Android app: institution-scoped tutor",
      "matching, bookings, messaging and tutor claim sheets.",
      "",
      "**Authentication.** Firebase is the identity provider. The app signs in",
      "with the Firebase SDK and sends the resulting ID token as",
      "`Authorization: Bearer <idToken>`. Paste one into **Authorize** to try",
      "these endpoints out. Every route needs a token except",
      "`GET /reference/institutions`, which the sign-up screen calls before a",
      "user has one.",
      "",
      "A verified token still needs a TutorBridge profile behind it: call",
      "`POST /auth/bootstrap` once after each sign-in, or the other routes",
      "answer `403 NO_PROFILE`.",
    ].join("\n"),
  },
  servers: [
    { url: "/", description: "This server" },
    { url: "http://localhost:5000", description: "Local development" },
  ],
  security: [{ bearerAuth: [] }],
  tags: [
    { name: "Auth", description: "Mapping a Firebase identity onto a TutorBridge profile." },
    { name: "Reference", description: "Institution, subject and location lookups." },
    { name: "Users", description: "The signed-in profile, settings and verification." },
    { name: "Institutions", description: "Institution admin: tutors and the verification queue." },
    { name: "Tuition requests", description: "The request board students post to." },
    { name: "Applications", description: "Tutor offers against a request." },
    { name: "Bookings", description: "Scheduled sessions." },
    { name: "Threads", description: "In-app messaging." },
    { name: "Notifications", description: "Notification feed and FCM device tokens." },
    { name: "Calendar", description: "Google Calendar linking." },
    { name: "Timesheets", description: "Tutor hours, claim periods and CSV export." },
    { name: "Admin reference data", description: "CRUD over the shared reference collections." },
  ],

  paths: {
    "/auth/bootstrap": {
      post: {
        tags: ["Auth"],
        summary: "Create or return the profile for the signed-in Firebase user",
        description: [
          "Idempotent; the app calls it straight after every Firebase sign-in.",
          "`institutionId` is only needed the first time a brand-new account",
          "signs in. A profile an admin pre-created with the same email is",
          "linked to the Firebase identity rather than duplicated.",
          "",
          "This is the one route that works without an existing TutorBridge",
          "profile - it verifies the Firebase token and nothing else.",
        ].join("\n"),
        requestBody: {
          ...json({
            type: "object",
            properties: {
              institutionId: objectId("Required only when creating a brand-new profile."),
              fullName: { type: "string", example: "Thabo Mokoena" },
            },
          }),
        },
        responses: {
          200: ok("An existing profile.", ref("Profile")),
          201: ok("A newly created profile.", ref("Profile")),
          400: res.badRequest,
          401: res.unauthorized,
        },
      },
    },
    "/auth/change-password": {
      post: {
        tags: ["Auth"],
        summary: "Replace an admin-issued temporary password",
        description: [
          "For a tutor provisioned through `POST /institutions/me/tutors`, while",
          "their profile still says `mustChangePassword`. The server sets the new",
          "password in Firebase and clears the flag in the same step, so the flag",
          "cannot be cleared without the password really changing.",
          "",
          "Firebase ends every session on an account whose password changes,",
          "the caller's included, so sign in again with the new password.",
        ].join("\n"),
        requestBody: {
          required: true,
          ...json({
            type: "object",
            required: ["newPassword"],
            properties: {
              newPassword: {
                type: "string",
                minLength: 6,
                description: "Must differ from the temporary password.",
              },
            },
          }),
        },
        responses: {
          204: { description: "Changed. No body." },
          400: res.badRequest,
          ...authErrors,
          409: res.conflict,
        },
      },
    },

    "/reference/institutions": {
      get: {
        tags: ["Reference"],
        summary: "List active institutions",
        description:
          "Public: the sign-up screen needs this before any token exists. Cached for an hour.",
        security: [],
        responses: {
          200: ok("Active institutions, by name.", { type: "array", items: ref("NamedRef") }),
        },
      },
    },
    "/reference/subjects-and-locations": {
      get: {
        tags: ["Reference"],
        summary: "Subjects, and provinces with their areas",
        description: "Everything the request and profile forms need to fill their pickers.",
        responses: {
          200: ok("Reference data. Cached for an hour.", {
            type: "object",
            properties: {
              subjects: { type: "array", items: ref("NamedRef") },
              provinces: {
                type: "array",
                items: {
                  type: "object",
                  properties: {
                    id: objectId("Province id."),
                    name: { type: "string" },
                    areas: { type: "array", items: ref("NamedRef") },
                  },
                },
              },
            },
          }),
          ...authErrors,
        },
      },
    },

    "/users/tutors": {
      get: {
        tags: ["Users"],
        summary: "List bookable tutors at the caller's institution",
        description: [
          "Verified tutors not pending deletion - what the Find a Tutor screen lists.",
          "",
          "Each tutor carries a `matchScore` for the caller, worked out from the",
          "caller's own tuition requests: a shared subject (live requests count",
          "most), a shared campus area, and the tutor's completed sessions. A tutor",
          "who shares neither a subject nor an area scores 0. Best fit comes first.",
        ].join("\n"),
        parameters: [pageParam],
        responses: {
          200: ok(
            "A page of tutors, each with sessions actually taught and a match score.",
            pageOf({
              allOf: [
                ref("User"),
                {
                  type: "object",
                  properties: {
                    completedSessions: { type: "integer", example: 12 },
                    matchScore: { type: "integer", minimum: 0, maximum: 100, example: 80 },
                  },
                },
              ],
            })
          ),
          ...authErrors,
        },
      },
    },
    "/users/me": {
      get: {
        tags: ["Users"],
        summary: "Fetch the signed-in user's profile",
        responses: { 200: ok("The profile.", ref("User")), ...authErrors, 404: res.notFound },
      },
      put: {
        tags: ["Users"],
        summary: "Update the signed-in user's profile",
        description:
          "Only the fields present in the body are written, so a partial update cannot blank the rest.",
        requestBody: {
          required: true,
          ...json({
            type: "object",
            properties: {
              fullName: { type: "string" },
              bio: { type: "string", maxLength: 1000 },
              hourlyRate: { type: "number", description: "ZAR per hour.", example: 250 },
              subjectIds: { type: "array", items: objectId("Subject id.") },
              areaId: objectId("Area id."),
              gradeLevel: { type: "string", example: "Grade 11" },
              availability: { type: "array", items: ref("AvailabilitySlot") },
              department: { type: "string" },
            },
          }),
        },
        responses: { 200: ok("The updated profile.", ref("User")), ...authErrors, 404: res.notFound },
      },
      delete: {
        tags: ["Users"],
        summary: "Schedule account deletion",
        description:
          "Soft delete with a 14-day grace period. The account stops being bookable immediately.",
        responses: {
          202: ok("Deletion scheduled.", {
            type: "object",
            properties: {
              message: { type: "string", example: "Account deletion scheduled" },
              scheduledFor: dateTime("When the account is due to be removed."),
            },
          }),
          ...authErrors,
        },
      },
    },
    "/users/me/completeness": {
      get: {
        tags: ["Users"],
        summary: "Profile completeness, and what is still missing",
        description: "Recomputes and stores the percentage as a side effect.",
        responses: {
          200: ok("Completeness.", {
            type: "object",
            properties: {
              percentage: { type: "integer", minimum: 0, maximum: 100, example: 60 },
              missingFields: {
                type: "array",
                items: {
                  type: "string",
                  enum: ["profilePhoto", "bio", "hourlyRate", "subjects", "availability"],
                },
              },
            },
          }),
          ...authErrors,
          404: res.notFound,
        },
      },
    },
    "/users/me/settings": {
      get: {
        tags: ["Users"],
        summary: "Fetch app settings",
        description: "Creates the defaults on first read.",
        responses: { 200: ok("The settings.", ref("UserSettings")), ...authErrors },
      },
      put: {
        tags: ["Users"],
        summary: "Update app settings",
        description:
          "Safe to repeat: the same body twice leaves the same settings, which is what lets the app " +
          "replay changes made offline. When edits to one setting are replayed in order, the last one wins.",
        requestBody: { required: true, ...json(ref("UserSettings")) },
        responses: { 200: ok("The updated settings.", ref("UserSettings")), ...authErrors },
      },
    },
    "/users/me/verification-documents": {
      post: {
        tags: ["Users"],
        summary: "Upload an identity or qualification document",
        description:
          "Moves the caller to PENDING and puts them in their institution's verification queue. Max 10 MB.",
        requestBody: {
          required: true,
          content: {
            "multipart/form-data": {
              schema: {
                type: "object",
                required: ["file", "type"],
                properties: {
                  file: { type: "string", format: "binary" },
                  type: enumOf(DocumentType),
                  requestedSubjectId: objectId("Subject the qualification is claimed for."),
                },
              },
            },
          },
        },
        responses: {
          202: ok("Accepted for review.", {
            type: "object",
            properties: {
              verificationStatus: enumOf(VerificationStatus),
              submittedAt: dateTime("When it was received."),
            },
          }),
          400: res.badRequest,
          ...authErrors,
        },
      },
    },
    "/users/me/verification-status": {
      get: {
        tags: ["Users"],
        summary: "Where the caller's verification stands",
        responses: {
          200: ok("Status, with the latest document's review outcome.", {
            type: "object",
            properties: {
              status: enumOf(VerificationStatus),
              submittedAt: { ...dateTime("Latest submission."), nullable: true },
              reviewedAt: { ...dateTime("When an admin decided."), nullable: true },
              rejectionReason: { type: "string", nullable: true },
            },
          }),
          ...authErrors,
          404: res.notFound,
        },
      },
    },

    "/institutions/me": {
      get: {
        tags: ["Institutions"],
        summary: "Fetch the caller's institution",
        description: "Any member of the institution, whatever their role. Every other route here is admin-only.",
        responses: {
          200: ok("The institution.", ref("Institution")),
          ...authErrors,
          404: res.notFound,
        },
      },
      put: {
        tags: ["Institutions"],
        summary: "Update the caller's institution",
        description: "Institution admins only.",
        requestBody: {
          required: true,
          ...json({
            type: "object",
            properties: {
              contactEmail: { type: "string", format: "email" },
              defaultLocale: enumOf(Locale),
            },
          }),
        },
        responses: {
          200: ok("The updated institution.", ref("Institution")),
          ...authErrors,
          404: res.notFound,
        },
      },
    },
    "/institutions/me/tutors": {
      get: {
        tags: ["Institutions"],
        summary: "List the institution's tutors",
        description:
          "Institution admins only. Unlike /users/tutors, this includes unverified ones. Removed tutors are left out.",
        parameters: [pageParam],
        responses: { 200: ok("A page of tutors.", pageOf(ref("User"))), ...authErrors },
      },
      post: {
        tags: ["Institutions"],
        summary: "Provision a tutor account",
        description: [
          "Institution admins only. Creates a pre-verified tutor with a Firebase",
          "sign-in and returns a one-time password for it. The tutor signs in to",
          "the app with it and must replace it (`POST /auth/change-password`)",
          "before going any further.",
          "",
          "That password appears in this response and nowhere else.",
        ].join("\n"),
        requestBody: {
          required: true,
          ...json({
            type: "object",
            required: ["fullName", "email"],
            properties: {
              fullName: { type: "string", example: "Naledi Dlamini" },
              email: { type: "string", format: "email" },
              subjectIds: { type: "array", items: objectId("Subject id.") },
              department: { type: "string", example: "Faculty of Science" },
              staffNumber: { type: "string", example: "ST-10432" },
              hourlyRate: { type: "number", example: 300 },
            },
          }),
        },
        responses: {
          201: ok("The provisioned tutor.", {
            type: "object",
            properties: {
              id: objectId("New user id."),
              role: enumOf(UserRole),
              verificationStatus: enumOf(VerificationStatus),
              temporaryPassword: { type: "string", description: "One-time password, returned once." },
              mustChangePassword: { type: "boolean", example: true },
            },
          }),
          400: res.badRequest,
          ...authErrors,
          409: res.conflict,
        },
      },
    },
    "/institutions/me/tutors/{id}": {
      delete: {
        tags: ["Institutions"],
        summary: "Remove a tutor",
        description: "Institution admins only. Soft delete - the tutor stops being bookable at once.",
        parameters: [pathId("id", "Tutor's user id.")],
        responses: { 204: noContent, ...authErrors, 404: res.notFound },
      },
    },
    "/institutions/me/verification-queue": {
      get: {
        tags: ["Institutions"],
        summary: "Documents awaiting review",
        description: "Institution admins only.",
        parameters: [pageParam],
        responses: {
          200: ok("A page of pending documents.", pageOf(ref("VerificationDocument"))),
          ...authErrors,
        },
      },
    },
    "/institutions/verification/{userId}": {
      put: {
        tags: ["Institutions"],
        summary: "Approve or reject a user's verification",
        description:
          "Institution admins only. Decides every pending document for that user at once.",
        parameters: [pathId("userId", "User under review.")],
        requestBody: { required: true, ...json(ref("Decision")) },
        responses: {
          200: ok("The decision.", {
            type: "object",
            properties: {
              userId: objectId("User id."),
              status: enumOf(VerificationStatus),
              reviewedAt: dateTime("When it was decided."),
            },
          }),
          400: res.badRequest,
          ...authErrors,
          404: res.notFound,
        },
      },
    },

    "/tuition-requests": {
      post: {
        tags: ["Tuition requests"],
        summary: "Post a tuition request",
        description: [
          "Students and guardians only.",
          "",
          "`clientRequestId` makes this safe to retry: posting the same one again",
          "(the app does this after a lost reply, and when it replays requests made",
          "offline) answers `200` with the request already made instead of creating",
          "a second. It is unique per student.",
        ].join("\n"),
        requestBody: {
          required: true,
          ...json({
            type: "object",
            required: ["subjectId", "areaId", "mode"],
            properties: {
              subjectId: objectId("Subject being requested."),
              areaId: objectId("Area the session should happen in."),
              gradeLevel: { type: "string", example: "Grade 11" },
              mode: enumOf(LessonMode),
              description: { type: "string", maxLength: 1000 },
              preferredRateBand: ref("RateBand"),
              availability: { type: "array", items: ref("AvailabilitySlot") },
              clientRequestId: {
                type: "string",
                maxLength: 100,
                description: "Client-generated id, unique per student.",
              },
            },
          }),
        },
        responses: {
          200: ok("A request with this `clientRequestId` already existed; here it is.", {
            type: "object",
            properties: {
              id: objectId("Request id."),
              status: enumOf(TuitionRequestStatus),
              matchScore: { type: "number", nullable: true },
              createdAt: dateTime("When it was posted."),
            },
          }),
          201: ok("The new request.", {
            type: "object",
            properties: {
              id: objectId("Request id."),
              status: enumOf(TuitionRequestStatus),
              matchScore: { type: "number", nullable: true },
              createdAt: dateTime("When it was posted."),
            },
          }),
          400: res.badRequest,
          ...authErrors,
        },
      },
      get: {
        tags: ["Tuition requests"],
        summary: "Browse the request board",
        description: [
          "Without `mine=true` this is the tutor board and only open requests",
          "come back. With it, the caller's own requests come back in every",
          "status.",
        ].join("\n"),
        parameters: [
          query("subjectId", "Only this subject."),
          query("areaId", "Only this area."),
          query("status", "Only this status.", enumOf(TuitionRequestStatus)),
          query("mine", "`true` for the caller's own requests.", {
            type: "string",
            enum: ["true", "false"],
          }),
          pageParam,
        ],
        responses: {
          200: ok(
            "A page of requests. Matched ones carry the tutor's name.",
            pageOf({
              allOf: [
                ref("TuitionRequest"),
                { type: "object", properties: { matchedTutorName: { type: "string" } } },
              ],
            })
          ),
          ...authErrors,
        },
      },
    },
    "/tuition-requests/{id}": {
      get: {
        tags: ["Tuition requests"],
        summary: "Fetch one request",
        parameters: [pathId("id", "Request id.")],
        responses: {
          200: ok("The request, with subject, area and student resolved.", ref("TuitionRequest")),
          ...authErrors,
          404: res.notFound,
        },
      },
      put: {
        tags: ["Tuition requests"],
        summary: "Edit your own request",
        description:
          "Only while the request is still `OPEN`; once a tutor is matched it answers 409. Tutors who already applied are notified.",
        parameters: [pathId("id", "Request id.")],
        requestBody: {
          required: true,
          ...json({
            type: "object",
            properties: {
              subjectId: objectId("Subject being requested."),
              areaId: objectId("Area the session should happen in."),
              gradeLevel: { type: "string", example: "Grade 11" },
              mode: enumOf(LessonMode),
              description: { type: "string", maxLength: 1000 },
              preferredRateBand: ref("RateBand"),
              availability: { type: "array", items: ref("AvailabilitySlot") },
            },
          }),
        },
        responses: {
          200: ok("The updated request.", ref("TuitionRequest")),
          400: res.badRequest,
          ...authErrors,
          404: res.notFound,
          409: res.conflict,
        },
      },
    },
    "/tuition-requests/{id}/close": {
      put: {
        tags: ["Tuition requests"],
        summary: "Close your own request",
        description:
          "For a request that is sorted. Open or matched requests only; pending applications are declined and their tutors told.",
        parameters: [pathId("id", "Request id.")],
        responses: {
          200: ok("The request's new status.", {
            type: "object",
            properties: { id: objectId("Request id."), status: enumOf(TuitionRequestStatus) },
          }),
          ...authErrors,
          404: res.notFound,
          409: res.conflict,
        },
      },
    },
    "/tuition-requests/{id}/withdraw": {
      put: {
        tags: ["Tuition requests"],
        summary: "Withdraw your own request",
        description:
          "For a request that is no longer wanted; leaves it `CANCELLED`. Open or matched requests only; pending applications are declined and their tutors told.",
        parameters: [pathId("id", "Request id.")],
        responses: {
          200: ok("The request's new status.", {
            type: "object",
            properties: { id: objectId("Request id."), status: enumOf(TuitionRequestStatus) },
          }),
          ...authErrors,
          404: res.notFound,
          409: res.conflict,
        },
      },
    },
    "/tuition-requests/{id}/applications": {
      post: {
        tags: ["Applications"],
        summary: "Apply to a request",
        description: "Verified tutors only, once per request, and only while the request is open.",
        parameters: [pathId("id", "Request id.")],
        requestBody: {
          required: true,
          ...json({
            type: "object",
            required: ["proposedRate"],
            properties: {
              message: { type: "string", maxLength: 1000 },
              proposedRate: { type: "number", description: "ZAR per hour.", example: 280 },
            },
          }),
        },
        responses: {
          201: ok("The application.", {
            type: "object",
            properties: {
              id: objectId("Application id."),
              tuitionRequestId: objectId("Request applied to."),
              status: enumOf(ApplicationStatus),
              createdAt: dateTime("When it was sent."),
            },
          }),
          400: res.badRequest,
          ...authErrors,
          404: res.notFound,
          409: res.conflict,
        },
      },
      get: {
        tags: ["Applications"],
        summary: "List applications to your own request",
        parameters: [pathId("id", "Request id."), pageParam],
        responses: {
          200: ok("A page of applications, each with its tutor.", pageOf(ref("Application"))),
          ...authErrors,
          404: res.notFound,
        },
      },
    },
    "/applications/mine": {
      get: {
        tags: ["Applications"],
        summary: "List the applications you have sent",
        description: "Tutors only. Each carries the request it went to, with subject and student.",
        parameters: [pageParam],
        responses: { 200: ok("A page of applications.", pageOf(ref("Application"))), ...authErrors },
      },
    },
    "/applications/{id}": {
      put: {
        tags: ["Applications"],
        summary: "Accept or reject an application",
        description:
          "The request owner decides. Accepting moves the request to MATCHED and notifies the tutor.",
        parameters: [pathId("id", "Application id.")],
        requestBody: { required: true, ...json(ref("Decision")) },
        responses: {
          200: ok("The decision.", {
            type: "object",
            properties: { id: objectId("Application id."), status: enumOf(ApplicationStatus) },
          }),
          400: res.badRequest,
          ...authErrors,
          404: res.notFound,
          409: res.conflict,
        },
      },
    },

    "/bookings": {
      post: {
        tags: ["Bookings"],
        summary: "Schedule the session behind an accepted application",
        description: [
          "The end of the Accept and Match flow: the tutor offered and the",
          "student accepted, so the booking is CONFIRMED outright and written",
          "to the tutor's Google Calendar if one is linked.",
        ].join("\n"),
        requestBody: {
          required: true,
          ...json({
            type: "object",
            required: ["applicationId", "startTime", "endTime"],
            properties: {
              applicationId: objectId("The accepted application."),
              startTime: dateTime("Session start."),
              endTime: dateTime("Session end; must be after startTime."),
              location: {
                type: "string",
                maxLength: 120,
                description:
                  "Where to meet: a campus venue, or a place the student named themselves such as a coffee shop. Defaults to the request's area.",
              },
            },
          }),
        },
        responses: {
          201: ok("The booking.", ref("BookingSummary")),
          400: res.badRequest,
          ...authErrors,
          404: res.notFound,
          409: res.conflict,
        },
      },
      get: {
        tags: ["Bookings"],
        summary: "List your bookings",
        description: "Tutors see the sessions they teach; everyone else sees the ones they booked.",
        parameters: [pageParam],
        responses: {
          200: ok("A page of bookings, with both parties resolved.", pageOf(ref("Booking"))),
          ...authErrors,
        },
      },
    },
    "/bookings/direct": {
      post: {
        tags: ["Bookings"],
        summary: "Book a tutor straight from their profile",
        description:
          "The Find a Tutor path, with no application behind it. Starts REQUESTED for the tutor to confirm.",
        requestBody: {
          required: true,
          ...json({
            type: "object",
            required: ["tutorId", "startTime", "endTime"],
            properties: {
              tutorId: objectId("A bookable tutor at your institution."),
              subjectId: objectId("Defaults to the tutor's first subject."),
              startTime: dateTime("Session start."),
              endTime: dateTime("Session end; must be after startTime."),
              mode: enumOf(LessonMode),
              location: {
                type: "string",
                maxLength: 120,
                description: "Where to meet: a campus venue, or a place the student named themselves such as a coffee shop.",
              },
            },
          }),
        },
        responses: {
          201: ok("The booking.", ref("BookingSummary")),
          400: res.badRequest,
          ...authErrors,
          404: res.notFound,
          409: res.conflict,
        },
      },
    },
    "/bookings/{id}/confirm": {
      put: {
        tags: ["Bookings"],
        summary: "Confirm a requested booking",
        description:
          "The tutor accepts. Rechecked for clashes, then written to Google Calendar if one is linked.",
        parameters: [pathId("id", "Booking id.")],
        responses: {
          200: ok("The confirmed booking.", ref("BookingSummary")),
          ...authErrors,
          404: res.notFound,
          409: res.conflict,
        },
      },
    },
    "/bookings/{id}/cancel": {
      put: {
        tags: ["Bookings"],
        summary: "Cancel a booking",
        description: "Either party may cancel until the session is completed. The other is notified.",
        parameters: [pathId("id", "Booking id.")],
        requestBody: {
          ...json({
            type: "object",
            properties: { reason: { type: "string", example: "Clash with a lecture" } },
          }),
        },
        responses: {
          200: ok("The cancelled booking.", {
            type: "object",
            properties: {
              id: objectId("Booking id."),
              status: enumOf(BookingStatus),
              cancelledBy: objectId("Who cancelled."),
              reason: { type: "string", nullable: true },
            },
          }),
          ...authErrors,
          404: res.notFound,
          409: res.conflict,
        },
      },
    },
    "/bookings/{id}/reschedule": {
      put: {
        tags: ["Bookings"],
        summary: "Move a session to a new time",
        description: [
          "Either party may ask, for a session that is still requested or confirmed",
          "and not yet started. A tutor moving a session keeps it confirmed (and a",
          "requested one becomes confirmed); a student moving a confirmed session",
          "sends it back to `REQUESTED` for the tutor to confirm. The other party is",
          "notified and the Google Calendar event follows. A time the tutor already",
          "has a confirmed session in answers 409 `SLOT_CONFLICT`.",
        ].join("\n"),
        parameters: [pathId("id", "Booking id.")],
        requestBody: {
          required: true,
          ...json({
            type: "object",
            required: ["startTime", "endTime"],
            properties: {
              startTime: dateTime("New start."),
              endTime: dateTime("New end, after the start."),
              location: { type: "string", maxLength: 120 },
            },
          }),
        },
        responses: {
          200: ok("The moved booking.", {
            type: "object",
            properties: {
              id: objectId("Booking id."),
              status: enumOf(BookingStatus),
              startTime: dateTime("New start."),
              endTime: dateTime("New end."),
            },
          }),
          400: res.badRequest,
          ...authErrors,
          404: res.notFound,
          409: res.conflict,
        },
      },
    },
    "/bookings/{id}/check-in-code": {
      post: {
        tags: ["Bookings"],
        summary: "Issue a check-in code for a session",
        description:
          "The tutor of a confirmed session gets a fresh 6-digit code to show the student, as text and " +
          "as a QR code (qrPayload). It works from 15 minutes before the start until the end, lasts 5 " +
          "minutes, and replaces any earlier code. Only a hash of it is stored.",
        parameters: [pathId("id", "Booking id.")],
        responses: {
          201: ok("The code to show the student.", ref("CheckInCode")),
          ...authErrors,
          404: res.notFound,
          409: res.conflict
        },
      },
    },
    "/bookings/{id}/check-in": {
      post: {
        tags: ["Bookings"],
        summary: "Check in to a session",
        description:
          "The student of the session sends the code from the tutor's screen. Five wrong codes lock " +
          "check-in until the tutor issues a new code. Checking in again returns the first check-in. " +
          "The tutor is notified.",
        paramaters: [pathId("id", "Booking id.")],
        requestBody: {
          required: true,
          ...json({
            type: "object",
            required: ["code"],
            properties: {
              code: { type: "string", pattern: "^\\d{6}$", example: "042917" },
              method: { type: "string", enum: ["QR", "CODE"], description: "How the code was entered." }
            },
          }),
        },
        responses: {
          200: ok("The check-in.", ref("Attendance")),
          400: res.badRequest,
          ...authErrors,
          404: res.notFound,
          409: res.conflict,
          429: { description: "Too many wrong codes - the tutor has to issue a new one." }
        },
      },
    },
    "/bookings/{id}/notes": {
      put: {
        tags: ["Bookings"],
        summary: "Save the tutor's prep notes for a session",
        description:
          "The tutor's own checklist and side notes about the student or guardian. Private to " +
          "the tutor who wrote them: they are returned with GET /bookings for that tutor and " +
          "never for the student. The whole set is replaced on every save, so saving the same notes " +
          "twice changes nothing and, when notes edited offline are replayed, the last save wins.",
        parameters: [pathId("id", "Booking id.")],
        requestBody: { required: true, ...json(ref("TutorNotes")) },
        responses: {
          200: ok("The stored notes.", ref("TutorNotes")),
          400: res.badRequest,
          ...authErrors,
          404: res.notFound,
        },
      },
    },

    "/threads": {
      get: {
        tags: ["Threads"],
        summary: "List your conversations",
        description: [
          "Most recently active first. Conversations are opened by the API when",
          "a student accepts a tutor's offer or books a tutor directly, one per",
          "student-tutor pair. Participants carry only `fullName` and `role`.",
        ].join("\n"),
        parameters: [pageParam],
        responses: {
          200: ok(
            "A page of threads, each with the caller's unread count.",
            pageOf({
              allOf: [
                ref("Thread"),
                { type: "object", properties: { unread: { type: "integer", example: 2 } } },
              ],
            })
          ),
          ...authErrors,
        },
      },
    },
    "/threads/{id}/messages": {
      get: {
        tags: ["Threads"],
        summary: "Read a thread",
        description: "Opening a thread marks the other person's messages as read and clears the caller's unread count.",
        parameters: [pathId("id", "Thread id."), pageParam],
        responses: {
          200: ok("A page of messages, newest first.", pageOf(ref("Message"))),
          ...authErrors,
          404: res.notFound,
        },
      },
      post: {
        tags: ["Threads"],
        summary: "Send a message",
        description: [
          "`clientMessageId` makes this safe to retry: resending one already",
          "stored returns the stored message instead of duplicating it, which",
          "is what the app relies on when a send is retried after going offline.",
        ].join("\n"),
        parameters: [pathId("id", "Thread id.")],
        requestBody: {
          required: true,
          ...json({
            type: "object",
            required: ["body", "clientMessageId"],
            properties: {
              body: { type: "string", minLength: 1, maxLength: 2000 },
              clientMessageId: {
                type: "string",
                description: "Client-generated id, unique within the thread.",
              },
            },
          }),
        },
        responses: {
          201: ok("The message, newly stored or already stored.", ref("Message")),
          400: res.badRequest,
          ...authErrors,
          404: res.notFound,
        },
      },
    },

    "/notifications": {
      get: {
        tags: ["Notifications"],
        summary: "Your notification feed",
        parameters: [pageParam],
        responses: {
          200: ok("A page of notifications, newest first.", pageOf(ref("NotificationEvent"))),
          ...authErrors,
        },
      },
    },
    "/notifications/{id}/read": {
      put: {
        tags: ["Notifications"],
        summary: "Mark a notification read",
        description: "404 if it is already read, or not yours.",
        parameters: [pathId("id", "Notification id.")],
        responses: {
          200: ok("Marked read.", {
            type: "object",
            properties: { id: objectId("Notification id."), readAt: dateTime("When it was read.") },
          }),
          ...authErrors,
          404: res.notFound,
        },
      },
    },
    "/notification-tokens": {
      post: {
        tags: ["Notifications"],
        summary: "Register this device for push",
        description: "Upserts on the FCM token, so it is safe to call on every app start.",
        requestBody: {
          required: true,
          ...json({
            type: "object",
            required: ["token"],
            properties: {
              token: { type: "string", description: "FCM registration token." },
              platform: { type: "string", default: "android" },
            },
          }),
        },
        responses: {
          200: ok("Registered.", {
            type: "object",
            properties: { registered: { type: "boolean" } },
          }),
          400: res.badRequest,
          ...authErrors,
        },
      },
    },
    "/notification-tokens/{token}": {
      delete: {
        tags: ["Notifications"],
        summary: "Unregister a device",
        parameters: [pathId("token", "The FCM registration token.")],
        responses: { 204: noContent, ...authErrors },
      },
    },

    "/calendar/link": {
      post: {
        tags: ["Calendar"],
        summary: "Link a Google Calendar",
        description:
          "Exchanges the one-time auth code from the app's Google sign-in for a refresh token, stored encrypted.",
        requestBody: {
          required: true,
          ...json({
            type: "object",
            required: ["authCode"],
            properties: {
              authCode: {
                type: "string",
                description: "One-time OAuth code carrying the calendar.events scope.",
              },
              calendarId: { type: "string", default: "primary" },
            },
          }),
        },
        responses: {
          201: ok("Linked.", {
            type: "object",
            properties: {
              linked: { type: "boolean" },
              googleAccountEmail: { type: "string", format: "email" },
              syncEnabled: { type: "boolean" },
            },
          }),
          400: res.badRequest,
          ...authErrors,
        },
      },
      get: {
        tags: ["Calendar"],
        summary: "Whether a calendar is linked",
        responses: { 200: ok("The link, or `linked: false`.", ref("CalendarLink")), ...authErrors },
      },
      delete: {
        tags: ["Calendar"],
        summary: "Unlink the calendar",
        responses: { 204: noContent, ...authErrors },
      },
    },

    "/timesheets/summary": {
      get: {
        tags: ["Timesheets"],
        summary: "Hours and earnings for one month",
        description: "Derived from the caller's completed bookings.",
        parameters: [month("Month to total, `YYYY-MM`.")],
        responses: {
          200: ok("The month's totals.", {
            type: "object",
            properties: {
              month: { type: "string", example: "2026-09" },
              totalHours: { type: "number", example: 18.5 },
              totalEarnings: { type: "number", example: 5550 },
              currency: { type: "string", example: "ZAR" },
              sessionCount: { type: "integer", example: 9 },
            },
          }),
          400: res.badRequest,
          ...authErrors,
        },
      },
    },
    "/timesheets/periods": {
      get: {
        tags: ["Timesheets"],
        summary: "The last few claim periods, with their sessions",
        description: [
          "One call for the whole claims screen. Periods are derived from",
          "completed bookings rather than a claims collection, so the current",
          "month is the working draft and every earlier one is closed.",
        ].join("\n"),
        parameters: [
          query("count", "How many months back, 1-24.", {
            type: "integer",
            minimum: 1,
            maximum: 24,
            default: 3,
          }),
          query("tutorId", "Required for non-tutors; tutors always get their own."),
        ],
        responses: {
          200: ok("The periods, newest first.", {
            type: "object",
            properties: {
              currency: { type: "string", example: "ZAR" },
              ratePerHour: { type: "number", example: 300 },
              staffNumber: { type: "string" },
              department: { type: "string" },
              periods: { type: "array", items: ref("ClaimPeriod") },
            },
          }),
          400: res.badRequest,
          ...authErrors,
        },
      },
    },
    "/timesheets": {
      get: {
        tags: ["Timesheets"],
        summary: "Export a month as CSV",
        description:
          "Tutors get their own sessions; admins may pass `tutorId`, or omit it for the whole institution.",
        parameters: [
          month("Month to export, `YYYY-MM`."),
          query("tutorId", "Narrow to one tutor. Ignored when the caller is a tutor."),
        ],
        responses: {
          200: {
            description: "CSV with a header row.",
            content: {
              "text/csv": {
                schema: { type: "string" },
                example:
                  "date,tutorName,startTime,endTime,subjectName,studentName,hours,ratePerHour,lineTotal\n" +
                  "2026-09-03,Naledi Dlamini,14:00,15:30,Mathematics,Thabo Mokoena,1.5,300,450",
              },
            },
          },
          400: res.badRequest,
          ...authErrors,
        },
      },
    },

    ...crudPaths("subjects", "Subject"),
    ...crudPaths("provinces", "Province"),
    ...crudPaths("areas", "Area"),
  },

  components: {
    securitySchemes: {
      bearerAuth: {
        type: "http",
        scheme: "bearer",
        bearerFormat: "JWT",
        description:
          "A Firebase ID token. Paste the token on its own - Swagger UI adds the `Bearer ` prefix.",
      },
    },
    parameters: {
      PageParam: {
        name: "page",
        in: "query",
        required: false,
        schema: { type: "integer", minimum: 1, default: 1 },
        description: "1-based page number. Pages are 20 records.",
      },
    },
    responses: {
      ValidationError: {
        description:
          "The body or query was rejected. `field` names the offending field when there is one.",
        ...json(ref("Error")),
      },
      Unauthorized: {
        description: "Missing, invalid or expired Firebase ID token.",
        ...json(ref("Error")),
      },
      Forbidden: {
        description:
          "Authenticated but not allowed: wrong role, not the owner, unverified, or no TutorBridge profile yet.",
        ...json(ref("Error")),
      },
      NotFound: {
        description:
          "No such record, it is not visible to the caller, or the id in the path is not a " +
          "well-formed ObjectId - all three mean the same thing to the client.",
        ...json(ref("Error")),
      },
      Conflict: {
        description:
          "Conflicts with current state: a clashing slot, a repeat application, or an already-decided record.",
        ...json(ref("Error")),
      },
    },
    schemas: {
      Error: {
        type: "object",
        description: "Every failure answers with this shape.",
        properties: {
          error: {
            type: "object",
            properties: {
              code: { type: "string", example: "VALIDATION_ERROR" },
              message: { type: "string", example: "endTime must be a valid time after startTime" },
              field: { type: "string", nullable: true, example: "endTime" },
            },
          },
        },
      },
      NamedRef: {
        type: "object",
        description: "A reference record trimmed to what a picker needs.",
        properties: { id: objectId("Record id."), name: { type: "string" } },
      },
      Decision: {
        type: "object",
        required: ["decision"],
        properties: { decision: { type: "string", enum: ["accept", "reject"] } },
      },
      AvailabilitySlot: {
        type: "object",
        required: ["day", "startTime", "endTime"],
        properties: {
          day: enumOf(DayOfWeek),
          startTime: { type: "string", example: "14:00", description: "24-hour `HH:mm`." },
          endTime: { type: "string", example: "16:00", description: "24-hour `HH:mm`." },
        },
      },
      RateBand: {
        type: "object",
        required: ["min", "max"],
        properties: {
          min: { type: "integer", example: 150 },
          max: { type: "integer", example: 300 },
          currency: { type: "string", default: "ZAR" },
        },
      },
      Profile: {
        type: "object",
        description: "The trimmed profile /auth/bootstrap returns.",
        properties: {
          id: objectId("User id."),
          institutionId: objectId("Institution the user belongs to."),
          role: enumOf(UserRole),
          fullName: { type: "string" },
          verificationStatus: enumOf(VerificationStatus),
          profileComplete: { type: "integer", minimum: 0, maximum: 100 },
          mustChangePassword: {
            type: "boolean",
            description: "Still on an admin-issued temporary password - see `POST /auth/change-password`.",
          },
        },
      },
      User: {
        type: "object",
        description: "Reference fields come back populated on the routes that say so.",
        properties: {
          _id: objectId("User id."),
          institutionId: objectId("Institution the user belongs to."),
          role: enumOf(UserRole),
          fullName: { type: "string" },
          email: { type: "string", format: "email" },
          authProvider: enumOf(AuthProvider),
          phoneNumber: { type: "string" },
          profilePhotoUrl: { type: "string" },
          bio: { type: "string", maxLength: 1000 },
          hourlyRate: { type: "number", description: "ZAR per hour." },
          subjectIds: { type: "array", items: ref("Subject") },
          areaId: ref("Area"),
          gradeLevel: { type: "string" },
          department: { type: "string" },
          staffNumber: { type: "string" },
          availability: { type: "array", items: ref("AvailabilitySlot") },
          verificationStatus: enumOf(VerificationStatus),
          profileCompleteness: { type: "integer", minimum: 0, maximum: 100 },
          locale: enumOf(Locale),
          deletionScheduledFor: {
            ...dateTime("Set once deletion is scheduled."),
            nullable: true,
          },
          deactivatedAt: {
            ...dateTime("Set when an admin removes a tutor; never purged."),
            nullable: true,
          },
          mustChangePassword: { type: "boolean" },
          createdAt: dateTime("When the profile was created."),
          updatedAt: dateTime("When it last changed."),
        },
      },
      UserSettings: {
        type: "object",
        properties: {
          userId: objectId("Owner."),
          language: enumOf(Locale),
          notifications: {
            type: "object",
            properties: {
              newListing: { type: "boolean" },
              application: { type: "boolean" },
              booking: { type: "boolean" },
              message: { type: "boolean" },
              verification: { type: "boolean" },
            },
          },
          smsFallback: { type: "boolean" },
          biometricEntry: { type: "boolean" },
          theme: enumOf(Theme),
        },
      },
      Institution: {
        type: "object",
        properties: {
          _id: objectId("Institution id."),
          name: { type: "string" },
          slug: { type: "string" },
          contactEmail: { type: "string", format: "email" },
          phoneNumber: { type: "string" },
          provinceId: objectId("Province it sits in."),
          addressLine: { type: "string" },
          logoUrl: { type: "string" },
          defaultLocale: enumOf(Locale),
          status: enumOf(InstitutionStatus),
        },
      },
      Subject: {
        type: "object",
        properties: {
          _id: objectId("Subject id."),
          name: { type: "string", example: "Mathematics" },
          slug: { type: "string", example: "mathematics" },
        },
      },
      Province: {
        type: "object",
        properties: {
          _id: objectId("Province id."),
          name: { type: "string", example: "Gauteng" },
        },
      },
      Area: {
        type: "object",
        properties: {
          _id: objectId("Area id."),
          provinceId: objectId("Province it falls in."),
          name: { type: "string", example: "Sandton" },
        },
      },
      TuitionRequest: {
        type: "object",
        properties: {
          _id: objectId("Request id."),
          institutionId: objectId("Institution."),
          studentId: ref("User"),
          subjectId: ref("Subject"),
          areaId: ref("Area"),
          gradeLevel: { type: "string" },
          mode: enumOf(LessonMode),
          description: { type: "string", maxLength: 1000 },
          preferredRateBand: ref("RateBand"),
          availability: { type: "array", items: ref("AvailabilitySlot") },
          status: enumOf(TuitionRequestStatus),
          applicationCount: { type: "integer" },
          matchScore: { type: "number", minimum: 0, maximum: 100, nullable: true },
          clientRequestId: { type: "string", description: "The app's retry-safety id, if it sent one." },
          createdAt: dateTime("When it was posted."),
        },
      },
      Application: {
        type: "object",
        properties: {
          _id: objectId("Application id."),
          institutionId: objectId("Institution."),
          tuitionRequestId: ref("TuitionRequest"),
          tutorId: ref("User"),
          message: { type: "string", maxLength: 1000 },
          proposedRate: { type: "number", description: "ZAR per hour." },
          matchScore: { type: "number", minimum: 0, maximum: 100, nullable: true },
          status: enumOf(ApplicationStatus),
          decidedAt: { ...dateTime("When it was decided."), nullable: true },
          createdAt: dateTime("When it was sent."),
        },
      },
      Booking: {
        type: "object",
        properties: {
          _id: objectId("Booking id."),
          institutionId: objectId("Institution."),
          applicationId: {
            ...objectId("The accepted application it came from."),
            nullable: true,
            description: "Unset for direct bookings.",
          },
          tutorId: ref("User"),
          studentId: ref("User"),
          subjectName: { type: "string" },
          startTime: dateTime("Session start."),
          endTime: dateTime("Session end."),
          mode: enumOf(LessonMode),
          location: { type: "string", nullable: true },
          status: enumOf(BookingStatus),
          isTrial: { type: "boolean" },
          addressDisclosed: { type: "boolean" },
          cancelledBy: { ...objectId("Who cancelled."), nullable: true },
          cancellationReason: { type: "string", nullable: true },
          agreedRatePerHour: { type: "number", description: "ZAR per hour." },
          googleCalendarEventId: { type: "string", nullable: true },
          confirmedAt: { ...dateTime("When it was confirmed."), nullable: true },
          completedAt: { ...dateTime("When it was completed."), nullable: true },
          tutorNotes: {
            ...ref("TutorNotes"),
            description: "Only present for the tutor teaching the session.",
          },
          attendance: {
            type: "object",
            nullable: true,
            description: "Set once the student has checked in.",
            properties: {
              checkedInAt: dateTime("When the student checked in."),
              method: { type: "string", enum: ["QR", "CODE"] }
            },
          },
        },
      },
      TutorNotes: {
        type: "object",
        description: "A tutor's private prep notes for one session.",
        properties: {
          items: {
            type: "array",
            description: "The checklist, at most 50 rows. Blank rows are dropped on save.",
            items: {
              type: "object",
              properties: {
                text: { type: "string", maxLength: 200, example: "Mark last week's problem set" },
                done: { type: "boolean", example: false },
              },
            },
          },
          text: {
            type: "string",
            maxLength: 2000,
            description: "Free-text side notes.",
            example: "Guardian asked for a progress update before the test.",
          },
        },
      },
      CheckInCode: {
        type: "object",
        properties: {
          bookingId: objectId("Booking id."),
          code: { type: "string", example: "042917" },
          expiresAt: dateTime("When the code stops working."),
          qrPayload: { type: "string", example: "tutorbridge://checkin?booking=665f...&code=042917" }
        },
      },
      Attendance: {
        type: "object",
        properties: {
          bookingId: objectId("Booking id."),
          checkedInAt: dateTime("When the student checked in."),
          method: { type: "string", enum: ["QR", "CODE"] }
        },
      },
      BookingSummary: {
        type: "object",
        description: "What the write endpoints return; the app refetches the list for the rest.",
        properties: {
          id: objectId("Booking id."),
          status: enumOf(BookingStatus),
          startTime: dateTime("Session start."),
          endTime: dateTime("Session end."),
        },
      },
      Thread: {
        type: "object",
        properties: {
          _id: objectId("Thread id."),
          institutionId: objectId("Institution."),
          bookingId: { ...objectId("Booking it hangs off."), nullable: true },
          applicationId: { ...objectId("Application it hangs off."), nullable: true },
          participants: { type: "array", items: ref("User"), minItems: 2, maxItems: 2 },
          lastMessagePreview: { type: "string" },
          lastMessageAt: { ...dateTime("Last message."), nullable: true },
          unreadCount: {
            type: "object",
            additionalProperties: { type: "integer" },
            description: "Unread count per user id.",
          },
        },
      },
      Message: {
        type: "object",
        properties: {
          _id: objectId("Message id."),
          threadId: objectId("Thread it belongs to."),
          senderId: objectId("Who sent it."),
          body: { type: "string", minLength: 1, maxLength: 2000 },
          clientMessageId: { type: "string" },
          status: enumOf(MessageStatus),
          sentAt: dateTime("When it was sent."),
          readAt: { ...dateTime("When it was read."), nullable: true },
        },
      },
      NotificationEvent: {
        type: "object",
        properties: {
          _id: objectId("Notification id."),
          userId: objectId("Recipient."),
          category: enumOf(NotificationCategory),
          title: { type: "string" },
          body: { type: "string" },
          deepLink: {
            type: "string",
            example: "tutorbridge://bookings/66f1a2b3c4d5e6f708192a3b",
          },
          locale: enumOf(Locale),
          pushSentAt: { ...dateTime("When push was sent."), nullable: true },
          readAt: { ...dateTime("When it was read."), nullable: true },
          createdAt: dateTime("When it was raised."),
        },
      },
      VerificationDocument: {
        type: "object",
        description: "The file itself is not served here, only its metadata.",
        properties: {
          _id: objectId("Document id."),
          institutionId: objectId("Institution."),
          userId: objectId("Who submitted it."),
          type: enumOf(DocumentType),
          requestedSubjectId: { ...objectId("Subject claimed."), nullable: true },
          storagePath: { type: "string" },
          mimeType: { type: "string", example: "application/pdf" },
          sizeBytes: { type: "integer" },
          status: enumOf(VerificationStatus),
          submittedAt: dateTime("When it was uploaded."),
          reviewedAt: { ...dateTime("When it was reviewed."), nullable: true },
          rejectionReason: { type: "string", nullable: true },
        },
      },
      CalendarLink: {
        type: "object",
        properties: {
          linked: { type: "boolean" },
          googleAccountEmail: { type: "string", format: "email" },
          syncEnabled: { type: "boolean" },
          lastSyncedAt: { ...dateTime("Last successful sync."), nullable: true },
          lastSyncError: { type: "string", nullable: true },
        },
      },
      ClaimPeriod: {
        type: "object",
        properties: {
          id: { type: "string", example: "2026-09" },
          month: { type: "string", example: "2026-09" },
          isCurrent: { type: "boolean", description: "The current month is the working draft." },
          totalHours: { type: "number", example: 18.5 },
          claimValue: { type: "number", example: 5550 },
          sessionCount: { type: "integer", example: 9 },
          sessions: {
            type: "array",
            items: {
              type: "object",
              properties: {
                id: objectId("Booking id."),
                subjectName: { type: "string" },
                date: { type: "string", format: "date", example: "2026-09-03" },
                startTime: dateTime("Session start."),
                endTime: dateTime("Session end."),
                studentName: { type: "string" },
                location: { type: "string" },
                hours: { type: "number", example: 1.5 },
                amount: { type: "number", example: 450 },
              },
            },
          },
        },
      },
    },
  },
};
