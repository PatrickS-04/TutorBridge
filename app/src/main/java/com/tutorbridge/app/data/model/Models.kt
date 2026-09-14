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
    OPEN, MATCHED, OFFERED, COMPLETED
}

enum class ClaimStatus {
    ACTIVE_DRAFT, PAID
}

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
    val consultationVenue: String = ""
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
    val applicants: List<TutorApplicant> = emptyList()
)

data class TutorApplicant(
    val id: String,
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
    val forRole: String // "student", "tutor", "both"
)

data class NotificationUi(
    val id: String,
    val emoji: String,
    val title: String,
    val body: String,
    val time: String,
    val unread: Boolean
)

data class RequestDraft(
    val step: Int = 1,
    val subject: String = "",
    val level: String = "Undergraduate 1st Year",
    val timeWindow: String = "Weekday afternoons (Wed/Fri)",
    val preferredVenue: String = "Upper Campus — Leslie Social Sciences 2.10",
    val topic: String = "",
    val notes: String = ""
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
    val timeRange: String,
    val active: Boolean
)

data class OneOffSlotUi(
    val id: String,
    val date: String,
    val timeRange: String,
    val note: String = ""
)

// ==================== SESSION PREP ====================

data class SessionGoalUi(
    val id: String,
    val text: String,
    val completed: Boolean
)

data class StudyMaterialUi(
    val id: String,
    val fileName: String,
    val sizeLabel: String
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
