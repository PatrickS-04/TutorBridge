package com.tutorbridge.app.ui.navigation

import androidx.compose.ui.res.stringResource
import com.tutorbridge.app.R
import android.hardware.biometrics.BiometricManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.tutorbridge.app.data.model.AuthMode
import com.tutorbridge.app.data.model.BiometricAuthentication
import com.tutorbridge.app.data.model.GoogleAuthentication
import com.tutorbridge.app.data.model.UserRole
import com.tutorbridge.app.ui.components.OfflineBanner
import com.tutorbridge.app.ui.screens.auth.AuthScreen
import com.tutorbridge.app.ui.screens.auth.BiometricScreen
import com.tutorbridge.app.ui.screens.auth.NewPasswordScreen
import com.tutorbridge.app.ui.screens.availability.TutorAvailabilityScreen
import com.tutorbridge.app.ui.screens.booking.BookingConfirmationScreen
import com.tutorbridge.app.ui.screens.booking.BookingDeclinedScreen
import com.tutorbridge.app.ui.screens.booking.BookingFlowScreen
import com.tutorbridge.app.ui.screens.booking.BookingRequestSentScreen
import com.tutorbridge.app.ui.screens.booking.ReviewBookingScreen
import com.tutorbridge.app.ui.screens.calendar.CalendarScreen
import com.tutorbridge.app.ui.screens.claims.TutorExportClaimsScreen
import com.tutorbridge.app.ui.screens.find.FindTutorsScreen
import com.tutorbridge.app.ui.screens.find.TutorProfileScreen
import com.tutorbridge.app.ui.screens.history.LearningHistoryScreen
import com.tutorbridge.app.ui.screens.messages.ChatScreen
import com.tutorbridge.app.ui.screens.messages.MessagesScreen
import com.tutorbridge.app.ui.screens.home.StudentHomeScreen
import com.tutorbridge.app.ui.screens.home.TutorHomeScreen
import com.tutorbridge.app.ui.screens.notifications.NotificationsScreen
import com.tutorbridge.app.ui.screens.offline.OfflineScreen
import com.tutorbridge.app.ui.screens.prep.SessionPrepScreen
import com.tutorbridge.app.ui.screens.profile.EditTutorProfileScreen
import com.tutorbridge.app.ui.screens.requests.MyRequestsScreen
import com.tutorbridge.app.ui.screens.requests.NewTuitionRequestScreen
import com.tutorbridge.app.ui.screens.requests.RequestDetailScreen
import com.tutorbridge.app.ui.screens.requests.SessionOfferSentScreen
import com.tutorbridge.app.ui.screens.requests.TutorRequestsBoardScreen
import com.tutorbridge.app.ui.screens.settings.DeleteAccountScreen
import com.tutorbridge.app.ui.screens.settings.EditProfileScreen
import com.tutorbridge.app.ui.screens.settings.SettingsScreen
import com.tutorbridge.app.ui.screens.splash.SplashScreen
import com.tutorbridge.app.ui.screens.sync.SyncStatusScreen
import com.tutorbridge.app.viewmodel.TutorBridgeUiState
import com.tutorbridge.app.viewmodel.TutorBridgeViewModel

sealed class NavItem(val route: String, val titleRes: Int, val icon: ImageVector) {
    object Home : NavItem("home", R.string.nav_home, Icons.Default.Home)
    object Find : NavItem("find", R.string.nav_find, Icons.Default.Search)
    object Claims : NavItem("claims", R.string.nav_claims, Icons.Default.Receipt)
    object Calendar : NavItem("calendar", R.string.nav_calendar, Icons.Default.CalendarMonth)
    object Requests : NavItem("requests", R.string.nav_requests, Icons.Default.Assignment)
    object Settings : NavItem("settings", R.string.nav_settings, Icons.Default.Settings)
}

private val studentRoutesWithBottomBar = listOf(
    "student_home", "find_tutors", "calendar", "my_requests", "settings"
)

