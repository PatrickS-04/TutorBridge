//All enumerations are stored in MongoDB and validated by Mongoose at the application layer

export enum UserRole {
  GUARDIAN_LEARNER = "GUARDIAN_LEARNER",
  TUTOR = "TUTOR",
  INSTITUTION_ADMIN = "INSTITUTION_ADMIN",
  //Runs the platform itself. Subjects, provinces and areas are shared by every
  //institution, so no single institution's admin may change them.
  PLATFORM_ADMIN = "PLATFORM_ADMIN",
}

export enum AuthProvider {
  GOOGLE = "GOOGLE",
  PASSWORD = "PASSWORD",
}

export enum VerificationStatus {
  UNVERIFIED = "UNVERIFIED",
  PENDING = "PENDING",
  VERIFIED = "VERIFIED",
  REJECTED = "REJECTED",
}

export enum DocumentType {
  IDENTITY_DOCUMENT = "IDENTITY_DOCUMENT",
  QUALIFICATION = "QUALIFICATION",
}

export enum Locale {
  EN = "EN",
  ZU = "ZU",
  AF = "AF",
}

export enum Theme {
  LIGHT = "LIGHT",
  DARK = "DARK",
  SYSTEM = "SYSTEM",
}

export enum LessonMode {
  ONLINE = "ONLINE",
  IN_PERSON = "IN_PERSON",
  HYBRID = "HYBRID",
}

export enum DayOfWeek {
  MONDAY = "MONDAY",
  TUESDAY = "TUESDAY",
  WEDNESDAY = "WEDNESDAY",
  THURSDAY = "THURSDAY",
  FRIDAY = "FRIDAY",
  SATURDAY = "SATURDAY",
  SUNDAY = "SUNDAY",
}

export enum TuitionRequestStatus {
  DRAFT = "DRAFT",
  OPEN = "OPEN",
  MATCHED = "MATCHED",
  CLOSED = "CLOSED",
  CANCELLED = "CANCELLED",
}

export enum ApplicationStatus {
  PENDING = "PENDING",
  ACCEPTED = "ACCEPTED",
  REJECTED = "REJECTED",
  WITHDRAWN = "WITHDRAWN",
}

export enum BookingStatus {
  REQUESTED = "REQUESTED",
  CONFIRMED = "CONFIRMED",
  TRIAL_RUNNING = "TRIAL_RUNNING",
  ACTIVE = "ACTIVE",
  COMPLETED = "COMPLETED",
  CANCELLED = "CANCELLED",
}

export enum MessageStatus {
  SENT = "SENT",
  DELIVERED = "DELIVERED",
  READ = "READ",
}

export enum NotificationCategory {
  NEW_LISTING = "NEW_LISTING",
  APPLICATION = "APPLICATION",
  BOOKING = "BOOKING",
  MESSAGE = "MESSAGE",
  VERIFICATION = "VERIFICATION",
}

export enum SyncState {
  SYNCED = "SYNCED",
  PENDING = "PENDING",
  FAILED = "FAILED",
}

export enum InstitutionStatus {
  ACTIVE = "ACTIVE",
  SUSPENDED = "SUSPENDED",
}

export enum ReportReason {
  HARASSMENT = "HARASSMENT",
  INAPPROPRIATE = "INAPPROPRIATE",
  UNSAFE = "UNSAFE",
  SPAM = "SPAM",
  OTHER = "OTHER",
}

export enum ReportStatus {
  OPEN = "OPEN",
  REVIEWED = "REVIEWED",
}
