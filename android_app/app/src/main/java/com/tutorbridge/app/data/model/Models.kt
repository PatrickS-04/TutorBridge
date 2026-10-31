package com.tutorbridge.app.data.model

enum class UserRole {
    STUDENT, TUTOR
}

enum class AuthMode {
    SIGN_IN, SIGN_UP
}

enum class CalendarEventType {
    LESSON, TUTORIAL, WORKSHOP, EXAM_PREP, OFFICE_HOURS
}

enum class EventStatus {
    CONFIRMED, COMPLETED, CANCELLED
}

enum class RequestStatus {
    OPEN, MATCHED, OFFERED, COMPLETED, WITHDRAWN
}

enum class ClaimStatus {
    ACTIVE_DRAFT, PAID
}

/** One weekly availability window, parsed from the user's availability slots. */
data class AvailabilityWindowUi(
    val day: java.time.DayOfWeek,
    val start: java.time.LocalTime,
    val end: java.time.LocalTime
)

data class TutorUi(
    val id: String,
    val name: String,
    val initials: String,
    val subject: String,
    val courseCode: String,
    val rating: Double,
    val reviewsCount: Int,
    val hourlyRate: String,
    val campus: String,
    val availableSlots: List<String>,
    val bio: String,
    val tagList: List<String>,
    val matchPercent: Int = 0,
    val activeNow: Boolean = false,
    val activeToday: Boolean = false,
    val staffOrTutorId: String = "",
    val verified: Boolean = false,
    val department: String = "",
    val languages: List<String> = emptyList(),
    val totalConsultations: Int = 0,
    val consultationVenue: String = "",
    /** Weekly availability, which the booking flow turns into slots per date. */
    val windows: List<AvailabilityWindowUi> = emptyList()
)

data class StudentRequestUi(
    val id: String,
    val subject: String,
    val courseCode: String,
    val topic: String,
    val description: String = "",
    val level: String = "Undergraduate 1st Year",
    val timeWindow: String = "",
    val studentName: String,
    val studentInitials: String,
    val status: RequestStatus,
    val timestamp: String,
    val preferredVenue: String,
    val matchedTutorName: String = "",
    /** How many tutors applied; the applicants themselves load on the detail screen. */
    val applicantCount: Int = 0,
    val applicants: List<TutorApplicant> = emptyList(),
    /** Made offline and not yet sent: drawn as a card, but there is nothing on the server to open. */
    val pendingSync: Boolean = false,
    /** Ids and hours the edit form is prefilled from. */
    val subjectId: String = "",
    val areaId: String = "",
    val availability: List<AvailabilityDto> = emptyList()
)

data class TutorApplicant(
    /** The application's id - what accept/reject is sent against. */
    val id: String,
    /** The tutor behind it, so accepting can open their availability. */
    val tutorId: String,
    val name: String,
    val initials: String,
    val verified: Boolean,
    val note: String
)

data class CalendarEventUi(
    val id: String,
    val title: String,
    val subject: String,
    val date: String, // YYYY-MM-DD
    val startTime: String,
    val endTime: String,
    val type: CalendarEventType,
    val participantName: String,
    val participantRole: String,
    val location: String,
    val status: EventStatus,
    val forRole: String, // "student", "tutor", "both"
    /** The tutor's prep checklist for this session; empty for the student. */
    val checklist: List<SessionGoalUi> = emptyList(),
    /** The tutor's free-text side notes about this student or guardian. */
    val sideNotes: String = "",
    /** The session's window as the server sent it (ISO-8601), which a reschedule keeps the length of. */
    val startIso: String = "",
    val endIso: String = "",
    /** Set when this session is one of a weekly series; the same for every session in it. */
    val seriesId: String? = null,
    /** Its place in the series, from 1; 0 for a single session. */
    val seriesIndex: Int = 0,
    /** How many sessions the series has; 0 for a single session. */
    val seriesCount: Int = 0
) {
    val isSeries: Boolean get() = seriesId != null && seriesCount > 1
}

