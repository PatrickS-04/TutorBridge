package com.tutorbridge.app.data.model

/**
 * Wire shapes for backend_api. These mirror the JSON the routes return, one
 * for one - the mapping onto the `*Ui` models the screens render lives in
 * TutorBridgeRepository.
 *
 * Mongoose serialises documents with `_id`; routes that build their own
 * response body use `id`, so both spellings appear below depending on which
 * endpoint the type belongs to. Dates stay Strings (ISO-8601) and are parsed
 * where they are formatted, which keeps Gson free of custom type adapters.
 */

/** Every list endpoint goes through utils/paginate.ts and answers this shape. */
data class PageResponse<T>(
    val data: List<T> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 20,
    val totalPages: Int = 1
)

// ==================== REFERENCE DATA ====================

data class SubjectDto(val _id: String, val name: String, val slug: String? = null)

data class AreaDto(val _id: String, val name: String)

/** GET /reference/subjects-and-locations returns `id`, not `_id`. */
data class ReferenceDataDto(
    val subjects: List<NamedIdDto> = emptyList(),
    val provinces: List<ProvinceDto> = emptyList()
)

data class NamedIdDto(val id: String, val name: String)

data class ProvinceDto(val id: String, val name: String, val areas: List<NamedIdDto> = emptyList())

// ==================== USERS ====================

data class AvailabilityDto(
    val day: String, // MONDAY..SUNDAY
    val startTime: String, // "HH:mm"
    val endTime: String
)

data class UserDto(
    val _id: String,
    val fullName: String = "",
    val email: String = "",
    val role: String = "",
    val bio: String? = null,
    val hourlyRate: Double? = null,
    val subjectIds: List<SubjectDto> = emptyList(),
    val areaId: AreaDto? = null,
    val gradeLevel: String? = null,
    val department: String? = null,
    val staffNumber: String? = null,
    val availability: List<AvailabilityDto> = emptyList(),
    val verificationStatus: String = "UNVERIFIED",
    val profileCompleteness: Int = 0,
    val locale: String = "EN",
    /** Completed sessions, added by GET /users/tutors only. */
    val completedSessions: Int = 0,
    /** 0-100 fit with the caller's own requests, added by GET /users/tutors only. */
    val matchScore: Int = 0
)

/** A populated `tutorId` / `studentId` reference - the same document, fewer fields read. */
data class UserBriefDto(
    val _id: String,
    val fullName: String = "",
    val email: String = "",
    val verificationStatus: String = "UNVERIFIED"
)

data class NotificationPreferencesDto(
    val newListing: Boolean = true,
    val application: Boolean = true,
    val booking: Boolean = true,
    val message: Boolean = true,
    val verification: Boolean = true
)

data class UserSettingsDto(
    val language: String = "EN",
    val notifications: NotificationPreferencesDto = NotificationPreferencesDto(),
    val smsFallback: Boolean = true,
    val biometricEntry: Boolean = true,
    val theme: String = "SYSTEM"
)

data class UpdateProfileRequest(
    val fullName: String? = null,
    val bio: String? = null,
    val hourlyRate: Double? = null,
    val availability: List<AvailabilityDto>? = null,
    val department: String? = null
)

data class UpdateSettingsRequest(
    val language: String? = null,
    val notifications: NotificationPreferencesDto? = null,
    val smsFallback: Boolean? = null,
    val biometricEntry: Boolean? = null,
    val theme: String? = null
)

data class InstitutionDto(val _id: String, val name: String, val slug: String? = null)

// ==================== TUITION REQUESTS & APPLICATIONS ====================

data class TuitionRequestDto(
    val _id: String,
    val subjectId: SubjectDto? = null,
    val areaId: AreaDto? = null,
    val studentId: UserBriefDto? = null,
    val gradeLevel: String? = null,
    val mode: String = "IN_PERSON",
    val description: String? = null,
    val availability: List<AvailabilityDto> = emptyList(),
    val status: String = "OPEN",
    val applicationCount: Int = 0,
    /** Added by GET /tuition-requests for the student's own board. */
    val matchedTutorName: String? = null,
    val createdAt: String? = null
)

data class CreateTuitionRequest(
    val subjectId: String,
    val areaId: String,
    val gradeLevel: String? = null,
    val mode: String = "IN_PERSON",
    val description: String? = null,
    val availability: List<AvailabilityDto> = emptyList()
)

/** PUT /tuition-requests/:id - only the fields that are set are changed. */
data class UpdateTuitionRequest(
    val subjectId: String? = null,
    val areaId: String? = null,
    val gradeLevel: String? = null,
    val description: String? = null,
    val availability: List<AvailabilityDto>? = null
)

