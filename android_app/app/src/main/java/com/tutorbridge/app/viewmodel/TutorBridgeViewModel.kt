package com.tutorbridge.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tutorbridge.app.Retrofit.RetrofitInstance
import com.tutorbridge.app.data.model.*
import com.tutorbridge.app.data.repository.ApiException
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.tutorbridge.app.R
import com.tutorbridge.app.push.PushEvents
import com.tutorbridge.app.Retrofit.SessionEvents
import com.tutorbridge.app.TutorBridgeApp
import com.tutorbridge.app.data.local.LocalCache
import com.tutorbridge.app.data.local.MessagePayload
import com.tutorbridge.app.data.local.NotesPayload
import com.tutorbridge.app.data.local.OutboxState
import com.tutorbridge.app.data.local.OutboxType
import com.tutorbridge.app.data.local.RequestPayload
import com.tutorbridge.app.data.local.SettingsPayload
import com.tutorbridge.app.data.local.pendingNotesByBooking
import com.tutorbridge.app.data.local.pendingSettings
import com.tutorbridge.app.data.local.withPending
import com.tutorbridge.app.data.repository.notesOf
import com.tutorbridge.app.data.repository.slotTime
import com.tutorbridge.app.sync.SyncScheduler
import com.tutorbridge.app.network.NetworkMonitor
import java.io.IOException
import com.tutorbridge.app.network.OfflineMode
import com.tutorbridge.app.data.repository.AuthRepository
import com.tutorbridge.app.data.repository.AuthResult
import com.tutorbridge.app.data.repository.TutorBridgeRepository
import com.tutorbridge.app.data.repository.toCalendarEvent
import com.tutorbridge.app.data.repository.toDayWindows
import com.tutorbridge.app.data.repository.toLearningHistory
import com.tutorbridge.app.data.repository.toPendingBookingUi
import com.tutorbridge.app.data.repository.toWindowUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.tasks.await
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant
import java.util.UUID