// ==================== MESSAGING ====================

/** One row of the Messages list: the other person, and what was said last. */
data class ThreadUi(
    val id: String,
    val otherName: String,
    val initials: String,
    val preview: String,
    val time: String,
    val unread: Int
)

data class MessageUi(
    val id: String,
    val body: String,
    /** Sent by the signed-in user, so it sits on the right. */
    val mine: Boolean,
    val time: String,
    /** The server's ISO sentAt, which is also what orders the conversation. */
    val sentAt: String,
    /** Made by the app when the message was written; the same id comes back on the server's copy, which is how the two are matched up. */
    val clientId: String = "",
    /** Still on its way: shown straight away, replaced by the stored copy once the server answers. */
    val pending: Boolean = false,
    /** Pending and sitting in the offline queue, not being sent right now - the screen says "waiting" rather than "sending". */
    val queued: Boolean = false
)

/** A booking a student requested, waiting for the tutor to confirm or decline. */
data class PendingBookingUi(
    val id: String,
    val studentName: String,
    val initials: String,
    val subjectName: String,
    val whenLabel: String,
    val location: String,
    /** Set when this waiting session is one of a weekly series the student booked in one go. */
    val seriesId: String? = null,
    val seriesIndex: Int = 0,
    val seriesCount: Int = 0
)

data class NotificationUi(
    val id: String,
    val emoji: String,
    val title: String,
    val body: String,
    /** When it was sent (ISO-8601); the screen says how long ago, in the language the app is showing. */
    val createdAt: String,
    val unread: Boolean
)

data class RequestDraft(
    val step: Int = 1,
    val subject: String = "",
    val subjectId: String = "",
    val level: String = "Undergraduate 1st Year",
    val timeWindow: String = "",
    val preferredVenue: String = "",
    val areaId: String = "",
    /** Days and hours picked in step 3, sent as the request's availability. */
    val days: Set<java.time.DayOfWeek> = emptySet(),
    val startTime: String = "14:00",
    val endTime: String = "17:00",
    val topic: String = "",
    val notes: String = "",
    /** Set while an existing request is being edited; null for a new one. */
    val editingRequestId: String? = null
)

// ==================== TUTOR: CLAIMS & TIMESHEETS ====================

data class ClaimSessionUi(
    val id: String,
    val courseCode: String,
    val date: String,
    val timeRange: String,
    val amount: String,
    val studentName: String,
    val studentIdTag: String,
    val topic: String,
    val hours: Double,
    val checkedIn: Boolean = true
)

data class ClaimPeriodUi(
    val id: String,
    val label: String, // "August 2026"
    val status: ClaimStatus,
    val totalHours: Double,
    val sessionsCount: Int,
    val claimValue: Double,
    val claimRef: String = "",
    val sessions: List<ClaimSessionUi> = emptyList()
)

data class InstitutionUi(
    val id: String,
    val shortName: String, // "UCT"
    val fullName: String,
    val faculty: String,
    val staffId: String,
    val rate: Double,
    val claimPeriods: List<ClaimPeriodUi>
)

// ==================== TUTOR: AVAILABILITY ====================

data class DayWindowUi(
    val day: String,
    /** DayOfWeek enum name (e.g. "MONDAY") - what edits and saves are keyed on. */
    val dayName: String,
    val timeRange: String,
    val active: Boolean
)

// ==================== SESSION NOTES ====================

/** One row on a tutor's checklist for a session. */
data class SessionGoalUi(
    val id: String,
    val text: String,
    val completed: Boolean
)

data class LearningHistoryEntryUi(
    val id: String,
    val title: String,
    val date: String,
    val summary: String
)

// ==================== PROFILE ====================

data class UserProfileUi(
    val name: String,
    val email: String,
    val staffId: String = "",
    val role: String = "",
    val active: Boolean = true,
    val profileCompleteness: Int = 100,
    val languagesSpoken: String = "",
    val consultationRoom: String = ""
)