/** GET /tuition-requests/:id/applications - `tutorId` is populated here. */
data class RequestApplicationDto(
    val _id: String,
    val tutorId: UserBriefDto? = null,
    val tuitionRequestId: String? = null,
    val message: String? = null,
    val proposedRate: Double = 0.0,
    val status: String = "PENDING",
    val createdAt: String? = null
)

/** GET /applications/mine - the request is populated instead of the tutor. */
data class MyApplicationDto(
    val _id: String,
    val tuitionRequestId: TuitionRequestDto? = null,
    val message: String? = null,
    val proposedRate: Double = 0.0,
    val status: String = "PENDING",
    val createdAt: String? = null
)

data class ApplyRequest(val message: String?, val proposedRate: Double)

data class DecisionRequest(val decision: String) // "accept" | "reject"

// ==================== BOOKINGS ====================

data class BookingDto(
    val _id: String,
    val tutorId: UserBriefDto? = null,
    val studentId: UserBriefDto? = null,
    val subjectName: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val mode: String = "IN_PERSON",
    val location: String? = null,
    val status: String = "REQUESTED",
    val agreedRatePerHour: Double = 0.0,
    /** Only ever set for the tutor teaching the session - the server leaves it
     *  out of the student's own booking list. */
    val tutorNotes: TutorNotesDto? = null
)

/** The tutor's private prep notes for one session. Also the PUT body. */
data class TutorNotesDto(
    val items: List<TutorNoteItemDto> = emptyList(),
    val text: String = ""
)

data class TutorNoteItemDto(val text: String = "", val done: Boolean = false)

/** POST /bookings - schedules the session behind an already-accepted application. */
data class ApplicationBookingRequest(
    val applicationId: String,
    val startTime: String, // ISO-8601 instant
    val endTime: String,
    val location: String? = null
)

data class DirectBookingRequest(
    val tutorId: String,
    val subjectId: String? = null,
    val startTime: String, // ISO-8601 instant
    val endTime: String,
    val mode: String = "IN_PERSON",
    val location: String? = null
)

/** POST /bookings and /bookings/direct answer with `id`, not the whole document. */
data class BookingAckDto(val id: String, val status: String)

data class CancelRequest(val reason: String?)

/** PUT /bookings/:id/reschedule - the new window, as ISO-8601 instants. */
data class RescheduleRequest(
    val startTime: String,
    val endTime: String,
    val location: String? = null
)

/** The moved booking; [status] tells whether it still stands or now waits on the tutor. */
data class RescheduleAckDto(val id: String, val status: String)

// ==================== MESSAGING ====================

/** GET /threads - `participants` carry only a name, and `unread` is the caller's own count. */
data class ThreadDto(
    val _id: String,
    val participants: List<UserBriefDto> = emptyList(),
    val lastMessagePreview: String? = null,
    val lastMessageAt: String? = null,
    val unread: Int = 0
)

/** `senderId` is a bare id here - messages are not populated. */
data class MessageDto(
    val _id: String,
    val senderId: String = "",
    val body: String = "",
    val sentAt: String = "",
    val status: String = "SENT",
    val clientMessageId: String = ""
)

/** [clientMessageId] makes a retried send safe: the server returns the stored message instead of a duplicate. */
data class SendMessageRequest(val body: String, val clientMessageId: String)

// ==================== NOTIFICATIONS ====================

data class NotificationEventDto(
    val _id: String,
    val category: String = "BOOKING",
    val title: String = "",
    val body: String = "",
    val deepLink: String? = null,
    val readAt: String? = null,
    val createdAt: String? = null
)

// ==================== TIMESHEETS / CLAIMS ====================

data class TimesheetSessionDto(
    val id: String,
    val subjectName: String = "",
    val date: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val studentName: String = "",
    val location: String = "",
    val hours: Double = 0.0,
    val amount: Double = 0.0
)

data class TimesheetPeriodDto(
    val id: String,
    val month: String = "",
    val isCurrent: Boolean = false,
    val totalHours: Double = 0.0,
    val claimValue: Double = 0.0,
    val sessionCount: Int = 0,
    val sessions: List<TimesheetSessionDto> = emptyList()
)

data class TimesheetPeriodsDto(
    val currency: String = "ZAR",
    val ratePerHour: Double = 0.0,
    val staffNumber: String = "",
    val department: String = "",
    val periods: List<TimesheetPeriodDto> = emptyList()
)

// ==================== PUSH TOKENS ====================

data class RegisterPushTokenRequest(val token: String, val platform: String = "android")

// ==================== GOOGLE CALENDAR ====================

/** [authCode] is the one-time offline-access code from Play Services; the
 * backend trades it for the refresh token and keeps that side hidden. */
data class LinkCalendarRequest(val authCode: String)

data class CalendarLinkDto(
    val linked: Boolean = false,
    val googleAccountEmail: String? = null,
    val syncEnabled: Boolean = true,
    val lastSyncedAt: String? = null,
    val lastSyncError: String? = null
)
