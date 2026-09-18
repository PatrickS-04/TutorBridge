package com.tutorbridge.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.GoogleAuthProvider
import com.tutorbridge.app.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import kotlinx.coroutines.tasks.await

class TutorBridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TutorBridgeUiState())
    val uiState: StateFlow<TutorBridgeUiState> = _uiState.asStateFlow()

    init {
        if (Firebase.auth.currentUser != null) {
            val lastRole = AppPreferences(getApplication()).getLastRole() ?: UserRole.STUDENT
            _uiState.update { it.copy(role = lastRole, currentRoute = "biometric_lock") }
        }
    }

    fun selectRole(role: UserRole) {
        _uiState.update { it.copy(role = role) }
    }

    fun setAuthMode(mode: AuthMode) {
        _uiState.update { it.copy(authMode = mode, authErrorMessage = null) }
    }

    fun signUp(email: String, password: String) {
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            try {
                Firebase.auth.createUserWithEmailAndPassword(email, password).await()
                _uiState.update { it.copy(isAuthLoading = false) }
                completeAuth()
            } catch (exception: Exception) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = exception.message ?: "Could not create an account") }
            }
        }
    }

    fun signIn(email: String, password: String) {
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            try {
                Firebase.auth.signInWithEmailAndPassword(email, password).await()
                _uiState.update { it.copy(isAuthLoading = false) }
                completeAuth()
            } catch (exception: Exception) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = exception.message ?: "Could not sign in") }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                Firebase.auth.signInWithCredential(credential).await()
                _uiState.update { it.copy(isAuthLoading = false) }
                completeAuth()
            } catch (exception: Exception) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = exception.message ?: "Google Sign-In Failed") }
            }
        }
    }

    fun setAuthError(message: String) {
        _uiState.update { it.copy(authErrorMessage = message) }
    }

    fun completeAuth() {
        val role = _uiState.value.role ?: UserRole.STUDENT
        AppPreferences(getApplication()).saveLastRole(role)
        _uiState.update { state ->
            val nextDestination = if (state.role == UserRole.TUTOR) "tutor_home" else "student_home"
            state.copy(role = role, currentRoute = nextDestination)
        }
    }

    fun signOut() {
        Firebase.auth.signOut()
        AppPreferences(getApplication()).clear()
        _uiState.update { TutorBridgeUiState() }
    }

    fun navigateTo(route: String) {
        _uiState.update { it.copy(currentRoute = route) }
    }

    // ==================== CALENDAR OPERATIONS ====================

    fun selectCalendarDate(date: LocalDate) {
        _uiState.update { it.copy(selectedCalendarDate = date) }
    }

    fun setCalendarFilter(filterType: String) {
        _uiState.update { it.copy(calendarFilterType = filterType) }
    }

    fun addCalendarEvent(event: CalendarEventUi) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    calendarEvents = state.calendarEvents + event,
                    notifications = listOf(
                        NotificationUi(
                            id = "notif_${System.currentTimeMillis()}",
                            emoji = "📅",
                            title = "Campus Session Scheduled",
                            body = "${event.title} on ${event.date} at ${event.startTime}",
                            time = "Just now",
                            unread = true
                        )
                    ) + state.notifications
                )
            }
        }
    }

    // ==================== DIRECT BOOKING FLOW ====================

    fun selectTutor(tutor: TutorUi) {
        _uiState.update { it.copy(selectedTutor = tutor) }
    }

    fun selectSlot(slot: String) {
        _uiState.update { it.copy(selectedSlot = slot) }
    }

    fun selectLocation(location: String) {
        _uiState.update { it.copy(selectedLocation = location) }
    }

    fun selectBookingDate(date: String) {
        _uiState.update { it.copy(selectedBookingDate = date) }
    }

    fun sendBookingRequest() {
        _uiState.update { it.copy(currentRoute = "booking_sent") }
    }

    fun simulateAccepted(tutor: TutorUi) {
        viewModelScope.launch {
            val newEvent = CalendarEventUi(
                id = "booking_${System.currentTimeMillis()}",
                title = "${tutor.subject} with ${tutor.name}",
                subject = tutor.subject,
                date = _uiState.value.selectedBookingDate,
                startTime = _uiState.value.selectedSlot,
                endTime = "11:30 AM",
                type = CalendarEventType.LESSON,
                participantName = tutor.name,
                participantRole = "tutor",
                location = _uiState.value.selectedLocation.ifEmpty { tutor.campus },
                status = EventStatus.CONFIRMED,
                forRole = "both"
            )
            _uiState.update { state ->
                state.copy(
                    calendarEvents = state.calendarEvents + newEvent,
                    currentRoute = "booking_confirmed"
                )
            }
        }
    }

    fun simulateDeclined() {
        _uiState.update { it.copy(currentRoute = "booking_declined") }
    }

    fun acceptTutorBooking() {
        _uiState.update { it.copy(currentRoute = "tutor_home") }
    }

    fun declineTutorBooking() {
        _uiState.update { it.copy(currentRoute = "tutor_home") }
    }

    // ==================== TUITION REQUESTS (STUDENT) ====================

    fun updateDraftSubject(subject: String) {
        _uiState.update { it.copy(requestDraft = it.requestDraft.copy(subject = subject)) }
    }

    fun updateDraftNotes(notes: String) {
        _uiState.update { it.copy(requestDraft = it.requestDraft.copy(notes = notes)) }
    }

    fun updateDraftLevel(level: String) {
        _uiState.update { it.copy(requestDraft = it.requestDraft.copy(level = level)) }
    }

    fun updateDraftTimeWindow(timeWindow: String) {
        _uiState.update { it.copy(requestDraft = it.requestDraft.copy(timeWindow = timeWindow)) }
    }

    fun updateDraftVenue(venue: String) {
        _uiState.update { it.copy(requestDraft = it.requestDraft.copy(preferredVenue = venue)) }
    }

    fun updateDraftStep(step: Int) {
        _uiState.update { it.copy(requestDraft = it.requestDraft.copy(step = step)) }
    }

    fun submitTuitionRequest() {
        val draft = _uiState.value.requestDraft
        val newRequest = StudentRequestUi(
            id = "req_${System.currentTimeMillis()}",
            subject = draft.subject.ifBlank { "New Request" },
            courseCode = draft.subject.split(" ").firstOrNull() ?: "",
            topic = draft.topic,
            description = draft.notes,
            level = draft.level,
            timeWindow = draft.timeWindow,
            studentName = _uiState.value.userName,
            studentInitials = "LN",
            status = RequestStatus.OPEN,
            timestamp = "Just now",
            preferredVenue = draft.preferredVenue
        )

        _uiState.update { state ->
            state.copy(
                myRequests = listOf(newRequest) + state.myRequests,
                currentRoute = "my_requests",
                requestDraft = RequestDraft()
            )
        }
    }

    fun selectRequest(request: StudentRequestUi) {
        _uiState.update { it.copy(selectedRequest = request, currentRoute = "request_detail") }
    }

    fun acceptApplicant(requestId: String, applicantId: String) {
        _uiState.update { state ->
            state.copy(
                myRequests = state.myRequests.map { req ->
                    if (req.id == requestId) req.copy(status = RequestStatus.MATCHED) else req
                },
                currentRoute = "my_requests"
            )
        }
    }

    // ==================== TUITION REQUESTS (TUTOR BOARD) ====================

    fun acceptAndOfferSession(requestId: String) {
        _uiState.update { state ->
            state.copy(
                openRequests = state.openRequests.filterNot { it.id == requestId },
                myApplications = state.myApplications + (state.openRequests.find { it.id == requestId }
                    ?.copy(status = RequestStatus.OFFERED) ?: return@update state),
                currentRoute = "session_offer_sent"
            )
        }
    }

    // ==================== TUTOR: AVAILABILITY ====================

    fun setAvailabilityTab(tab: String) {
        _uiState.update { it.copy(availabilityTab = tab) }
    }

    fun toggleActiveDay(day: String) {
        _uiState.update { state ->
            val newDays = if (state.activeDays.contains(day)) state.activeDays - day else state.activeDays + day
            state.copy(activeDays = newDays)
        }
    }

    // ==================== TUTOR: CLAIMS ====================

    fun selectInstitution(id: String) {
        _uiState.update { it.copy(selectedInstitutionId = id) }
    }

    fun selectClaimPeriod(id: String) {
        _uiState.update { it.copy(selectedClaimPeriodId = id) }
    }

    // ==================== SESSION PREP ====================

    fun toggleSessionGoal(goalId: String) {
        _uiState.update { state ->
            state.copy(
                sessionGoals = state.sessionGoals.map { goal ->
                    if (goal.id == goalId) goal.copy(completed = !goal.completed) else goal
                }
            )
        }
    }

    // ==================== STUDENT ACCOUNT DELETION (POPIA) ====================

    fun setDeleteReason(reason: String) {
        _uiState.update { it.copy(deleteAccountReason = reason) }
    }

    fun setDeleteConfirmChecked(checked: Boolean) {
        _uiState.update { it.copy(deleteConfirmChecked = checked) }
    }

    fun setDeleteConfirmText(text: String) {
        _uiState.update { it.copy(deleteConfirmText = text) }
    }

    fun deleteStudentAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    role = null,
                    userName = "Liam Naidoo",
                    myRequests = emptyList(),
                    calendarEvents = state.calendarEvents.filter { it.participantRole == "tutor" },
                    currentRoute = "auth",
                    deleteConfirmChecked = false,
                    deleteConfirmText = ""
                )
            }
            onComplete()
        }
    }

    // ==================== THEME & PREFERENCES ====================

    fun toggleDarkTheme(isDark: Boolean) {
        _uiState.update { it.copy(isDarkTheme = isDark) }
    }

    fun toggleOfflineMode(offline: Boolean) {
        _uiState.update { it.copy(isOffline = offline) }
    }

    fun setLanguage(language: String) {
        _uiState.update { it.copy(selectedLanguage = language) }
    }

    fun toggleBookingUpdates(enabled: Boolean) {
        _uiState.update { it.copy(bookingUpdatesEnabled = enabled) }
    }

    fun toggleSessionReminders(enabled: Boolean) {
        _uiState.update { it.copy(sessionRemindersEnabled = enabled) }
    }

    fun toggleSmsFallback(enabled: Boolean) {
        _uiState.update { it.copy(smsFallbackEnabled = enabled) }
    }

    fun toggleBiometric(enabled: Boolean) {
        _uiState.update { it.copy(biometricEnabled = enabled) }
    }
}