// References:
// Android Developers. 2026. Guide to app architecture. [Online].
//   Available at: https://developer.android.com/topic/architecture [Accessed 21 September 2026].
// Android Developers. 2026. ViewModel overview. [Online].
//   Available at: https://developer.android.com/topic/libraries/architecture/viewmodel [Accessed 21 September 2026].
// Android Developers. 2026. Kotlin coroutines on Android. [Online].
//   Available at: https://developer.android.com/kotlin/coroutines [Accessed 21 September 2026].
class TutorBridgeViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(RetrofitInstance.authApi)
    // What the phone remembers of the API between sessions, per signed-in user.
    private val localCache = LocalCache(
        dao = (application as TutorBridgeApp).database.cachedResources(),
        userId = { Firebase.auth.currentUser?.uid }
    )
    private val repository = TutorBridgeRepository(RetrofitInstance.tutorBridgeApi, localCache)
    private val networkMonitor = NetworkMonitor(application)

    /** Follows when the cache was last refreshed, for the "data saved at" line on the offline banner. */
    private var cacheClockJob: Job? = null

    // What the user did offline and has not been sent yet; see sendOrQueue().
    private val outbox = (application as TutorBridgeApp).outbox

    /** Follows the outbox, so screens show queued work and the app notices when it has been delivered. */
    private var outboxJob: Job? = null

    // Reference: Android Developers. 2026. StateFlow and SharedFlow. [Online].
    //   Available at: https://developer.android.com/kotlin/flow/stateflow-and-sharedflow [Accessed 21 September 2026].
    private val _uiState = MutableStateFlow(TutorBridgeUiState())
    val uiState: StateFlow<TutorBridgeUiState> = _uiState.asStateFlow()

    /** The booking the student just requested, so "check for a reply" knows what to look at. */
    private var lastBookingId: String? = null

    /** Set while the student is scheduling a match they just accepted; null
     * means the booking flow is the ordinary "book from a profile" one.
     * Cleared by selectTutor(), so picking a tutor from the directory always
     * starts a fresh direct booking. */
    private var acceptedApplicationId: String? = null

    /** The pages the back gesture returns through, this one last. See nextRouteHistory. */
    private var routeHistory = emptyList<String>()

    init {
        // Watched here because many actions set currentRoute directly, not through navigateTo.
        viewModelScope.launch {
            _uiState.map { it.currentRoute }.distinctUntilChanged().collect { routeHistory = nextRouteHistory(routeHistory, it) }
        }

        val preferences = AppPreferences(getApplication())
        val enabledBiometric = preferences.getEnabledBiometric()
        val enabledMessageNotifications = preferences.getEnabledMessageNotifications()
        val enabledBookingUpdates = preferences.getEnabledBookingUpdates()

        if (Firebase.auth.currentUser != null && enabledBiometric) {
            val lastRole = AppPreferences(getApplication()).getLastRole() ?: UserRole.STUDENT
            _uiState.update { it.copy(role = lastRole, biometricEnabled = true,
                messageNotificationsEnabled = enabledMessageNotifications, bookingUpdatesEnabled = enabledBookingUpdates, currentRoute = "biometric_lock") }
        } else {
            // Someone signed in with no lock on goes straight back in; see the init at the end of the class.
            val role = if (Firebase.auth.currentUser != null) preferences.getLastRole() ?: UserRole.STUDENT else null
            _uiState.update { it.copy(role = role, biometricEnabled = enabledBiometric,
                messageNotificationsEnabled = enabledMessageNotifications, bookingUpdatesEnabled = enabledBookingUpdates) }
        }
        // The picker should open on the language the app is really in, not on English.
        effectiveLanguage().let { language ->
            _uiState.update { it.copy(selectedLanguage = language.displayName) }
            Firebase.auth.setLanguageCode(language.tag)
        }
        loadInstitutions()
        // A free host may be asleep; this starts it waking before the first real call.
        viewModelScope.launch { authRepository.wakeServer() }
        viewModelScope.launch { SessionEvents.expired.collect { onSessionExpired() } }
        viewModelScope.launch { PushEvents.arrived.collect { loadThreads() } }

        // The phone's connection, for the offline banner and for refreshing when it comes back.
        viewModelScope.launch { networkMonitor.isOnline.collect { onNetworkChanged(it) } }
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
        _uiState.update { it.copy(authMode = mode, authErrorMessage = null, authInfoMessage = null) }
    }

    // Reference: Firebase. 2026. Authenticate with Firebase using password-based accounts on Android. [Online].
    //   Available at: https://firebase.google.com/docs/auth/android/password-auth [Accessed 21 September 2026].
    fun signUp(fullName: String, email: String, password: String) {
        val institutionId = _uiState.value.selectedSignupInstitutionId
        if (institutionId == null) {
            _uiState.update { it.copy(authErrorMessage = text(R.string.vm_select_institution)) }
            return
        }
        // Self sign-up is always a student/guardian; the backend assigns the same role.
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null, role = UserRole.STUDENT) }
        viewModelScope.launch {
            try {
                Firebase.auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val profileUpdate = UserProfileChangeRequest.Builder().setDisplayName(fullName).build()
                Firebase.auth.currentUser?.updateProfile(profileUpdate)?.await()
                // The sign-up button stays disabled until the age box is ticked.
                finishSignIn(institutionId, fullName, ageConfirmed = true)
            } catch (exception: Exception) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = text(authErrorRes(exception))) }
            }
        }
    }

    fun signIn(email: String, password: String) {
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            try {
                Firebase.auth.signInWithEmailAndPassword(email.trim(), password).await()
                finishSignIn(institutionId = null, fullName = null)
            } catch (exception: Exception) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = text(authErrorRes(exception))) }
            }
        }
    }

    // Reference: Firebase. 2026. Authenticate with Google on Android. [Online].
    //   Available at: https://firebase.google.com/docs/auth/android/google-signin [Accessed 21 September 2026].
    /**
     * [idToken] is the raw Google ID token from Credential Manager
     * (GoogleAuthentication.idTokenRequest), exchanged for a Firebase
     * session. Firebase is the session of record for TutorBridge.
     */
    fun signInWithGoogle(idToken: String, ageConfirmed: Boolean) {
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                Firebase.auth.signInWithCredential(credential).await()
                finishSignIn(_uiState.value.selectedSignupInstitutionId, fullName = null, ageConfirmed = ageConfirmed)
            } catch (exception: Exception) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = text(authErrorRes(exception))) }
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
    private suspend fun finishSignIn(institutionId: String?, fullName: String?, ageConfirmed: Boolean? = null) {
        when (val result = authRepository.bootstrapProfile(institutionId, fullName, ageConfirmed)) {
            is AuthResult.Success -> {
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        role = roleFromBackend(result.profile.role),
                        userName = result.profile.fullName
                    )
                }
                // A tutor an admin provisioned signs in with a temporary
                // password, and goes no further until it is replaced.
                if (result.profile.mustChangePassword) {
                    _uiState.update { it.copy(currentRoute = "new_password") }
                } else {
                    completeAuth()
                }
            }
            is AuthResult.Failure -> {
                Firebase.auth.signOut()
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = result.message ?: text(R.string.error_unreachable)) }
            }
        }
    }

    /**
     * Replaces the temporary password on the new-password screen. The server
     * sets it in Firebase, and a password change ends every session on the
     * account - this one included - so sign straight back in with the new one.
     */
    fun setNewPassword(newPassword: String) {
        val email = Firebase.auth.currentUser?.email
        if (email == null) {
            signOut()
            return
        }
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            authRepository.changePassword(newPassword)
                .onSuccess {
                    try {
                        Firebase.auth.signInWithEmailAndPassword(email, newPassword).await()
                        _uiState.update { it.copy(isAuthLoading = false) }
                        completeAuth()
                    } catch (exception: Exception) {
                        // The password did change; only the fresh session failed.
                        Firebase.auth.signOut()
                        _uiState.update {
                            it.copy(
                                isAuthLoading = false,
                                currentRoute = "auth",
                                authMode = AuthMode.SIGN_IN,
                                authErrorMessage = text(R.string.vm_password_changed)
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = friendlyMessage(error)) }
                }
        }
    }

    private fun roleFromBackend(backendRole: String): UserRole =
        if (backendRole == "TUTOR") UserRole.TUTOR else UserRole.STUDENT

    /** Emails the signed-in user a Firebase link to set a new password. */
    fun sendPasswordReset(onResult: (String) -> Unit) {
        val email = Firebase.auth.currentUser?.email
        if (email == null) {
            onResult(text(R.string.vm_no_signed_in_user))
            return
        }
        viewModelScope.launch {
            try {
                Firebase.auth.sendPasswordResetEmail(email).await()
                onResult(getApplication<Application>().getString(R.string.auth_password_reset_sent_to, email))
            } catch (exception: Exception) {
                onResult(text(R.string.auth_password_reset_failed))
            }
        }
    }

    // Reference: Firebase. 2026. Authenticate with Firebase using password-based accounts on Android. [Online].
    //   Available at: https://firebase.google.com/docs/auth/android/password-auth [Accessed 21 September 2026].
    /**
     * The sign-in screen's "Forgot password?": emails a reset link to whatever
     * address was typed, signed in or not. The answer is the same whether or not
     * the address has an account, so the screen cannot be used to find out
     * who is registered.
     */
    fun sendPasswordResetTo(email: String) {
        val address = email.trim()
        if (address.isEmpty()) {
            _uiState.update { it.copy(authInfoMessage = null, authErrorMessage = text(R.string.vm_enter_email_first)) }
            return
        }
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null, authInfoMessage = null) }
        viewModelScope.launch {
            val sent = getApplication<Application>().getString(R.string.auth_password_reset_if_exists, address)
            try {
                Firebase.auth.sendPasswordResetEmail(address).await()
                _uiState.update { it.copy(isAuthLoading = false, authInfoMessage = sent) }
            } catch (exception: FirebaseAuthInvalidUserException) {
                _uiState.update { it.copy(isAuthLoading = false, authInfoMessage = sent) }
            } catch (exception: FirebaseAuthInvalidCredentialsException) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = text(R.string.vm_invalid_email)) }
            } catch (exception: Exception) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = text(R.string.auth_password_reset_failed)) }
            }
        }
    }

    fun setAuthError(message: String) {
        _uiState.update { it.copy(authErrorMessage = message) }
    }

    fun completeAuth() {
        val role = _uiState.value.role ?: UserRole.STUDENT
        val user = Firebase.auth.currentUser
        val preferences = AppPreferences(getApplication())
        preferences.saveLastRole(role)
        val offerBiometric = !preferences.getEnabledBiometric() && !preferences.getBiometricOffered()
        _uiState.update { state ->
            val nextDestination = if (state.role == UserRole.TUTOR) "tutor_home" else "student_home"
            state.copy(role = role, currentRoute = nextDestination, userName = user?.displayName ?:
                user?.email?.substringBefore("@") ?: state.userName,
                userEmail = user?.email ?: state.userEmail,
                showBiometricOffer = offerBiometric)
        }
        watchCacheClock()
        watchOutbox()
        refreshAll()
        requestSync()        // anything queued before the last sign-out goes now
        registerPushToken()
        // After the route is set to home, so a notification tapped on a cold
        // start opens its own screen rather than being overwritten by home.
        consumePendingDeepLink()
    }

    // ==================== OFFLINE QUEUE ====================

    /** How a write ended: the server has it, it is queued for later, or the server said no. */
    private sealed class Delivery {
        object Sent : Delivery()
        object Queued : Delivery()
        data class Refused(val error: Throwable) : Delivery()
    }

    /**
     * Does what the user asked now if it can, and queues it if it cannot.
     *
     * Offline, or with the connection dropping mid-request, the work goes in the
     * outbox and is sent when the connection is back, so nothing the user did is
     * lost. A server that answers with a refusal is different: that is the real
     * answer, not a connectivity problem, and it is passed back to be shown.
     *
     * While anything is already queued, new work queues behind it rather than
     * going straight out. Sending it ahead would reorder what the user did - a
     * newer save of the same notes arriving, then the older queued one replacing it.
     */
    private suspend fun <P : Any> sendOrQueue(
        type: OutboxType,
        payload: P,
        dedupeKey: String? = null,
        /** Say "saved offline" in a snackbar when queued. A chat message does not: its own bubble says so, and one per message would be noise. */
        announce: Boolean = true,
        send: suspend () -> Result<*>
    ): Delivery {
        val state = _uiState.value
        if (!state.isOnline) {
            Log.i(OUTBOX, "$type: offline, queueing")
            return queueForLater(type, payload, dedupeKey, announce)
        }
        if (state.pendingSyncCount > 0) {
            Log.i(OUTBOX, "$type: ${state.pendingSyncCount} already queued, queueing behind them to keep order")
            return queueForLater(type, payload, dedupeKey, announce)
        }

        val result = send()
        if (result.isSuccess) {
            Log.i(OUTBOX, "$type: sent now")
            return Delivery.Sent
        }
        val error = result.exceptionOrNull() ?: return Delivery.Sent
        return if (error is IOException) {
            Log.i(OUTBOX, "$type: connection dropped (${error.message}), queueing")
            queueForLater(type, payload, dedupeKey, announce)
        } else {
            Log.w(OUTBOX, "$type: refused by the server: ${error.message}")
            Delivery.Refused(error)
        }
    }

    private suspend fun <P : Any> queueForLater(type: OutboxType, payload: P, dedupeKey: String?, announce: Boolean): Delivery {
        if (outbox.enqueue(type, payload, dedupeKey) == null) {
            return Delivery.Refused(IllegalStateException(text(R.string.vm_sign_in_to_save)))
        }
        if (announce) _uiState.update { it.copy(noticeMessage = text(R.string.saved_offline)) }
        requestSync()
        return Delivery.Queued
    }

    /** Asks the background worker to send the outbox. Held back while offline mode is forced - it would only be refused. */
    private fun requestSync() {
        if (_uiState.value.isOnline) SyncScheduler.request(getApplication())
    }

    /** "Sync now": ask for the waiting items to be sent. Items the server refused stay as they are - see [retryFailedSync]. */
    fun syncNow() {
        if (_uiState.value.isOnline) {
            requestSync()
            refreshAll()     // also what moves "Last synced" on
        } else _uiState.update { it.copy(dataErrorMessage = text(R.string.error_offline)) }
    }

    /** Another go at everything the server refused: they go back in the queue and a sync is requested. */
    fun retryFailedSync() {
        viewModelScope.launch {
            outbox.retryFailed()
            requestSync()
        }
    }

    /** The user gives up on one queued item; it is removed and never sent. */
    fun discardOutboxItem(id: Long) {
        viewModelScope.launch { outbox.discard(id) }
    }

    /** Needs a signed-in user: the outbox is per user. */
    private fun watchOutbox() {
        outboxJob?.cancel()
        outboxJob = viewModelScope.launch {
            var waiting = emptySet<Long>()
            outbox.observe().collect { items ->
                _uiState.update { it.copy(outboxItems = items) }

                // What was waiting and is now gone has been delivered.
                val ids = items.map { it.id }.toSet()
                val delivered = waiting.count { it !in ids }
                waiting = items.filter { it.state == OutboxState.PENDING }.map { it.id }.toSet()
                if (delivered > 0) onDelivered(delivered)
            }
        }
    }

    /** Queued work has reached the server: say so, and bring the screens up to date with it. */
    private fun onDelivered(count: Int) {
        Log.i("Sync", "$count queued change(s) delivered")
        val resources = getApplication<Application>().resources
        _uiState.update { it.copy(noticeMessage = resources.getQuantityString(R.plurals.synced_changes, count, count)) }
        refreshIfSignedIn()
        // The chat on screen would otherwise wait for its next poll to swap "Waiting to send" for the real message.
        if (_uiState.value.currentRoute == "chat") viewModelScope.launch { fetchMessages(page = 1, quiet = true) }
    }

    /** Needs a signed-in user: the cache is per user, and its clock is that user's. */
    private fun watchCacheClock() {
        cacheClockJob?.cancel()
        cacheClockJob = viewModelScope.launch {
            localCache.newestUpdate().collect { savedAt ->
                _uiState.update { it.copy(cacheSavedAtMillis = savedAt) }
            }
        }
    }

    private var pendingDeepLink: String? = null

    /** Called by MainActivity when a notification is tapped. */
    fun openDeepLink(link: String) {
        val insideApp = _uiState.value.currentRoute !in setOf("splash", "auth", "biometric_lock", "new_password")
        if (insideApp) routeForDeepLink(link, _uiState.value.role)?.let { navigateTo(it) }
        else pendingDeepLink = link   // cold start: replayed once the user is through sign-in / biometrics
    }

    private fun consumePendingDeepLink() {
        val link = pendingDeepLink ?: return
        pendingDeepLink = null
        routeForDeepLink(link, _uiState.value.role)?.let { navigateTo(it) }
    }

    /** Guards against the sign-out's own requests being refused and starting another one. */
    private var signingOut = false

    /** The server refused this user's token (see AuthInterceptor), so they have to sign in again. */
    private fun onSessionExpired() {
        if (signingOut || Firebase.auth.currentUser == null) return
        // On these screens a sign-in is in progress and reports its own failure.
        if (_uiState.value.currentRoute in setOf("splash", "auth")) return
        signOut(message = text(R.string.session_expired))
    }

    fun signOut(message: String? = null) {
        if (signingOut) return
        signingOut = true
        chatPoller?.cancel()
        cacheClockJob?.cancel()
        outboxJob?.cancel()
        viewModelScope.launch {
            // Timeout so a bad connection can't leave the user stuck on "Sign out".
            withTimeoutOrNull(3_000) { unregisterPushToken() }
            Firebase.auth.signOut()          // only after the DELETE
            GoogleAuthentication.clearCredentials(getApplication())
            localCache.clear()               // nothing of this user stays on the phone
            AppPreferences(getApplication()).clear()
            lastBookingId = null
            pendingDeepLink = null           // added in C5
            _uiState.update { state ->
                if (message == null) signedOutState()
                else signedOutState().copy(currentRoute = "auth", authErrorMessage = message)
            }
            signingOut = false
            loadInstitutions()
        }
    }

    fun navigateTo(requested: String) {
        val route = routeForRole(requested, _uiState.value.role)
        _uiState.update { it.copy(currentRoute = route) }
        if (route != "chat") chatPoller?.cancel()
        if (route != "calendar" && _uiState.value.checkInCode != null) dismissCheckInCode()
        // Screens that are worth having fresh the moment they are opened.
        when (route) {
            "student_home", "tutor_home" -> {
                loadBookings()
                loadThreads()
            }
            "messages" -> loadThreads()
            "calendar" -> loadBookings()
            "notifications" -> loadNotifications(markRead = true)
            "find_tutors" -> loadTutors()
            "my_requests", "tutor_requests" -> loadRequests()
            "claims_export" -> loadClaims()
        }
    }

    /** Returns to the previous page; false when there is none, so the back gesture would leave the app. */
    fun goBack(): Boolean {
        val previous = routeHistory.getOrNull(routeHistory.size - 2) ?: return false
        navigateTo(previous)
        return true
    }

    fun dismissDataError() {
        _uiState.update { it.copy(dataErrorMessage = null) }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(noticeMessage = null) }
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
                        userId = user._id,
                        role = roleFromBackend(user.role),
                        userName = user.fullName.ifBlank { state.userName },
                        userEmail = user.email.ifBlank { state.userEmail },
                        userStaffId = user.staffNumber.orEmpty(),
                        tutorHourlyRate = user.hourlyRate ?: 0.0,
                        tutorProfileCompleteness = user.profileCompleteness,
                        tutorLanguagesSpoken = Language.fromServerCode(user.locale).displayName,
                        tutorConsultationRoom = user.areaId?.name.orEmpty(),
                        weeklyAvailability = windows.toDayWindows(),
                        activeDays = windows.map { it.day.name }.toSet()
                    )
                }
                // The conversations are read from the caller's side, so they wait on the id.
                loadThreads()
            }.onFailure { reportLoadError(it) }
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            repository.me().onSuccess { profile ->
                _uiState.update { it.copy(currentProfile = profile) }
            }
        }
    }

    fun saveProfile(newEmail: String, bio: String, hourlyRate: Double?, department: String, onComplete: () -> Unit) {
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null, profileStatusMessage = null) }
        viewModelScope.launch {
            val profileResult = repository.updateProfile(UpdateProfileRequest(bio = bio, hourlyRate = hourlyRate, department = department))
            if (profileResult.isFailure) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = friendlyMessage(profileResult.exceptionOrNull()!!)) }
                return@launch
            }

            val user = Firebase.auth.currentUser
            val emailChanged = user != null && newEmail.isNotBlank() && newEmail != user.email

            try {
                if (emailChanged) {
                    user!!.verifyBeforeUpdateEmail(newEmail).await()
                }
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        currentProfile = profileResult.getOrNull(),
                        profileStatusMessage = text(if (emailChanged) R.string.vm_profile_saved_confirm_email else R.string.vm_profile_saved)
                    )
                }
                onComplete()
            } catch (exception: FirebaseAuthRecentLoginRequiredException) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = text(R.string.vm_reauth_change_email)) }
            } catch (exception: Exception) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = text(R.string.vm_email_update_failed)) }
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            // What the server (or the cache) says, with changes made offline and not yet sent on top -
            // otherwise a reload would flip a switch back that the user just turned.
            repository.settings().map { it.withPending(_uiState.value.outboxItems.pendingSettings()) }.onSuccess { settings ->
                // The account's language and this phone's may differ (a new phone, a choice made offline).
                val plan = reconcileLanguage(Language.fromServerCode(settings.language), languageChosenOnThisPhone())
                plan.applyOnThisPhone?.let { applyLanguage(it) }
                plan.saveToAccount?.let { saveSettings(UpdateSettingsRequest(language = it.serverCode)) }

                _uiState.update {
                    it.copy(
                        selectedLanguage = (plan.applyOnThisPhone ?: effectiveLanguage()).displayName,
                        isDarkTheme = settings.theme == "DARK",
                        defaultAreaId = settings.defaultAreaId,
                        bookingUpdatesEnabled = settings.notifications.booking,
                        messageNotificationsEnabled = settings.notifications.message
                    )
                }
            }.onFailure { reportLoadError(it) }
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
            }.onFailure { reportLoadError(it) }
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
            }.onFailure { reportLoadError(it) }
        }
    }

    private fun loadRequests() {
        viewModelScope.launch {
            if (_uiState.value.role == UserRole.TUTOR) {
                repository.openRequests().onSuccess { requests ->
                    _uiState.update { it.copy(openRequests = requests) }
                }.onFailure { reportLoadError(it) }
                repository.myApplications().onSuccess { applications ->
                    _uiState.update { it.copy(myApplications = applications) }
                }.onFailure { reportLoadError(it) }
            } else {
                repository.myRequests().onSuccess { requests ->
                    _uiState.update { it.copy(myRequests = requests) }
                }.onFailure { reportLoadError(it) }
            }
        }
    }

    private fun loadBookings() {
        viewModelScope.launch {
            val isTutor = _uiState.value.role == UserRole.TUTOR
            repository.bookings().onSuccess { bookings ->
                _uiState.update { state ->
                    // Notes saved offline and not yet sent win over what the server still has.
                    val unsentNotes = state.outboxItems.pendingNotesByBooking()
                    state.copy(
                        calendarEvents = bookings
                            .filter { it.status != "CANCELLED" }
                            .mapNotNull { it.toCalendarEvent(isTutor) }
                            .map { event ->
                                val notes = unsentNotes[event.id] ?: return@map event
                                event.copy(
                                    checklist = notes.items.mapIndexed { index, item ->
                                        SessionGoalUi(id = "n$index", text = item.text, completed = item.done)
                                    },
                                    sideNotes = notes.text
                                )
                            },
                        pendingBookings = if (isTutor) {
                            bookings.filter { it.status == "REQUESTED" }.mapNotNull { it.toPendingBookingUi() }
                        } else {
                            emptyList()
                        },
                        // ponytail: only the first page of GET /bookings (20, newest created) arrives,
                        // so older sessions drop off; fetch every page if a student outgrows it.
                        learningHistory = if (isTutor) emptyList() else bookings.toLearningHistory(),
                        lessonsTaught = if (isTutor) 0 else bookings.count { it.status == "COMPLETED" }
                    )
                }
            }.onFailure { reportLoadError(it) }
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
            }.onFailure { reportLoadError(it) }
        }
    }

    private fun loadClaims() {
        if (_uiState.value.role != UserRole.TUTOR) return
        viewModelScope.launch {
            val institutionName = repository.institution().getOrNull()?.name ?: _uiState.value.institutionName
            repository.claims(institutionName, _uiState.value.claimMonthsLoaded).onSuccess { institutions ->
                _uiState.update { state ->
                    val periods = institutions.firstOrNull()?.claimPeriods.orEmpty()
                    state.copy(
                        institutions = institutions,
                        selectedInstitutionId = institutions.firstOrNull()?.id ?: state.selectedInstitutionId,
                        // Keep the month being looked at when more history arrives.
                        selectedClaimPeriodId = periods.firstOrNull { it.id == state.selectedClaimPeriodId }?.id
                            ?: periods.firstOrNull()?.id
                            ?: state.selectedClaimPeriodId
                    )
                }
            }.onFailure { reportLoadError(it) }
        }
    }

    /** "Load earlier months": widens the window by [CLAIM_MONTHS_STEP], up to what the API allows. */
    fun loadEarlierClaims() {
        _uiState.update {
            it.copy(claimMonthsLoaded = (it.claimMonthsLoaded + CLAIM_MONTHS_STEP).coerceAtMost(CLAIM_MONTHS_MAX))
        }
        loadClaims()
    }

    /**
     * Fetches one month's CSV from the server and hands it to [onResult]; the
     * screen owns the file and the share sheet, so no Context is needed here.
     */
    fun fetchClaimCsv(month: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            repository.claimCsv(month)
                .onFailure { reportError(it) }
                .let(onResult)
        }
    }

    private fun text(id: Int): String = getApplication<Application>().getString(id)

    private fun reportError(error: Throwable) {
        _uiState.update { it.copy(dataErrorMessage = friendlyMessage(error)) }
    }

    /**
     * For loading data in the background, as opposed to something the user just
     * did. With no connection the offline banner has already said so, and a
     * dozen loaders each adding a snackbar would only bury the screen - so a
     * dropped connection is logged, not shown. Anything else still is.
     */
    private fun reportLoadError(error: Throwable) {
        if (error is IOException && !_uiState.value.isOnline) {
            Log.d(NETWORK, "Offline, not showing: ${error.message}")
            return
        }
        reportError(error)
    }

    /** A dropped connection says "Unable to resolve host ..." to a developer; say what it means to a user. */
    private fun friendlyMessage(error: Throwable): String = when {
        error is IOException && !_uiState.value.isOnline -> text(R.string.error_offline)
        error is IOException -> text(R.string.error_unreachable)
        error is ApiException && error.code >= 500 -> text(R.string.error_server)
        error is ApiException && errorMessageRes(error.errorCode) != null -> text(errorMessageRes(error.errorCode)!!)
        // A series error names the weeks it is about, written in the phone's language.
        error is ApiException && seriesErrorMessageRes(error.errorCode) != null -> getApplication<Application>().getString(
            seriesErrorMessageRes(error.errorCode)!!,
            formatSeriesDates(error.dates, ZoneId.systemDefault(), Locale.getDefault())
        )
        else -> error.message ?: text(R.string.error_unreachable)
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
        acceptedApplicationId = null
        _uiState.update {
            it.copy(
                selectedTutor = tutor,
                selectedLocation = tutor.consultationVenue.ifBlank { it.selectedLocation },
                selectedSlot = "",
                // A new booking starts as a single session, and from the tutor's profile.
                repeatWeekly = false,
                repeatCount = SERIES_DEFAULT,
                repeatDays = emptySet(),
                bookingFromApplication = false
            )
        }
    }

    /** Book the same time every week instead of one session. Only offered for a direct booking. */
    fun setRepeatWeekly(enabled: Boolean) {
        _uiState.update { it.copy(repeatWeekly = enabled) }
    }

    /** How many weeks the booking repeats for, kept to what the server accepts for the days chosen. */
    fun setRepeatCount(count: Int) {
        _uiState.update { it.copy(repeatCount = weeksFor(count, it.repeatDays.size + 1)) }
    }

    /**
     * Adds or removes a weekday from the days booked. The weekday of the chosen date is always booked; with more days
     * the most weeks allowed shrinks (24 sessions at most), so the weeks are brought back into range.
     */
    fun toggleRepeatDay(day: DayOfWeek) {
        _uiState.update { state ->
            val first = state.firstDay ?: return@update state
            val days = toggledDays(state.repeatDays, day, first)
            state.copy(repeatDays = days, repeatCount = weeksFor(state.repeatCount, days.size + 1))
        }
    }

    fun selectSlot(slot: String) {
        _uiState.update { state ->
            // Extra days are only kept where the tutor still works at the new time.
            val open = availableDays(state.selectedTutor?.windows.orEmpty(), slot)
            val days = state.repeatDays.filter { it in open }.toSet()
            state.copy(
                selectedSlot = slot,
                repeatDays = days,
                repeatCount = weeksFor(state.repeatCount, days.size + 1)
            )
        }
    }

    fun selectLocation(location: String) {
        _uiState.update { it.copy(selectedLocation = location) }
    }

    fun selectBookingDate(date: String) {
        _uiState.update { state ->
            // The new date's weekday is booked anyway, so it is no longer an extra day.
            val first = runCatching { LocalDate.parse(date).dayOfWeek }.getOrNull()
            val days = if (first != null) state.repeatDays - first else state.repeatDays
            state.copy(
                selectedBookingDate = date,
                selectedSlot = "",
                repeatDays = days,
                repeatCount = weeksFor(state.repeatCount, days.size + 1)
            )
        }
    }

    /**
     * Books the selected tutor for the selected date and slot (one hour).
     *
     * Two ways in, one submit: scheduling a match the student already accepted
     * books the agreed session outright - the server confirms it and writes it
     * to Google Calendar - while booking from a tutor's profile still has to
     * wait on that tutor's reply.
     */
    fun sendBookingRequest() {
        val state = _uiState.value
        val tutor = state.selectedTutor
        val start = startOfSelectedSlot(state)
        if (tutor == null || start == null) {
            _uiState.update { it.copy(dataErrorMessage = text(R.string.vm_pick_date_slot)) }
            return
        }

        val applicationId = acceptedApplicationId
        _uiState.update { it.copy(isRefreshing = true, dataErrorMessage = null) }
        viewModelScope.launch {
            val startIso = start.toInstant().toString()
            val endIso = start.plusHours(1).toInstant().toString()
            val booked = if (applicationId != null) {
                repository.scheduleFromApplication(
                    ApplicationBookingRequest(
                        applicationId = applicationId,
                        startTime = startIso,
                        endTime = endIso,
                        location = state.selectedLocation.ifBlank { null }
                    )
                )
            } else {
                repository.bookTutorDirectly(
                    DirectBookingRequest(
                        tutorId = tutor.id,
                        subjectId = null, // the tutor's own subject is used server-side
                        startTime = startIso,
                        endTime = endIso,
                        mode = "IN_PERSON",
                        location = state.selectedLocation.ifBlank { null },
                        repeat = repeatFor(state.repeatWeekly, state.repeatCount, start.dayOfWeek, state.repeatDays)
                    )
                )
            }

            booked
                .onSuccess { ack ->
                    lastBookingId = ack.id
                    acceptedApplicationId = null
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            // How many sessions were just asked for, for the "sent" screen; 0 for a single one.
                            lastSeriesCount = ack.seriesCount ?: 0,
                            repeatWeekly = false,
                            repeatDays = emptySet(),
                            bookingFromApplication = false,
                            currentRoute = if (applicationId != null) "booking_confirmed" else "booking_sent"
                        )
                    }
                    loadBookings()
                }
                .onFailure {
                    _uiState.update { state2 ->
                        state2.copy(isRefreshing = false, dataErrorMessage = friendlyMessage(it))
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
                            text(R.string.vm_still_waiting_tutor)
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

    /**
     * Cancels this session and every later one of its weekly series. Either person may; the other is told once.
     * Sessions already held, and ones before this, are left alone.
     */
    fun cancelSeriesFrom(event: CalendarEventUi) {
        val seriesId = event.seriesId ?: return
        viewModelScope.launch {
            repository.cancelSeries(seriesId, reason = null, fromBookingId = event.id)
                .onSuccess { ack ->
                    val cancelled = ack.cancelled ?: 0
                    _uiState.update {
                        it.copy(
                            noticeMessage = getApplication<Application>().resources
                                .getQuantityString(R.plurals.calendar_series_cancelled, cancelled, cancelled)
                        )
                    }
                    loadBookings()
                }
                .onFailure { reportError(it) }
        }
    }

    /** The tutor accepts a whole weekly series at once; a clash with one of their sessions is reported by week. */
    fun acceptTutorSeries(seriesId: String) {
        viewModelScope.launch {
            repository.confirmSeries(seriesId)
                .onSuccess {
                    _uiState.update { it.copy(currentRoute = "tutor_home") }
                    loadBookings()
                }
                .onFailure { reportError(it) }
        }
    }

    /** The tutor turns down every upcoming session of a weekly series. */
    fun declineTutorSeries(seriesId: String) {
        viewModelScope.launch {
            // No reason is sent: a typed-in English sentence would sit untranslated inside the student's
            // notification, whereas without one the server's own text is shown in the student's language.
            repository.cancelSeries(seriesId, reason = null)
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

    /** The "New" button: a fresh wizard, even if an edit was abandoned half way, with only the default area filled in. */
    fun startNewRequest() {
        _uiState.update { it.copy(requestDraft = newRequestDraft(it.defaultAreaId, it.areaOptions), currentRoute = "new_request") }
    }

    /** Leaves the wizard, dropping whatever was typed - new request or edit alike. */
    fun discardRequestDraft() {
        _uiState.update { it.copy(requestDraft = RequestDraft(), currentRoute = "my_requests") }
    }

    /** Opens the wizard on an existing open request, prefilled from it. */
    fun startEditingRequest(request: StudentRequestUi) {
        val slots = request.availability
        _uiState.update {
            it.copy(
                requestDraft = RequestDraft(
                    subject = request.subject,
                    subjectId = request.subjectId,
                    level = request.level,
                    preferredVenue = request.preferredVenue,
                    areaId = request.areaId,
                    days = slots.mapNotNull { slot -> runCatching { DayOfWeek.valueOf(slot.day) }.getOrNull() }.toSet(),
                    startTime = slots.firstOrNull()?.startTime ?: "14:00",
                    endTime = slots.firstOrNull()?.endTime ?: "17:00",
                    notes = request.description,
                    editingRequestId = request.id
                ),
                currentRoute = "new_request"
            )
        }
    }

    fun submitTuitionRequest() {
        val draft = _uiState.value.requestDraft
        if (draft.subjectId.isBlank() || draft.areaId.isBlank()) {
            _uiState.update { it.copy(dataErrorMessage = text(R.string.vm_choose_subject_area)) }
            return
        }

        _uiState.update { it.copy(isRefreshing = true, dataErrorMessage = null) }
        viewModelScope.launch {
            val availability = draft.days.map { AvailabilityDto(it.name, draft.startTime, draft.endTime) }
            val editing = draft.editingRequestId

            val failure: Throwable? = if (editing != null) {
                // Editing needs the server to say whether the request is still open, so it is online-only.
                repository.updateRequest(
                    editing,
                    UpdateTuitionRequest(
                        subjectId = draft.subjectId,
                        areaId = draft.areaId,
                        gradeLevel = draft.level.ifBlank { null },
                        description = draft.notes,
                        availability = availability
                    )
                ).exceptionOrNull()
            } else {
                val request = CreateTuitionRequest(
                    subjectId = draft.subjectId,
                    areaId = draft.areaId,
                    gradeLevel = draft.level.ifBlank { null },
                    mode = "IN_PERSON", // the wizard is campus-based; no online option is offered
                    description = draft.notes,
                    availability = availability,
                    // Made now and sent on every try, so a retry or a replay of the offline
                    // queue is recognised by the server as this same post, not a new one.
                    clientRequestId = UUID.randomUUID().toString()
                )
                val payload = RequestPayload(request, subjectName = draft.subject, areaName = draft.preferredVenue)
                (sendOrQueue(OutboxType.CREATE_REQUEST, payload) { repository.createRequest(request) } as? Delivery.Refused)?.error
            }

            if (failure == null) {
                // Sent, or queued and showing on the board as "Waiting to sync" - either way the student is done.
                _uiState.update {
                    it.copy(isRefreshing = false, currentRoute = "my_requests", requestDraft = RequestDraft())
                }
                loadRequests()
            } else {
                _uiState.update { state ->
                    state.copy(
                        isRefreshing = false,
                        dataErrorMessage = if (failure is IOException) friendlyMessage(failure) else failure.message
                            ?: text(if (editing != null) R.string.vm_save_changes_failed else R.string.vm_post_request_failed)
                    )
                }
            }
        }
    }

    /** The student no longer needs the request - [withdraw] cancels it, otherwise it is closed as sorted. */
    fun endRequest(requestId: String, withdraw: Boolean) {
        viewModelScope.launch {
            val result = if (withdraw) repository.withdrawRequest(requestId) else repository.closeRequest(requestId)
            result
                .onSuccess {
                    _uiState.update { it.copy(selectedRequest = null, currentRoute = "my_requests") }
                    loadRequests()
                }
                .onFailure { reportError(it) }
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

    /**
     * The student accepts a tutor's offer. Matching is only half of it - the
     * session itself still has to be put in the diary, so this goes straight on
     * into the booking flow against the matched tutor's own hours.
     */
    fun acceptApplicant(applicant: TutorApplicant) {
        viewModelScope.launch {
            repository.decideApplication(applicant.id, accept = true)
                .onSuccess {
                    loadRequests()
                    val tutor = _uiState.value.tutors.firstOrNull { it.id == applicant.tutorId }
                    if (tutor == null) {
                        // Matched, but without the tutor's hours there are no slots to offer.
                        _uiState.update {
                            it.copy(
                                currentRoute = "my_requests",
                                dataErrorMessage = getApplication<Application>().getString(R.string.vm_matched_book_time, applicant.name)
                            )
                        }
                        return@onSuccess
                    }
                    selectTutor(tutor)                       // clears any earlier direct booking
                    acceptedApplicationId = applicant.id     // ...so this has to follow it
                    // An agreed session is one session: there is nothing to repeat.
                    _uiState.update { it.copy(bookingFromApplication = true) }
                    navigateTo("booking_flow")
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

    /** [day] is a DayOfWeek name; toggling writes the whole week back to the server. */
    fun toggleActiveDay(day: String) {
        val state = _uiState.value
        val days = if (state.activeDays.contains(day)) state.activeDays - day else state.activeDays + day
        _uiState.update { it.copy(activeDays = days) }
        saveWeeklyAvailability(state, days)
    }

    /** [day] is a DayOfWeek name; edits that day's hours and writes the whole week back. */
    fun updateDayHours(day: String, startTime: String, endTime: String) {
        val state = _uiState.value
        saveWeeklyAvailability(state, state.activeDays, overrideDay = day, overrideStart = startTime, overrideEnd = endTime)
    }

    /**
     * Builds the week's AvailabilityDto list from [days], keeping each day's
     * existing hours except [overrideDay] (new days default to 09:00-17:00),
     * and writes it to the server.
     */
    private fun saveWeeklyAvailability(
        state: TutorBridgeUiState,
        days: Set<String>,
        overrideDay: String? = null,
        overrideStart: String = "",
        overrideEnd: String = ""
    ) {
        viewModelScope.launch {
            val existing = state.weeklyAvailability.associateBy { it.dayName }
            val availability = days.mapNotNull { name ->
                runCatching { DayOfWeek.valueOf(name) }.getOrNull()?.let { dayOfWeek ->
                    if (name == overrideDay) {
                        AvailabilityDto(day = dayOfWeek.name, startTime = overrideStart, endTime = overrideEnd)
                    } else {
                        val hours = existing[name]?.timeRange?.split("–")?.map { it.trim() }
                        AvailabilityDto(
                            day = dayOfWeek.name,
                            startTime = hours?.getOrNull(0) ?: "09:00",
                            endTime = hours?.getOrNull(1) ?: "17:00"
                        )
                    }
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
                    // Put the days back: the server did not take the change.
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

    // ==================== SESSION PREP: THE TUTOR'S NOTES ====================

    /**
     * Open one session's notes. The checklist and side notes come down with
     * the booking list, so there is nothing to fetch - they are copied into
     * editable state here and written back by [saveSessionNotes].
     */
    fun openSessionNotes(session: CalendarEventUi) {
        _uiState.update {
            it.copy(
                selectedSessionId = session.id,
                sessionChecklist = session.checklist,
                sessionSideNotes = session.sideNotes,
                newChecklistItem = "",
                currentRoute = routeForRole("session_prep", it.role)
            )
        }
    }

    /** Ticking a box is a decision, not a draft, so it saves straight away. */
    fun toggleSessionGoal(goalId: String) {
        _uiState.update { state ->
            state.copy(
                sessionChecklist = state.sessionChecklist.map { goal ->
                    if (goal.id == goalId) goal.copy(completed = !goal.completed) else goal
                }
            )
        }
        saveSessionNotes()
    }

    fun setNewChecklistItem(text: String) {
        _uiState.update { it.copy(newChecklistItem = text) }
    }

    fun addChecklistItem() {
        val text = _uiState.value.newChecklistItem.trim()
        if (text.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                // Ids only have to be unique within the list the screen is
                // drawing; the server stores the rows in order, without ids.
                sessionChecklist = state.sessionChecklist + SessionGoalUi(
                    id = "n${state.sessionChecklist.size}-${System.currentTimeMillis()}",
                    text = text,
                    completed = false
                ),
                newChecklistItem = ""
            )
        }
        saveSessionNotes()
    }

    fun removeChecklistItem(goalId: String) {
        _uiState.update { state ->
            state.copy(sessionChecklist = state.sessionChecklist.filterNot { it.id == goalId })
        }
        saveSessionNotes()
    }

    /** Typing is a draft: it is written back when the tutor leaves the screen. */
    fun setSessionSideNotes(text: String) {
        _uiState.update { it.copy(sessionSideNotes = text) }
    }

    /** Write the open session's notes back, without waiting for the result. */
    fun saveSessionNotes() {
        val sessionId = _uiState.value.selectedSessionId ?: return
        viewModelScope.launch { writeSessionNotes(sessionId) }
    }

    /**
     * Write the notes back and return to the calendar. The write is awaited
     * before navigating, because opening the calendar refetches the bookings -
     * starting both at once would let the refresh answer with notes from
     * before the save.
     *
     * [requireSaved] is what separates Save from Back. Save is a deliberate
     * act, so a failed write keeps the tutor on the screen with their typing
     * intact to try again; Back has to go back either way, and lets the
     * snackbar carry the bad news.
     */
    fun closeSessionNotes(requireSaved: Boolean = false) {
        val sessionId = _uiState.value.selectedSessionId
        if (sessionId == null) {
            navigateTo("calendar")
            return
        }
        viewModelScope.launch {
            val saved = writeSessionNotes(sessionId)
            if (saved || !requireSaved) navigateTo("calendar")
        }
    }

    /**
     * Saves whatever is currently on screen. Also patches the booking already
     * in [TutorBridgeUiState.calendarEvents] so reopening the session shows
     * what was saved without waiting on a refresh.
     */
    private suspend fun writeSessionNotes(sessionId: String): Boolean {
        val state = _uiState.value
        val checklist = state.sessionChecklist
        val sideNotes = state.sessionSideNotes

        val notes = notesOf(checklist, sideNotes)
        // The whole document is one thing: with a key, a newer save replaces an unsent older one.
        val delivery = sendOrQueue(OutboxType.SAVE_NOTES, NotesPayload(sessionId, notes), dedupeKey = "notes:$sessionId") {
            repository.saveSessionNotes(sessionId, notes)
        }

        if (delivery is Delivery.Refused) {
            reportError(delivery.error)
            return false
        }
        // Sent or queued: the tutor's screen shows their notes either way.
        _uiState.update { current ->
            current.copy(
                calendarEvents = current.calendarEvents.map { event ->
                    if (event.id == sessionId) {
                        event.copy(checklist = checklist, sideNotes = sideNotes)
                    } else {
                        event
                    }
                }
            )
        }
        return true
    }

    // ==================== RESCHEDULING ====================

    /**
     * Moves a session to [date] at [time], keeping its length. The server decides
     * what that means: a tutor's move stands, a student's move of a confirmed
     * session goes back to the tutor to confirm - and the message says which.
     */
    fun rescheduleSession(event: CalendarEventUi, date: LocalDate, time: LocalTime) {
        val oldStart = runCatching { Instant.parse(event.startIso) }.getOrNull()
        val oldEnd = runCatching { Instant.parse(event.endIso) }.getOrNull()
        val length = if (oldStart != null && oldEnd != null) Duration.between(oldStart, oldEnd) else Duration.ofHours(1)
        val start = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant()
        val isTutor = _uiState.value.role == UserRole.TUTOR

        viewModelScope.launch {
            repository.rescheduleBooking(event.id, RescheduleRequest(start.toString(), start.plus(length).toString()))
                .onSuccess { ack ->
                    _uiState.update {
                        it.copy(
                            selectedCalendarDate = date,
                            noticeMessage = if (ack.status == "REQUESTED" && !isTutor) {
                                text(R.string.vm_reschedule_sent)
                            } else {
                                getApplication<Application>().getString(R.string.vm_session_moved, date.toString(), time)
                            }
                        )
                    }
                    loadBookings()
                }
                .onFailure { reportError(it) }
        }
    }

    // ==================== ATTENDANCE CHECK-IN ====================

    private var checkInPoller: Job? = null

    fun showCheckInCode(event: CalendarEventUi) {
        viewModelScope.launch {
            repository.checkInCode(event.id)
                .onSuccess { dto ->
                    val expires = runCatching { Instant.parse(dto.expiresAt) }
                        .getOrDefault(Instant.now().plusSeconds(300))
                    _uiState.update { it.copy(checkInCode = CheckInCodeUi(dto.bookingId, dto.code, dto.qrPayload, expires)) }
                    watchForCheckIn(event.id)
                }
                .onFailure { reportError(it) }
        }
    }

    private fun watchForCheckIn(bookingId: String) {
        checkInPoller?.cancel()
        checkInPoller = viewModelScope.launch {
            while (isActive && _uiState.value.checkInCode?.bookingId == bookingId) {
                delay(4_000)
                repository.bookings().onSuccess { bookings ->
                    val done = bookings.firstOrNull { it._id == bookingId }?.attendance?.checkedInAt != null
                    if (done) {
                        _uiState.update { it.copy(checkInCode = null, noticeMessage = text(R.string.checkin_student_checked_in)) }
                        loadBookings()
                    }
                }
            }
        }
    }

    fun dismissCheckInCode() {
        checkInPoller?.cancel()
        _uiState.update { it.copy(checkInCode = null) }
    }

    fun checkIn(event: CalendarEventUi, code: String, method: String) {
        if (_uiState.value.isCheckingIn) return
        _uiState.update { it.copy(isCheckingIn = true) }
        viewModelScope.launch {
            repository.checkIn(event.id, code.trim(), method)
                .onSuccess {
                    _uiState.update { it.copy(isCheckingIn = false, noticeMessage = text(R.string.checkin_you_are_checked_in)) }
                    loadBookings()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isCheckingIn = false) }
                    reportError(error)
                }
        }
    }

    fun checkInFromScan(event: CalendarEventUi, raw: String?) {
        val scanned = parseCheckInQr(raw)
        when {
            scanned == null -> _uiState.update { it.copy(dataErrorMessage = text(R.string.checkin_not_a_tutorbridge_code)) }
            scanned.bookingId != event.id -> _uiState.update { it.copy(dataErrorMessage = text(R.string.checkin_wrong_session)) }
            else -> checkIn(event, scanned.code, "QR")
        }
    }

    fun scannerUnavailable() {
        _uiState.update { it.copy(dataErrorMessage = text(R.string.checkin_scanner_unavailable)) }
    }

    // ==================== MESSAGING ====================

    private var chatPoller: Job? = null

    fun loadThreads() {
        val me = _uiState.value.userId
        if (me.isBlank()) return
        viewModelScope.launch {
            repository.threads(me)
                .onSuccess { threads -> _uiState.update { it.copy(threads = threads) } }
                .onFailure { reportLoadError(it) }
        }
    }

    fun openThread(thread: ThreadUi) {
        _uiState.update {
            it.copy(
                selectedThreadId = thread.id,
                selectedThreadName = thread.otherName,
                selectedThreadBlocked = thread.blocked,
                selectedThreadBlockedByMe = thread.blockedByMe,
                chatMessages = emptyList(),
                chatPage = 1,
                chatTotalPages = 1,
                chatDraft = "",
                currentRoute = "chat"
            )
        }
        viewModelScope.launch { fetchMessages(page = 1, quiet = false) }

        // There is no push channel into an open chat, so look for new messages
        // while it is on screen; navigateTo() stops this the moment it is not.
        chatPoller?.cancel()
        chatPoller = viewModelScope.launch {
            while (isActive) {
                delay(CHAT_POLL_MILLIS)
                if (_uiState.value.currentRoute != "chat") break
                fetchMessages(page = 1, quiet = true)
            }
        }
    }

    /** "Load earlier messages": the next page back in time. */
    fun loadEarlierMessages() {
        val next = _uiState.value.chatPage + 1
        if (next > _uiState.value.chatTotalPages) return
        viewModelScope.launch { fetchMessages(page = next, quiet = false) }
    }

    /** [quiet] is for the poll: a dropped connection every few seconds should not become a snackbar every few seconds. */
    private suspend fun fetchMessages(page: Int, quiet: Boolean) {
        val state = _uiState.value
        val threadId = state.selectedThreadId ?: return
        repository.messages(threadId, state.userId, page)
            .onSuccess { (messages, totalPages) ->
                _uiState.update { current ->
                    // The user may have moved on to another conversation while this was in flight.
                    if (current.selectedThreadId != threadId) {
                        current
                    } else {
                        current.copy(
                            chatMessages = mergeMessages(current.chatMessages, messages),
                            chatTotalPages = totalPages,
                            chatPage = maxOf(current.chatPage, page)
                        )
                    }
                }
            }
            .onFailure { if (!quiet) reportLoadError(it) }
    }

    fun setChatDraft(text: String) {
        // Capped where the server caps it, so a long paste fails here and not on send.
        if (text.length <= MAX_MESSAGE_LENGTH) _uiState.update { it.copy(chatDraft = text) }
    }

    /**
     * Shows the message at once and sends it in the background. The id the
     * server gets is generated here, so if the reply is lost and the send is
     * repeated the server hands back the stored message instead of a duplicate.
     */
    fun sendChatMessage() {
        val state = _uiState.value
        val threadId = state.selectedThreadId ?: return
        val text = state.chatDraft.trim()
        if (text.isEmpty()) return

        val clientMessageId = UUID.randomUUID().toString()
        val local = MessageUi(
            id = "local-$clientMessageId",
            body = text,
            mine = true,
            time = "",
            sentAt = Instant.now().toString(),
            clientId = clientMessageId,
            pending = true
        )
        _uiState.update { it.copy(chatMessages = it.chatMessages + local, chatDraft = "") }

        viewModelScope.launch {
            val payload = MessagePayload(threadId, text, clientMessageId)
            when (val delivery = sendOrQueue(OutboxType.SEND_MESSAGE, payload, announce = false) {
                repository.sendMessage(threadId, state.userId, text, clientMessageId)
            }) {
                Delivery.Sent -> {
                    // The server's copy is fetched on the next poll; until then the bubble stays as "Sending…".
                    fetchMessages(page = 1, quiet = true)
                }
                Delivery.Queued -> {
                    // Stays on screen, now as "Waiting to send"; the sync swaps it for the real message.
                    _uiState.update { current ->
                        current.copy(chatMessages = current.chatMessages.map {
                            if (it.id == local.id) it.copy(queued = true) else it
                        })
                    }
                }
                is Delivery.Refused -> {
                    // Take the unsent bubble back and give the words back to the box.
                    _uiState.update { current ->
                        current.copy(
                            chatMessages = current.chatMessages.filterNot { it.id == local.id },
                            chatDraft = current.chatDraft.ifBlank { text }
                        )
                    }
                    reportError(delivery.error)
                }
            }
        }
    }

    /** Block or unblock the other person in the open conversation. Needs a connection: nothing is queued. */
    fun setThreadBlocked(block: Boolean) {
        val threadId = _uiState.value.selectedThreadId ?: return
        viewModelScope.launch {
            repository.setThreadBlocked(threadId, block)
                .onSuccess { state ->
                    _uiState.update {
                        it.copy(
                            selectedThreadBlocked = state.blocked,
                            selectedThreadBlockedByMe = state.blockedByMe,
                            noticeMessage = text(if (block) R.string.chat_blocked_notice else R.string.chat_unblocked_notice)
                        )
                    }
                    loadThreads()
                }
                .onFailure { reportError(it) }
        }
    }

    /** Sends a report to the institution's admins. Needs a connection: a safety report should not sit unseen in a queue. */
    fun reportThread(reason: String, details: String) {
        val threadId = _uiState.value.selectedThreadId ?: return
        viewModelScope.launch {
            repository.reportThread(threadId, reason, details)
                .onSuccess { _uiState.update { it.copy(noticeMessage = text(R.string.chat_report_sent)) } }
                .onFailure { reportError(it) }
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

    fun deleteAccount(onComplete: () -> Unit) {
        val user = Firebase.auth.currentUser
        if (user == null) {
            _uiState.update { it.copy(authErrorMessage = text(R.string.vm_no_signed_in_user)) }
            return
        }
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            try {
                // Schedule the TutorBridge record for deletion first (POPIA
                // grace period) - it needs the Firebase token that deleting
                // the account is about to invalidate.
                val scheduled = repository.scheduleAccountDeletion()
                if (scheduled.isFailure) {
                    // Deleting the Firebase user now would strand the TutorBridge
                    // record: there is no account left to authenticate a retry with.
                    _uiState.update {
                        it.copy(
                            isAuthLoading = false,
                            authErrorMessage = friendlyMessage(scheduled.exceptionOrNull()!!)
                        )
                    }
                    return@launch
                }
                // The account is going, so anything it had queued has nowhere to be sent.
                outbox.clearUser()
                user.delete().await()
                cacheClockJob?.cancel()
                outboxJob?.cancel()
                localCache.clear()
                GoogleAuthentication.clearCredentials(getApplication())
                AppPreferences(getApplication()).clear()
                _uiState.update { signedOutState() }
                onComplete()
            } catch (exception: FirebaseAuthRecentLoginRequiredException) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = text(R.string.vm_reauth_delete_account)) }
            } catch (exception: Exception) {
                _uiState.update { it.copy(isAuthLoading = false, authErrorMessage = text(R.string.vm_delete_failed)) }
            }
        }
    }

    // ==================== THEME & PREFERENCES ====================

    fun toggleDarkTheme(isDark: Boolean) {
        _uiState.update { it.copy(isDarkTheme = isDark) }
        saveSettings(UpdateSettingsRequest(theme = if (isDark) "DARK" else "LIGHT"))
    }

    /**
     * The "Force offline mode" switch. The OkHttp client reads the same flag
     * (OfflineGateInterceptor), so while it is on no request leaves the phone;
     * turning it off brings the data back up to date.
     */
    fun toggleOfflineMode(offline: Boolean) {
        Log.i(NETWORK, "Force offline mode ${if (offline) "on" else "off"}")
        OfflineMode.forced = offline
        _uiState.update { it.copy(isOffline = offline) }
        if (!offline) {
            requestSync()
            refreshIfSignedIn()
        }
    }

    /** Called with every change in the phone's connectivity. */
    private fun onNetworkChanged(available: Boolean) {
        val before = _uiState.value
        if (before.networkAvailable == available) return
        Log.i(NETWORK, "Network ${if (available) "available" else "lost"}")
        _uiState.update { it.copy(networkAvailable = available) }
        if (available && !before.isOffline) {
            requestSync()
            refreshIfSignedIn()
        }
    }

    /** Back online: pull fresh data, but only for someone who is actually inside the app. */
    private fun refreshIfSignedIn() {
        val insideApp = Firebase.auth.currentUser != null &&
            _uiState.value.currentRoute !in setOf("splash", "auth", "biometric_lock", "new_password")
        if (insideApp) refreshAll()
    }

    /** The picker's choice: change the app now, and tell the account so other phones agree. */
    fun setLanguage(name: String) {
        val language = Language.fromDisplayName(name)
        _uiState.update { it.copy(selectedLanguage = language.displayName) }
        applyLanguage(language)
        saveSettings(UpdateSettingsRequest(language = language.serverCode))
    }

    // Reference: Android Developers. 2026. Per-app language preferences. [Online].
    //   Available at: https://developer.android.com/guide/topics/resources/app-languages [Accessed 21 September 2026].
    /**
     * Changes the language of the app. AppCompat recreates the open screen by itself, and keeps
     * the choice across restarts (see AppLocalesMetadataHolderService in the manifest).
     */
    private fun applyLanguage(language: Language) {
        Log.i(TAG, "App language -> ${language.tag}")
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        // So Firebase's own emails, such as the password reset, arrive in the same language.
        Firebase.auth.setLanguageCode(language.tag)
    }

    /** What was picked in this app on this phone, or null if nothing has been. */
    private fun languageChosenOnThisPhone(): Language? =
        AppCompatDelegate.getApplicationLocales().takeIf { !it.isEmpty }?.get(0)?.let { Language.fromTag(it.toLanguageTag()) }

    /** The language the app is actually showing: a choice made here, or else the phone's own if the app has it. */
    private fun effectiveLanguage(): Language =
        languageChosenOnThisPhone() ?: Language.fromTag(Locale.getDefault().language) ?: Language.ENGLISH
    fun toggleBookingUpdates(enabled: Boolean) {
        AppPreferences(getApplication()).enableBookingUpdatesSaved(enabled)
        _uiState.update { it.copy(bookingUpdatesEnabled = enabled) }
        saveNotificationPreferences()
    }

    fun toggleMessageNotifications(enabled: Boolean) {
        AppPreferences(getApplication()).enableMessageNotificationsSaved(enabled)
        _uiState.update { it.copy(messageNotificationsEnabled = enabled) }
        saveNotificationPreferences()
    }

    /** An empty id means no default. */
    fun setDefaultArea(id: String) {
        _uiState.update { it.copy(defaultAreaId = id) }
        saveSettings(UpdateSettingsRequest(defaultAreaId = id))
    }

    /** Answered the "turn on fingerprint unlock?" question, or the phone cannot do it, so it is not asked again. */
    fun dismissBiometricOffer() {
        AppPreferences(getApplication()).markBiometricOffered()
        _uiState.update { it.copy(showBiometricOffer = false) }
    }

    fun showNotice(message: String) {
        _uiState.update { it.copy(noticeMessage = message) }
    }

    fun toggleBiometric(enabled: Boolean) {
        AppPreferences(getApplication()).enableBiometricSaved(enabled)
        _uiState.update { it.copy(biometricEnabled = enabled) }
        saveSettings(UpdateSettingsRequest(biometricEntry = enabled))
    }

    // ==================== GOOGLE CALENDAR ====================

    fun refreshCalendarLink() {
        viewModelScope.launch {
            repository.calendarLink().onSuccess { link ->
                _uiState.update {
                    it.copy(
                        calendarLinked = link.linked,
                        calendarAccountEmail = link.googleAccountEmail,
                        calendarSyncError = link.lastSyncError
                    )
                }
            }
        }
    }

    /** Called with the auth code from GoogleAuthentication.rememberCalendarAuthorizer. */
    fun linkCalendar(authCode: String) {
        _uiState.update { it.copy(isCalendarLinking = true, calendarMessage = null) }
        viewModelScope.launch {
            repository.linkCalendar(authCode)
                .onSuccess { link ->
                    _uiState.update {
                        it.copy(
                            isCalendarLinking = false,
                            calendarLinked = link.linked,
                            calendarAccountEmail = link.googleAccountEmail,
                            calendarMessage = text(R.string.vm_calendar_connected)
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isCalendarLinking = false,
                            calendarMessage = friendlyMessage(error)
                        )
                    }
                }
        }
    }

    fun unlinkCalendar() {
        viewModelScope.launch {
            repository.unlinkCalendar()
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            calendarLinked = false,
                            calendarAccountEmail = null,
                            calendarMessage = text(R.string.vm_calendar_disconnected)
                        )
                    }
                }
                .onFailure { error -> reportError(error) }
        }
    }

    fun setCalendarMessage(message: String?) {
        _uiState.update { it.copy(isCalendarLinking = false, calendarMessage = message) }
    }

    private fun saveNotificationPreferences() {
        val state = _uiState.value
        saveSettings(
            UpdateSettingsRequest(
                notifications = NotificationPreferencesDto(
                    booking = state.bookingUpdatesEnabled,
                    message = state.messageNotificationsEnabled
                )
            )
        )
    }

    /**
     * Settings are applied on screen straight away; this is only about getting
     * them to the server. Offline, each change is queued and replayed in order,
     * the last value for a setting winning.
     */
    private fun saveSettings(body: UpdateSettingsRequest) {
        viewModelScope.launch {
            val delivery = sendOrQueue(OutboxType.SAVE_SETTINGS, SettingsPayload(body)) { repository.saveSettings(body) }
            if (delivery is Delivery.Refused) reportError(delivery.error)
        }
    }

    // ==================== HELPERS ====================

    /** "2026-10-16" + "10:30 AM" -> a zoned start time, or null if either is missing. */
    private fun startOfSelectedSlot(state: TutorBridgeUiState) = runCatching {
        LocalDate.parse(state.selectedBookingDate)
            .atTime(LocalTime.parse(state.selectedSlot, slotTime))
            .atZone(ZoneId.systemDefault())
    }.getOrNull()

    // ==================== PUSH ====================

    // Runs on every sign-in and biometric unlock. The backend upserts by token, so repeats are harmless.
    // Reference: Firebase. 2026. Get started with Firebase Cloud Messaging in Android apps. [Online].
    //   Available at: https://firebase.google.com/docs/cloud-messaging/android/get-started [Accessed 21 September 2026].
    private fun registerPushToken() {
        viewModelScope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                repository.registerPushToken(token).getOrThrow()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.w("Push", "Could not register push token", e) }
        }
    }

    // Needs a signed-in Firebase user: AuthInterceptor reads the ID token from it.
    private suspend fun unregisterPushToken() {
        try {
            val messaging = FirebaseMessaging.getInstance()
            repository.unregisterPushToken(messaging.token.await())
            // Also invalidate it locally: if the server call failed, the next push
            // returns "not registered" and the backend prunes it.
            messaging.deleteToken().await()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { Log.w("Push", "Could not unregister push token", e) }
    }

    // ==================== SIGNED-OUT STATE ====================

    /**
     * A clean slate for the next person to sign in. Whether the phone has a
     * connection is not about the user, so it is carried over - the monitor only
     * reports changes, and would never say again that the network is gone.
     */
    private fun signedOutState(): TutorBridgeUiState {
        OfflineMode.forced = false
        return TutorBridgeUiState(networkAvailable = _uiState.value.networkAvailable)
    }

    // Last in the class: this reaches most of the ViewModel, so every property has to be set up first.
    init {
        if (Firebase.auth.currentUser != null && !_uiState.value.biometricEnabled) {
            completeAuth()
            // A tutor who closed the app on the new-password screen still has to replace the temporary one.
            viewModelScope.launch {
                val result = authRepository.bootstrapProfile(null, null)
                if (result is AuthResult.Success && result.profile.mustChangePassword) {
                    _uiState.update { it.copy(currentRoute = "new_password") }
                }
            }
        }
    }

    private companion object {
        const val TAG = "TutorBridgeVM"

        // Shared with the other offline classes, so one logcat filter follows a change from tap to server.
        const val NETWORK = "Network"
        const val OUTBOX = "Outbox"
    }
}
