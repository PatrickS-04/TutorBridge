package com.tutorbridge.app.viewmodel

import com.tutorbridge.app.data.local.OutboxEntity
import com.tutorbridge.app.data.local.failedCount
import com.tutorbridge.app.data.local.pendingCount
import com.tutorbridge.app.data.local.pendingMessagesFor
import com.tutorbridge.app.data.local.pendingRequestCards
import com.tutorbridge.app.data.model.*
import com.tutorbridge.app.network.effectivelyOnline
import java.time.LocalDate

/** Claim history: how much is fetched first, how much each "Load earlier" adds, and the API's ceiling. */
const val CLAIM_MONTHS_DEFAULT = 12
const val CLAIM_MONTHS_STEP = 6
const val CLAIM_MONTHS_MAX = 24

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
    /** A non-error notice on the sign-in screen, e.g. that a reset link was sent. */
    val authInfoMessage: String? = null,
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
    /** The Settings switch "Force offline mode" - the user's choice, not what the network is doing. */
    val isOffline: Boolean = false,
    /** Whether the phone has a working internet connection, from NetworkMonitor. */
    val networkAvailable: Boolean = true,
    /** When the offline cache was last refreshed from the API (epoch millis); null if nothing is saved yet. */
    val cacheSavedAtMillis: Long? = null,
    /** Everything in the outbox for the signed-in user: what was done offline and has not reached the server, or was refused. */
    val outboxItems: List<OutboxEntity> = emptyList(),
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
    /** Good news worth a snackbar, e.g. that a session was moved. */
    val noticeMessage: String? = null,
    /** The signed-in user's TutorBridge id, which tells a chat which messages are theirs. */
    val userId: String = "",

    // Messaging
    val threads: List<ThreadUi> = emptyList(),
    val selectedThreadId: String? = null,
    val selectedThreadName: String = "",
    /** The open conversation, oldest first; grows upward as earlier pages load. */
    val chatMessages: List<MessageUi> = emptyList(),
    val chatPage: Int = 1,
    val chatTotalPages: Int = 1,
    val chatDraft: String = "",
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
    /** How many months of claim history have been asked for; grows with "Load earlier". */
    val claimMonthsLoaded: Int = CLAIM_MONTHS_DEFAULT,

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
    /** Things done offline that are still waiting to be sent. */
    val pendingSyncCount: Int
        get() = outboxItems.pendingCount()

    /** Things the server refused; they stay until the user retries or discards them. */
    val failedSyncCount: Int
        get() = outboxItems.failedCount()

    /** The open conversation as the user should see it: what the server has, plus what they wrote that is still on its way. */
    val visibleChat: List<MessageUi>
        get() = mergeMessages(existing = outboxItems.pendingMessagesFor(selectedThreadId), incoming = chatMessages)

    /** The student's own requests, with ones posted offline on top as "Waiting to sync". */
    val visibleMyRequests: List<StudentRequestUi>
        get() = outboxItems.pendingRequestCards() + myRequests

    /** What the app should act on: connected, and not forced offline. */
    val isOnline: Boolean
        get() = effectivelyOnline(networkAvailable, isOffline)

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
