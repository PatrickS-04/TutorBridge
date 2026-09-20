package com.tutorbridge.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tutorbridge.app.Retrofit.RetrofitInstance
import com.tutorbridge.app.data.model.*
import com.tutorbridge.app.data.repository.AuthRepository
import com.tutorbridge.app.data.repository.AuthResult
import com.tutorbridge.app.data.repository.TutorBridgeRepository
import com.tutorbridge.app.data.repository.toCalendarEvent
import com.tutorbridge.app.data.repository.toDayWindows
import com.tutorbridge.app.data.repository.toPendingBookingUi
import com.tutorbridge.app.data.repository.toWindowUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.google.firebase.auth.auth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import kotlinx.coroutines.tasks.await

class TutorBridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(RetrofitInstance.authApi)
    private val repository = TutorBridgeRepository(RetrofitInstance.tutorBridgeApi)

    private val _uiState = MutableStateFlow(TutorBridgeUiState())
    val uiState: StateFlow<TutorBridgeUiState> = _uiState.asStateFlow()

    /** The booking the student just requested, so "check for a reply" knows what to look at. */
    private var lastBookingId: String? = null

    init {
        val preferences = AppPreferences(getApplication())
        val enabledBiometric = preferences.getEnabledBiometric()
        val enabledSMSFallback = preferences.getEnabledSMSFallback()
        val enabledSessionReminders = preferences.getEnabledSessionReminders()
        val enabledBookingUpdates = preferences.getEnabledBookingUpdates()

        if (Firebase.auth.currentUser != null && enabledBiometric) {
            val lastRole = AppPreferences(getApplication()).getLastRole() ?: UserRole.STUDENT
            _uiState.update { it.copy(role = lastRole, biometricEnabled = true, smsFallbackEnabled = enabledSMSFallback,
                sessionRemindersEnabled = enabledSessionReminders, bookingUpdatesEnabled = enabledBookingUpdates, currentRoute = "biometric_lock") }
        } else {
            if (Firebase.auth.currentUser != null) {
                Firebase.auth.signOut()
            }
            _uiState.update { it.copy(biometricEnabled = enabledBiometric, smsFallbackEnabled = enabledSMSFallback,
                sessionRemindersEnabled = enabledSessionReminders, bookingUpdatesEnabled = enabledBookingUpdates) }
        }
        loadInstitutions()
    }

    private fun loadInstitutions() {
        viewModelScope.launch {
            val institutions = authRepository.listInstitutions()
            _uiState.update { state ->
                state.copy(
                    institutionOptions = institutions,
                    selectedSignupInstitutionId = state.selectedSignupInstitutionId ?: institutions.firstOrNull()?.id,
                    institutionName = institutions.firstOrNull()?.name ?: state.institutionName
                )
            }
        }
    }

    fun selectRole(role: UserRole) {
        _uiState.update { it.copy(role = role) }
    }

    fun selectSignupInstitution(id: String) {
        val name = _uiState.value.institutionOptions.firstOrNull { it.id == id }?.name
        _uiState.update { it.copy(selectedSignupInstitutionId = id, institutionName = name ?: it.institutionName) }
    }

    fun setAuthMode(mode: AuthMode) {
        _uiState.update { it.copy(authMode = mode, authErrorMessage = null) }
    }

    fun signUp(fullName: String, email: String, password: String) {
        val institutionId = _uiState.value.selectedSignupInstitutionId
        if (institutionId == null) {
            _uiState.update { it.copy(authErrorMessage = "Select your institution first") }
            return
        }
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            try {
                Firebase.auth.createUserWithEmailAndPassword(email, password).await()
                val profileUpdate = UserProfileChangeRequest.Builder().setDisplayName(fullName).build()
                Firebase.auth.currentUser?.updateProfile(profileUpdate)?.await()
                finishSignIn(institutionId, fullName)
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
                finishSignIn(institutionId = null, fullName = null)
            } catch (exception: Exception) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = exception.message ?: "Could not sign in") }
            }
        }
    }

    /**
     * [idToken] is the raw Google ID token from Credential Manager
     * (GoogleAuthentication.idTokenRequest), exchanged for a Firebase
     * session. Firebase is the session of record for TutorBridge.
     */
    fun signInWithGoogle(idToken: String) {
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                Firebase.auth.signInWithCredential(credential).await()
                finishSignIn(_uiState.value.selectedSignupInstitutionId, fullName = null)
            } catch (exception: Exception) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = exception.message ?: "Google Sign-In Failed") }
            }
        }
    }

    /**
     * Firebase has authenticated the user; now resolve their TutorBridge
     * profile so we know their role and institution. A failure here means the
     * Firebase account is real but the backend profile could not be created
     * or read, so we surface it rather than dropping the user into the app
     * with a default role.
     */
    private suspend fun finishSignIn(institutionId: String?, fullName: String?) {
        when (val result = authRepository.bootstrapProfile(institutionId, fullName)) {
            is AuthResult.Success -> {
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        role = roleFromBackend(result.profile.role),
                        userName = result.profile.fullName
                    )
                }
                completeAuth()
            }
            is AuthResult.Failure -> {
                Firebase.auth.signOut()
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = result.message) }
            }
        }
    }

    private fun roleFromBackend(backendRole: String): UserRole =
        if (backendRole == "TUTOR") UserRole.TUTOR else UserRole.STUDENT

    fun setAuthError(message: String) {
        _uiState.update { it.copy(authErrorMessage = message) }
    }

    fun completeAuth() {
        val role = _uiState.value.role ?: UserRole.STUDENT
        val user = Firebase.auth.currentUser
        AppPreferences(getApplication()).saveLastRole(role)
        _uiState.update { state ->
            val nextDestination = if (state.role == UserRole.TUTOR) "tutor_home" else "student_home"
            state.copy(role = role, currentRoute = nextDestination, userName = user?.displayName ?:
                user?.email?.substringBefore("@") ?: state.userName,
                userEmail = user?.email ?: state.userEmail)
        }
        refreshAll()
    }

    fun signOut() {
        Firebase.auth.signOut()
        AppPreferences(getApplication()).clear()
        lastBookingId = null
        _uiState.update { TutorBridgeUiState() }
        loadInstitutions()
    }

    fun navigateTo(route: String) {
        _uiState.update { it.copy(currentRoute = route) }
        // Screens that are worth having fresh the moment they are opened.
        when (route) {
            "calendar", "student_home", "tutor_home" -> loadBookings()
            "notifications" -> loadNotifications(markRead = true)
            "find_tutors" -> loadTutors()
            "my_requests", "tutor_requests" -> loadRequests()
            "claims_export" -> loadClaims()
        }
    }

    fun dismissDataError() {
        _uiState.update { it.copy(dataErrorMessage = null) }
    }

    // ==================== LOADING FROM THE API ====================

    /** Everything the signed-in user's screens need. Each loader runs on its own. */
    fun refreshAll() {
        loadProfile()
        loadSettings()
        loadReferenceData()
        loadTutors()
        loadRequests()
        loadBookings()
        loadNotifications()
        if (_uiState.value.role == UserRole.TUTOR) loadClaims()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            repository.me().onSuccess { user ->
                val windows = user.availability.mapNotNull { it.toWindowUi() }
                _uiState.update { state ->
                    state.copy(
                        role = roleFromBackend(user.role),
                        userName = user.fullName.ifBlank { state.userName },
                        userEmail = user.email.ifBlank { state.userEmail },
                        userStaffId = user.staffNumber.orEmpty(),
                        tutorHourlyRate = user.hourlyRate ?: 0.0,
                        tutorProfileCompleteness = user.profileCompleteness,
                        tutorLanguagesSpoken = user.locale.let {
                            when (it) {
                                "ZU" -> "isiZulu"
                                "AF" -> "Afrikaans"
                                else -> "English"
                            }
                        },
                        tutorConsultationRoom = user.areaId?.name.orEmpty(),
                        weeklyAvailability = windows.toDayWindows(),
                        activeDays = windows.map { it.day.name }.toSet()
                    )
                }
            }.onFailure { reportError(it) }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            repository.settings().onSuccess { settings ->
                _uiState.update {
                    it.copy(
                        selectedLanguage = when (settings.language) {
                            "ZU" -> "isiZulu"
                            "AF" -> "Afrikaans"
                            else -> "English"
                        },
                        isDarkTheme = settings.theme == "DARK",
                        smsFallbackEnabled = settings.smsFallback,
                        bookingUpdatesEnabled = settings.notifications.booking,
                        sessionRemindersEnabled = settings.notifications.message
                    )
                }
            }.onFailure { reportError(it) }
        }
    }

    private fun loadReferenceData() {
        viewModelScope.launch {
            repository.referenceData().onSuccess { reference ->
                _uiState.update { state ->
                    state.copy(
                        subjectOptions = reference.subjects,
                        areaOptions = reference.provinces.flatMap { it.areas }
                    )
                }
            }.onFailure { reportError(it) }
        }
    }

    private fun loadTutors() {
        if (_uiState.value.role == UserRole.TUTOR) return
        viewModelScope.launch {
            repository.tutors().onSuccess { tutors ->
                _uiState.update { state ->
                    state.copy(
                        tutors = tutors,
                        selectedTutor = tutors.firstOrNull { it.id == state.selectedTutor?.id } ?: state.selectedTutor
                    )
                }
            }.onFailure { reportError(it) }
        }
    }

    private fun loadRequests() {
        viewModelScope.launch {
            if (_uiState.value.role == UserRole.TUTOR) {
                repository.openRequests().onSuccess { requests ->
                    _uiState.update { it.copy(openRequests = requests) }
                }.onFailure { reportError(it) }
                repository.myApplications().onSuccess { applications ->
                    _uiState.update { it.copy(myApplications = applications) }
                }.onFailure { reportError(it) }
            } else {
                repository.myRequests().onSuccess { requests ->
                    _uiState.update { it.copy(myRequests = requests) }
                }.onFailure { reportError(it) }
            }
        }
    }

    private fun loadBookings() {
        viewModelScope.launch {
            val isTutor = _uiState.value.role == UserRole.TUTOR
            repository.bookings().onSuccess { bookings ->
                _uiState.update { state ->
                    state.copy(
                        calendarEvents = bookings
                            .filter { it.status != "CANCELLED" }
                            .mapNotNull { it.toCalendarEvent(isTutor) },
                        pendingBookings = if (isTutor) {
                            bookings.filter { it.status == "REQUESTED" }.mapNotNull { it.toPendingBookingUi() }
                        } else {
                            emptyList()
                        }
                    )
                }
            }.onFailure { reportError(it) }
        }
    }

    private fun loadNotifications(markRead: Boolean = false) {
        viewModelScope.launch {
            repository.notifications().onSuccess { notifications ->
                _uiState.update { it.copy(notifications = notifications) }
                // Opening the screen is what counts as reading them.
                if (markRead) {
                    notifications.filter { it.unread }.forEach { repository.markNotificationRead(it.id) }
                }
            }.onFailure { reportError(it) }
        }
    }

    private fun loadClaims() {
        if (_uiState.value.role != UserRole.TUTOR) return
        viewModelScope.launch {
            val institutionName = repository.institution().getOrNull()?.name ?: _uiState.value.institutionName
            repository.claims(institutionName).onSuccess { institutions ->
                _uiState.update { state ->
                    state.copy(
                        institutions = institutions,
                        selectedInstitutionId = institutions.firstOrNull()?.id ?: state.selectedInstitutionId,
                        selectedClaimPeriodId = institutions.firstOrNull()?.claimPeriods?.firstOrNull()?.id
                            ?: state.selectedClaimPeriodId
                    )
                }
            }.onFailure { reportError(it) }
        }
    }

    private fun reportError(error: Throwable) {
        _uiState.update { it.copy(dataErrorMessage = error.message ?: "Could not reach the server") }
    }

    // ==================== CALENDAR ====================

    fun selectCalendarDate(date: LocalDate) {
        _uiState.update { it.copy(selectedCalendarDate = date) }
    }

    fun setCalendarFilter(filterType: String) {
        _uiState.update { it.copy(calendarFilterType = filterType) }
    }

    // ==================== DIRECT BOOKING FLOW ====================

    fun selectTutor(tutor: TutorUi) {
        _uiState.update {
            it.copy(
                selectedTutor = tutor,
                selectedLocation = tutor.consultationVenue.ifBlank { it.selectedLocation },
                selectedSlot = ""
            )
        }
    }

    fun selectSlot(slot: String) {
        _uiState.update { it.copy(selectedSlot = slot) }
    }

    fun selectLocation(location: String) {
        _uiState.update { it.copy(selectedLocation = location) }
    }

    fun selectBookingDate(date: String) {
        _uiState.update { it.copy(selectedBookingDate = date, selectedSlot = "") }
    }

    /** Books the selected tutor for the selected date and slot (one hour). */
    fun sendBookingRequest() {
        val state = _uiState.value
        val tutor = state.selectedTutor
        val start = startOfSelectedSlot(state)
        if (tutor == null || start == null) {
            _uiState.update { it.copy(dataErrorMessage = "Pick a date and a time slot first") }
            return
        }

        _uiState.update { it.copy(isRefreshing = true, dataErrorMessage = null) }
        viewModelScope.launch {
            val request = DirectBookingRequest(
                tutorId = tutor.id,
                subjectId = null, // the tutor's own subject is used server-side
                startTime = start.toInstant().toString(),
                endTime = start.plusHours(1).toInstant().toString(),
                mode = "IN_PERSON",
                location = state.selectedLocation.ifBlank { null }
            )
            repository.bookTutorDirectly(request)
                .onSuccess { ack ->
                    lastBookingId = ack.id
                    _uiState.update { it.copy(isRefreshing = false, currentRoute = "booking_sent") }
                    loadBookings()
                }
                .onFailure {
                    _uiState.update { state2 ->
                        state2.copy(isRefreshing = false, dataErrorMessage = it.message ?: "Could not send the booking")
                    }
                }
        }
    }

    /**
     * Replaces the old "simulate accepted / declined" buttons: re-reads the
     * booking the student just sent and routes on what the tutor actually did.
     */
    fun checkBookingStatus() {
        val bookingId = lastBookingId ?: return
        viewModelScope.launch {
            repository.bookings().onSuccess { bookings ->
                val booking = bookings.firstOrNull { it._id == bookingId }
                val isTutor = _uiState.value.role == UserRole.TUTOR
                _uiState.update { state ->
                    state.copy(
                        calendarEvents = bookings
                            .filter { it.status != "CANCELLED" }
                            .mapNotNull { it.toCalendarEvent(isTutor) },
                        currentRoute = when (booking?.status) {
                            "CONFIRMED", "ACTIVE", "COMPLETED" -> "booking_confirmed"
                            "CANCELLED" -> "booking_declined"
                            else -> state.currentRoute
                        },
                        dataErrorMessage = if (booking?.status == "REQUESTED") {
                            "Still waiting for the tutor to reply"
                        } else {
                            state.dataErrorMessage
                        }
                    )
                }
            }.onFailure { reportError(it) }
        }
    }

    fun acceptTutorBooking(bookingId: String) {
        viewModelScope.launch {
            repository.confirmBooking(bookingId)
                .onSuccess {
                    _uiState.update { it.copy(currentRoute = "tutor_home") }
                    loadBookings()
                }
                .onFailure { reportError(it) }
        }
    }

    fun declineTutorBooking(bookingId: String) {
        viewModelScope.launch {
            repository.cancelBooking(bookingId, "The tutor is not available at this time")
                .onSuccess {
                    _uiState.update { it.copy(currentRoute = "tutor_home") }
                    loadBookings()
                }
                .onFailure { reportError(it) }
        }
    }

    // ==================== TUITION REQUESTS (STUDENT) ====================

    fun selectDraftSubject(id: String, name: String) {
        _uiState.update { it.copy(requestDraft = it.requestDraft.copy(subjectId = id, subject = name)) }
    }

    fun selectDraftArea(id: String, name: String) {
        _uiState.update { it.copy(requestDraft = it.requestDraft.copy(areaId = id, preferredVenue = name)) }
    }

    fun toggleDraftDay(day: DayOfWeek) {
        _uiState.update { state ->
            val draft = state.requestDraft
            val days = if (draft.days.contains(day)) draft.days - day else draft.days + day
            state.copy(requestDraft = draft.copy(days = days))
        }
    }

    fun updateDraftHours(startTime: String, endTime: String) {
        _uiState.update { it.copy(requestDraft = it.requestDraft.copy(startTime = startTime, endTime = endTime)) }
    }

    fun updateDraftNotes(notes: String) {
        _uiState.update { it.copy(requestDraft = it.requestDraft.copy(notes = notes)) }
    }

    fun updateDraftLevel(level: String) {
        _uiState.update { it.copy(requestDraft = it.requestDraft.copy(level = level)) }
    }

    fun updateDraftStep(step: Int) {
        _uiState.update { it.copy(requestDraft = it.requestDraft.copy(step = step)) }
    }

    fun submitTuitionRequest() {
        val draft = _uiState.value.requestDraft
        if (draft.subjectId.isBlank() || draft.areaId.isBlank()) {
            _uiState.update { it.copy(dataErrorMessage = "Choose a subject and a campus area first") }
            return
        }

        _uiState.update { it.copy(isRefreshing = true, dataErrorMessage = null) }
        viewModelScope.launch {
            val body = CreateTuitionRequest(
                subjectId = draft.subjectId,
                areaId = draft.areaId,
                gradeLevel = draft.level.ifBlank { null },
                mode = "IN_PERSON", // the wizard is campus-based; no online option is offered
                description = draft.notes,
                availability = draft.days.map { AvailabilityDto(it.name, draft.startTime, draft.endTime) }
            )
            repository.createRequest(body)
                .onSuccess {
                    _uiState.update {
                        it.copy(isRefreshing = false, currentRoute = "my_requests", requestDraft = RequestDraft())
                    }
                    loadRequests()
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(isRefreshing = false, dataErrorMessage = it.message ?: "Could not post the request")
                    }
                }
        }
    }

    /** Opens a request and pulls the tutors who have applied to it. */
    fun selectRequest(request: StudentRequestUi) {
        _uiState.update { it.copy(selectedRequest = request, currentRoute = "request_detail") }
        viewModelScope.launch {
            repository.applicants(request.id).onSuccess { applicants ->
                _uiState.update { state ->
                    val open = state.selectedRequest
                    if (open?.id == request.id) {
                        state.copy(selectedRequest = open.copy(applicants = applicants))
                    } else {
                        state
                    }
                }
            }.onFailure { reportError(it) }
        }
    }

    fun acceptApplicant(requestId: String, applicationId: String) {
        viewModelScope.launch {
            repository.decideApplication(applicationId, accept = true)
                .onSuccess {
                    _uiState.update { it.copy(currentRoute = "my_requests") }
                    loadRequests()
                }
                .onFailure { reportError(it) }
        }
    }

    // ==================== TUITION REQUESTS (TUTOR BOARD) ====================

    /** The tutor offers to take a request - an application against it. */
    fun acceptAndOfferSession(requestId: String) {
        viewModelScope.launch {
            val rate = _uiState.value.tutorHourlyRate
            repository.applyToRequest(requestId, "I can take this session.", rate)
                .onSuccess {
                    _uiState.update { it.copy(currentRoute = "session_offer_sent") }
                    loadRequests()
                }
                .onFailure { reportError(it) }
        }
    }

    // ==================== TUTOR: AVAILABILITY ====================

    fun setAvailabilityTab(tab: String) {
        _uiState.update { it.copy(availabilityTab = tab) }
    }

    /** [day] is a DayOfWeek name; toggling writes the whole week back to the server. */
    fun toggleActiveDay(day: String) {
        val state = _uiState.value
        val days = if (state.activeDays.contains(day)) state.activeDays - day else state.activeDays + day
        _uiState.update { it.copy(activeDays = days) }

        viewModelScope.launch {
            // Keep each day's existing hours; new days get the standard 09:00-17:00 window.
            val existing = state.weeklyAvailability.associateBy { it.day.removeSuffix("s").uppercase(Locale.ENGLISH) }
            val availability = days.mapNotNull { name ->
                runCatching { DayOfWeek.valueOf(name) }.getOrNull()?.let { dayOfWeek ->
                    val hours = existing[name]?.timeRange?.split("–")?.map { it.trim() }
                    AvailabilityDto(
                        day = dayOfWeek.name,
                        startTime = hours?.getOrNull(0) ?: "09:00",
                        endTime = hours?.getOrNull(1) ?: "17:00"
                    )
                }
            }
            repository.saveAvailability(availability)
                .onSuccess { user ->
                    val windows = user.availability.mapNotNull { it.toWindowUi() }
                    _uiState.update {
                        it.copy(weeklyAvailability = windows.toDayWindows(), activeDays = windows.map { w -> w.day.name }.toSet())
                    }
                }
                .onFailure {
                    // Put the toggle back: the server did not take the change.
                    _uiState.update { it.copy(activeDays = state.activeDays) }
                    reportError(it)
                }
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
        val user = Firebase.auth.currentUser
        if (user == null) {
            _uiState.update { it.copy(authErrorMessage = "No signed-in user found - Please log in again") }
            return
        }
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            try {
                // Schedule the TutorBridge record for deletion first (POPIA
                // grace period) - it needs the Firebase token that deleting
                // the account is about to invalidate.
                repository.scheduleAccountDeletion()
                user.delete().await()
                AppPreferences(getApplication()).clear()
                _uiState.update { TutorBridgeUiState() }
                onComplete()
            } catch (exception: FirebaseAuthRecentLoginRequiredException) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = "For your security, please sign out and log back in before deleting your account") }
            } catch (exception: Exception) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = exception.message ?: "Could not delete account") }
            }
        }
    }

    // ==================== THEME & PREFERENCES ====================

    fun toggleDarkTheme(isDark: Boolean) {
        _uiState.update { it.copy(isDarkTheme = isDark) }
        saveSettings(UpdateSettingsRequest(theme = if (isDark) "DARK" else "LIGHT"))
    }

    fun toggleOfflineMode(offline: Boolean) {
        _uiState.update { it.copy(isOffline = offline) }
    }

    fun setLanguage(language: String) {
        _uiState.update { it.copy(selectedLanguage = language) }
        saveSettings(
            UpdateSettingsRequest(
                language = when (language) {
                    "isiZulu" -> "ZU"
                    "Afrikaans" -> "AF"
                    else -> "EN"
                }
            )
        )
    }

    fun toggleBookingUpdates(enabled: Boolean) {
        AppPreferences(getApplication()).enableBookingUpdatesSaved(enabled)
        _uiState.update { it.copy(bookingUpdatesEnabled = enabled) }
        saveNotificationPreferences()
    }

    fun toggleSessionReminders(enabled: Boolean) {
        AppPreferences(getApplication()).enableSessionRemindersSaved(enabled)
        _uiState.update { it.copy(sessionRemindersEnabled = enabled) }
        saveNotificationPreferences()
    }

    fun toggleSmsFallback(enabled: Boolean) {
        AppPreferences(getApplication()).enableSMSFallbackSaved(enabled)
        _uiState.update { it.copy(smsFallbackEnabled = enabled) }
        saveSettings(UpdateSettingsRequest(smsFallback = enabled))
    }

    fun toggleBiometric(enabled: Boolean) {
        AppPreferences(getApplication()).enableBiometricSaved(enabled)
        _uiState.update { it.copy(biometricEnabled = enabled) }
        saveSettings(UpdateSettingsRequest(biometricEntry = enabled))
    }

    private fun saveNotificationPreferences() {
        val state = _uiState.value
        saveSettings(
            UpdateSettingsRequest(
                notifications = NotificationPreferencesDto(
                    booking = state.bookingUpdatesEnabled,
                    message = state.sessionRemindersEnabled
                )
            )
        )
    }

    private fun saveSettings(body: UpdateSettingsRequest) {
        viewModelScope.launch {
            repository.saveSettings(body).onFailure { reportError(it) }
        }
    }

    // ==================== HELPERS ====================

    /** "2026-10-16" + "10:30 AM" -> a zoned start time, or null if either is missing. */
    private fun startOfSelectedSlot(state: TutorBridgeUiState) = runCatching {
        LocalDate.parse(state.selectedBookingDate)
            .atTime(LocalTime.parse(state.selectedSlot, DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)))
            .atZone(ZoneId.systemDefault())
    }.getOrNull()
}