private val tutorRoutesWithBottomBar = listOf(
    "tutor_home", "claims_export", "calendar", "tutor_requests", "settings"
)

@Composable
fun TutorBridgeNavGraph(
    viewModel: TutorBridgeViewModel,
    uiState: TutorBridgeUiState
) {
    val studentNavItems = listOf(NavItem.Home, NavItem.Find, NavItem.Calendar, NavItem.Requests, NavItem.Settings)
    val tutorNavItems = listOf(NavItem.Home, NavItem.Claims, NavItem.Calendar, NavItem.Requests, NavItem.Settings)

    val currentItems = if (uiState.role == UserRole.TUTOR) tutorNavItems else studentNavItems
    val showBottomBar = uiState.role != null && uiState.currentRoute in (studentRoutesWithBottomBar + tutorRoutesWithBottomBar)

    val snackbarHostState = remember { SnackbarHostState() }

    // Anything the server refused or could not answer.
    LaunchedEffect(uiState.dataErrorMessage) {
        uiState.dataErrorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissDataError()
        }
    }

    LaunchedEffect(uiState.noticeMessage) {
        uiState.noticeMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissNotice()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    currentItems.forEach { item ->
                        val isSelected = when (item) {
                            NavItem.Home -> uiState.currentRoute in listOf("student_home", "tutor_home")
                            NavItem.Find -> uiState.currentRoute in listOf("find_tutors", "tutor_profile")
                            NavItem.Claims -> uiState.currentRoute == "claims_export"
                            NavItem.Calendar -> uiState.currentRoute == "calendar"
                            NavItem.Requests -> uiState.currentRoute in listOf("my_requests", "tutor_requests", "new_request", "request_detail", "session_offer_sent")
                            NavItem.Settings -> uiState.currentRoute == "settings"
                        }
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = stringResource(item.titleRes)) },
                            label = { Text(stringResource(item.titleRes)) },
                            selected = isSelected,
                            onClick = {
                                val target = when (item) {
                                    NavItem.Home -> if (uiState.role == UserRole.TUTOR) "tutor_home" else "student_home"
                                    NavItem.Find -> "find_tutors"
                                    NavItem.Claims -> "claims_export"
                                    NavItem.Calendar -> "calendar"
                                    NavItem.Requests -> if (uiState.role == UserRole.TUTOR) "tutor_requests" else "my_requests"
                                    NavItem.Settings -> "settings"
                                }
                                viewModel.navigateTo(target)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
        OfflineBanner(uiState, onClick = { viewModel.navigateTo("sync_status") })
        Surface(modifier = Modifier.weight(1f)) {
            when (uiState.currentRoute) {
                "splash" -> SplashScreen(
                    onGetStarted = { viewModel.navigateTo("auth") }
                )
                "auth" -> AuthScreen(
                    uiState = uiState,
                    onRoleSelect = { viewModel.selectRole(it) },
                    onInstitutionSelect = { id -> viewModel.selectSignupInstitution(id) },
                    onSignIn = { email, password -> viewModel.signIn(email, password) },
                    onSignUp = { fullName, email, password -> viewModel.signUp(fullName, email, password) },
                    onGoogleSignIn = { idToken -> viewModel.signInWithGoogle(idToken) },
                    onGoogleSignInError = { message -> viewModel.setAuthError(message) },
                    onForgotPassword = { email -> viewModel.sendPasswordResetTo(email) },
                    onToggleAuthMode = {viewModel.setAuthMode(
                        if (uiState.authMode == AuthMode.SIGN_IN) {
                            AuthMode.SIGN_UP
                        }
                        else {
                            AuthMode.SIGN_IN
                        }
                    )}
                )
                "new_password" -> NewPasswordScreen(
                    uiState = uiState,
                    onSubmit = { viewModel.setNewPassword(it) },
                    onSignOut = { viewModel.signOut() }
                )
                "biometric_lock" -> {
                    val activity = LocalContext.current as FragmentActivity
                    BiometricScreen(
                        onUnlockClick = {
                            val biometricManager = BiometricAuthentication(activity)
                            if (biometricManager.canAuthenticate()) {
                                biometricManager.authenticate(
                                    onSuccess = { viewModel.completeAuth() },
                                    onFailure = {}
                                )
                            } else {
                                viewModel.completeAuth()
                            }
                        },
                        onSignInDifferently = { viewModel.signOut() }
                    )
                }
                "student_home" -> StudentHomeScreen(
                    uiState = uiState,
                    onNavigate = { viewModel.navigateTo(it) }
                )
                "tutor_home" -> TutorHomeScreen(
                    uiState = uiState,
                    onNavigate = { viewModel.navigateTo(it) },
                    onAcceptBooking = { bookingId -> viewModel.acceptTutorBooking(bookingId) },
                    onDeclineBooking = { bookingId -> viewModel.declineTutorBooking(bookingId) }
                )
                "find_tutors" -> FindTutorsScreen(
                    uiState = uiState,
                    onSelectTutor = { tutor ->
                        viewModel.selectTutor(tutor)
                        viewModel.navigateTo("tutor_profile")
                    }
                )
                "tutor_profile" -> TutorProfileScreen(
                    uiState = uiState,
                    onBack = { viewModel.navigateTo("find_tutors") },
                    onBookSession = { viewModel.navigateTo("booking_flow") }
                )
                "booking_flow" -> BookingFlowScreen(
                    uiState = uiState,
                    onSelectSlot = { viewModel.selectSlot(it) },
                    onSelectLocation = { viewModel.selectLocation(it) },
                    onSelectDate = { viewModel.selectBookingDate(it) },
                    onNext = { viewModel.navigateTo("review_booking") },
                    onBack = { viewModel.navigateTo("tutor_profile") }
                )
                "review_booking" -> ReviewBookingScreen(
                    uiState = uiState,
                    onConfirm = { viewModel.sendBookingRequest() },
                    onBack = { viewModel.navigateTo("booking_flow") }
                )
                "booking_sent" -> BookingRequestSentScreen(
                    onCheckStatus = { viewModel.checkBookingStatus() },
                    onViewCalendar = { viewModel.navigateTo("calendar") }
                )
                "booking_confirmed" -> BookingConfirmationScreen(
                    uiState = uiState,
                    onGoToCalendar = { viewModel.navigateTo("calendar") },
                    onBackToDashboard = { viewModel.navigateTo("student_home") }
                )
                "booking_declined" -> BookingDeclinedScreen(
                    declineNote = uiState.lastDeclineNote,
                    onPickAnotherSlot = { viewModel.navigateTo("booking_flow") },
                    onFindAnotherTutor = { viewModel.navigateTo("find_tutors") }
                )
                "calendar" -> CalendarScreen(
                    uiState = uiState,
                    onSelectDate = { viewModel.selectCalendarDate(it) },
                    onFilterChange = { viewModel.setCalendarFilter(it) },
                    onOpenSessionNotes = { session -> viewModel.openSessionNotes(session) },
                    onAddEvent = { viewModel.navigateTo("find_tutors") },
                    onReschedule = { session, date, time -> viewModel.rescheduleSession(session, date, time) },
                    onShowCheckInCode = { session -> viewModel.showCheckInCode(session) },
                    onDismissCheckInCode = { viewModel.dismissCheckInCode() },
                    onScannedCheckIn = { session, raw -> viewModel.checkInFromScan(session, raw) },
                    onTypedCheckIn = { session, code -> viewModel.checkIn(session, code, "CODE") },
                    onScannerUnavailable = { viewModel.scannerUnavailable() }
                )

                "sync_status" -> SyncStatusScreen(
                    uiState = uiState,
                    onBack = { viewModel.navigateTo(if (uiState.role == UserRole.TUTOR) "tutor_home" else "student_home") },
                    onSyncNow = { viewModel.syncNow() },
                    onRetryFailed = { viewModel.retryFailedSync() },
                    onDiscard = { viewModel.discardOutboxItem(it) }
                )

                // ===== Messaging =====
                "messages" -> MessagesScreen(
                    uiState = uiState,
                    onBack = { viewModel.navigateTo(if (uiState.role == UserRole.TUTOR) "tutor_home" else "student_home") },
                    onOpenThread = { viewModel.openThread(it) }
                )
                "chat" -> ChatScreen(
                    uiState = uiState,
                    onBack = { viewModel.navigateTo("messages") },
                    onDraftChange = { viewModel.setChatDraft(it) },
                    onSend = { viewModel.sendChatMessage() },
                    onLoadEarlier = { viewModel.loadEarlierMessages() }
                )

                // ===== Student requests flow =====
                "my_requests" -> MyRequestsScreen(
                    uiState = uiState,
                    onNewRequest = { viewModel.startNewRequest() },
                    onSelectRequest = { viewModel.selectRequest(it) }
                )
                "new_request" -> NewTuitionRequestScreen(
                    uiState = uiState,
                    onBack = { viewModel.discardRequestDraft() },
                    onNext = { viewModel.updateDraftStep(uiState.requestDraft.step + 1) },
                    onPrevious = { viewModel.updateDraftStep(uiState.requestDraft.step - 1) },
                    onSelectSubject = { id, name -> viewModel.selectDraftSubject(id, name) },
                    onLevelChange = { viewModel.updateDraftLevel(it) },
                    onToggleDay = { viewModel.toggleDraftDay(it) },
                    onHoursChange = { start, end -> viewModel.updateDraftHours(start, end) },
                    onSelectArea = { id, name -> viewModel.selectDraftArea(id, name) },
                    onNotesChange = { viewModel.updateDraftNotes(it) },
                    onSubmit = { viewModel.submitTuitionRequest() }
                )
                "request_detail" -> RequestDetailScreen(
                    uiState = uiState,
                    onBack = { viewModel.navigateTo("my_requests") },
                    onAcceptApplicant = { applicant -> viewModel.acceptApplicant(applicant) },
                    onEdit = { viewModel.startEditingRequest(it) },
                    onClose = { viewModel.endRequest(it.id, withdraw = false) },
                    onWithdraw = { viewModel.endRequest(it.id, withdraw = true) }
                )

                // ===== Tutor requests board =====
                "tutor_requests" -> TutorRequestsBoardScreen(
                    uiState = uiState,
                    onAcceptAndOffer = { viewModel.acceptAndOfferSession(it) }
                )
                "session_offer_sent" -> SessionOfferSentScreen(
                    onViewApplications = { viewModel.navigateTo("tutor_requests") },
                    onBackToDashboard = { viewModel.navigateTo("tutor_home") }
                )

                "availability" -> TutorAvailabilityScreen(
                    uiState = uiState,
                    onBack = { viewModel.navigateTo("tutor_home") },
                    onToggleDay = { viewModel.toggleActiveDay(it) },
                    onEditHours = { day, start, end -> viewModel.updateDayHours(day, start, end) }
                )
                "claims_export" -> TutorExportClaimsScreen(
                    uiState = uiState,
                    onBack = { viewModel.navigateTo("tutor_home") },
                    onSelectInstitution = { viewModel.selectInstitution(it) },
                    onSelectClaimPeriod = { viewModel.selectClaimPeriod(it) },
                    onLoadEarlier = { viewModel.loadEarlierClaims() },
                    onFetchCsv = { month, onResult -> viewModel.fetchClaimCsv(month, onResult) }
                )
                "session_prep" -> SessionPrepScreen(
                    uiState = uiState,
                    // Side notes are a draft while they are being typed, so
                    // leaving the screen is what commits them.
                    onBack = { viewModel.closeSessionNotes() },
                    onToggleGoal = { viewModel.toggleSessionGoal(it) },
                    onRemoveGoal = { viewModel.removeChecklistItem(it) },
                    onNewItemChange = { viewModel.setNewChecklistItem(it) },
                    onAddItem = { viewModel.addChecklistItem() },
                    onSideNotesChange = { viewModel.setSessionSideNotes(it) },
                    onSaveNotes = { viewModel.closeSessionNotes(requireSaved = true) }
                )
                "edit_tutor_profile" -> EditTutorProfileScreen(
                    uiState = uiState,
                    onBack = { viewModel.navigateTo("settings") }
                )
                "edit_profile" -> EditProfileScreen(
                    uiState = uiState,
                    onLoadProfile = { viewModel.refreshProfile() },
                    onSave = { email, bio, hourlyRate, department ->
                        viewModel.saveProfile(email, bio, hourlyRate, department) { viewModel.navigateTo("settings") }
                    },
                    onBack = { viewModel.navigateTo("settings") }
                )
                "learning_history" -> LearningHistoryScreen(
                    uiState = uiState,
                    onBack = { viewModel.navigateTo("student_home") }
                )
                "notifications" -> NotificationsScreen(
                    uiState = uiState,
                    onBack = { viewModel.navigateTo(if (uiState.role == UserRole.TUTOR) "tutor_home" else "student_home") }
                )
                "settings" -> {
                    val linkCalendar = GoogleAuthentication.rememberCalendarAuthorizer(
                        onAuthCode = { authCode -> viewModel.linkCalendar(authCode) },
                        onError = { message -> viewModel.setCalendarMessage(message) }
                    )
                    // Both roles see the row now, so both need the status.
                    LaunchedEffect(uiState.role) {
                        viewModel.refreshCalendarLink()
                    }
                    SettingsScreen(
                        uiState = uiState,
                        onToggleDark = { viewModel.toggleDarkTheme(it) },
                        onToggleOffline = { viewModel.toggleOfflineMode(it) },
                        onSelectLanguage = { viewModel.setLanguage(it) },
                        onDeleteAccountClick = { viewModel.navigateTo("delete_account") },
                        onSignOut = { viewModel.signOut() },
                        onEditProfileClick = { viewModel.navigateTo("edit_profile") },
                        onToggleBookingUpdates = { viewModel.toggleBookingUpdates(it) },
                        onToggleSessionReminders = { viewModel.toggleSessionReminders(it) },
                        onToggleSmsFallback = { viewModel.toggleSmsFallback(it) },
                        onToggleBiometric = { viewModel.toggleBiometric(it) },
                        onLinkCalendar = linkCalendar,
                        onUnlinkCalendar = { viewModel.unlinkCalendar() },
                        onChangePassword = { viewModel.sendPasswordReset(it) },
                        onOpenSyncStatus = { viewModel.navigateTo("sync_status") }
                    )
                }
                "delete_account" -> DeleteAccountScreen(
                    uiState = uiState,
                    onConfirmDelete = {
                        viewModel.deleteStudentAccount {
                            viewModel.navigateTo("auth")
                        }
                    },
                    onCancel = { viewModel.navigateTo("settings") },
                    onReasonChange = { viewModel.setDeleteReason(it) },
                    onConfirmCheckChange = { viewModel.setDeleteConfirmChecked(it) },
                    onConfirmTextChange = { viewModel.setDeleteConfirmText(it) }
                )
                "offline_state" -> OfflineScreen(
                    onRetry = { viewModel.navigateTo(if (uiState.role == UserRole.TUTOR) "tutor_home" else "student_home") },
                    onBack = { viewModel.navigateTo(if (uiState.role == UserRole.TUTOR) "tutor_home" else "student_home") }
                )
                else -> StudentHomeScreen(
                    uiState = uiState,
                    onNavigate = { viewModel.navigateTo(it) }
                )
            }
        }
        }
    }
}
