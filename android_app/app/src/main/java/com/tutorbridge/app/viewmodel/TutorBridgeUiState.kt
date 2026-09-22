package com.tutorbridge.app.viewmodel

import com.tutorbridge.app.data.model.*
import java.time.LocalDate

/**
 * Everything the screens render. The lists start empty and are filled from
 * backend_api once the user is signed in - see TutorBridgeViewModel.refreshAll().
 *
 * Study materials and the learning history at the bottom are the exception:
 * no collection backs them yet, so they stay as fixed sample rows.
 */
data class TutorBridgeUiState(
    val currentRoute: String = "splash",
    val role: UserRole? = null,
    val authMode: AuthMode = AuthMode.SIGN_IN,
    val isAuthLoading: Boolean = false,
    val authErrorMessage: String? = null,
    val institutionName: String = "",
    val institutionOptions: List<InstitutionSummary> = emptyList(),
    // Named distinctly from `selectedInstitutionId` below, which is an
    // unrelated, pre-existing field for the tutor claims screen.
    val selectedSignupInstitutionId: String? = null,
    val userName: String = "",
    val userEmail: String = "",
    val userStaffId: String = "",
    /** The tutor's own rate, offered when they apply to a request. */
    val tutorHourlyRate: Double = 0.0,
    val isDarkTheme: Boolean = false,
    val isOffline: Boolean = false,
    val selectedLanguage: String = "English",
    val bookingUpdatesEnabled: Boolean = true,
    val sessionRemindersEnabled: Boolean = true,
    val smsFallbackEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,

    // Google Calendar: linked once, then every confirmed booking is written there.
    val calendarLinked: Boolean = false,
    val calendarAccountEmail: String? = null,
    val isCalendarLinking: Boolean = false,
    /** Outcome of the last link/unlink attempt, shown under the row. */
    val calendarMessage: String? = null,
    /** Why the server's last write to Google Calendar failed, if it did. The
     *  sync is fire-and-forget, so without this a broken link looks linked. */
    val calendarSyncError: String? = null,

    /** Set when a background refresh or a write to the server failed. */
    val dataErrorMessage: String? = null,
    val isRefreshing: Boolean = false,

    // Reference data (subjects and campus areas), for the new-request wizard
    val subjectOptions: List<NamedIdDto> = emptyList(),
    val areaOptions: List<NamedIdDto> = emptyList(),

    // Calendar
    val selectedCalendarDate: LocalDate = LocalDate.now(),
    val calendarFilterType: String = "all",
    val calendarEvents: List<CalendarEventUi> = emptyList(),

    // Tutors / Find
    val tutors: List<TutorUi> = emptyList(),
    val selectedTutor: TutorUi? = null,
    val selectedSlot: String = "",
    val selectedLocation: String = "",
    val selectedBookingDate: String = LocalDate.now().plusDays(1).toString(),

    // Student Requests (student view) / Requests board (tutor view)
    val myRequests: List<StudentRequestUi> = emptyList(),
    val openRequests: List<StudentRequestUi> = emptyList(),
    val myApplications: List<StudentRequestUi> = emptyList(),
    val selectedRequest: StudentRequestUi? = null,
    val requestDraft: RequestDraft = RequestDraft(),

    // Notifications
    val notifications: List<NotificationUi> = emptyList(),

    // Tutor: bookings still waiting on a decision
    val pendingBookings: List<PendingBookingUi> = emptyList(),

    // Tutor: institutions / claims
    val institutions: List<InstitutionUi> = emptyList(),
    val selectedInstitutionId: String = "claims",
    val selectedClaimPeriodId: String = "",

    // Tutor: availability
    val weeklyAvailability: List<DayWindowUi> = emptyList(),
    val activeDays: Set<String> = emptySet(),

    // Profile (tutor edit profile)
    val tutorProfileCompleteness: Int = 0,
    val tutorLanguagesSpoken: String = "",
    val tutorConsultationRoom: String = "",
    val currentProfile: UserDto? = null,
    val profileStatusMessage: String? = null,

    // Booking decline note
    val lastDeclineNote: String = "",

    // Tutor: prep notes for the session currently open on the prep screen.
    // Held here rather than read straight off the calendar event because the
    // tutor is editing them - the event stays the last thing the server sent.
    val selectedSessionId: String? = null,
    val sessionChecklist: List<SessionGoalUi> = emptyList(),
    val sessionSideNotes: String = "",
    /** What is typed in the "add a to-do" box, before it becomes a row. */
    val newChecklistItem: String = "",

    // Delete account
    val deleteAccountReason: String = "",
    val deleteConfirmChecked: Boolean = false,
    val deleteConfirmText: String = "",

    // ===== Not backed by the API yet: no collections exist for these =====
    val studentHealthIndexPercent: Int = 80,
    val lessonsTaught: Int = 0,
    val targetGrade: String = "A-",
    val learningHistory: List<LearningHistoryEntryUi> = sampleLearningHistory
) {
    /** The session the prep screen is showing, derived so it cannot go stale. */
    val selectedSession: CalendarEventUi?
        get() = calendarEvents.firstOrNull { it.id == selectedSessionId }
}

// ==================== LEARNING HISTORY (no backend collection) ====================

val sampleLearningHistory = listOf(
    LearningHistoryEntryUi(
        id = "lh1",
        title = "Lesson 4: Organic Chemistry Mechanisms",
        date = "Oct 2, 2026",
        summary = "Mastered electrophilic addition mechanisms. Quiz score: 92%."
    ),
    LearningHistoryEntryUi(
        id = "lh2",
        title = "Lesson 3: Acids, Bases & Buffers",
        date = "Sep 25, 2026",
        summary = "Worked through the Henderson-Hasselbalch equation and titration curves."
    )
)
