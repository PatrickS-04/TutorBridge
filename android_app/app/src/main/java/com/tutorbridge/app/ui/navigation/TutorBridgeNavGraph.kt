package com.tutorbridge.app.ui.navigation

import android.hardware.biometrics.BiometricManager
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.tutorbridge.app.data.model.AuthMode
import com.tutorbridge.app.data.model.BiometricAuthentication
import com.tutorbridge.app.data.model.UserRole
import com.tutorbridge.app.ui.screens.auth.AuthScreen
import com.tutorbridge.app.ui.screens.auth.BiometricScreen
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
import com.tutorbridge.app.ui.screens.settings.SettingsScreen
import com.tutorbridge.app.ui.screens.splash.SplashScreen
import com.tutorbridge.app.viewmodel.TutorBridgeUiState
import com.tutorbridge.app.viewmodel.TutorBridgeViewModel

sealed class NavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : NavItem("home", "Home", Icons.Default.Home)
    object Find : NavItem("find", "Find", Icons.Default.Search)
    object Claims : NavItem("claims", "Claims", Icons.Default.Receipt)
    object Calendar : NavItem("calendar", "Calendar", Icons.Default.CalendarMonth)
    object Requests : NavItem("requests", "Requests", Icons.Default.Assignment)
    object Settings : NavItem("settings", "Settings", Icons.Default.Settings)
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

    Scaffold(
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
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
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
        Surface(modifier = Modifier.padding(innerPadding)) {
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
                    onToggleAuthMode = {viewModel.setAuthMode(
                        if (uiState.authMode == AuthMode.SIGN_IN) {
                            AuthMode.SIGN_UP
                        }
                        else {
                            AuthMode.SIGN_IN
                        }
                    )}
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
                    onAcceptBooking = { viewModel.acceptTutorBooking() },
                    onDeclineBooking = { viewModel.declineTutorBooking() }
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
                    onSimulateAccepted = { uiState.selectedTutor?.let { viewModel.simulateAccepted(it) } },
                    onSimulateDeclined = { viewModel.simulateDeclined() },
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
                    onAddEvent = { viewModel.addCalendarEvent(it) },
                    onNavigateToPrep = { viewModel.navigateTo("session_prep") }
                )

                // ===== Student requests flow =====
                "my_requests" -> MyRequestsScreen(
                    uiState = uiState,
                    onNewRequest = { viewModel.navigateTo("new_request") },
                    onSelectRequest = { viewModel.selectRequest(it) }
                )
                "new_request" -> NewTuitionRequestScreen(
                    uiState = uiState,
                    onBack = { viewModel.navigateTo("my_requests") },
                    onNext = { viewModel.updateDraftStep(uiState.requestDraft.step + 1) },
                    onPrevious = { viewModel.updateDraftStep(uiState.requestDraft.step - 1) },
                    onSubjectChange = { viewModel.updateDraftSubject(it) },
                    onLevelChange = { viewModel.updateDraftLevel(it) },
                    onTimeWindowChange = { viewModel.updateDraftTimeWindow(it) },
                    onVenueChange = { viewModel.updateDraftVenue(it) },
                    onNotesChange = { viewModel.updateDraftNotes(it) },
                    onSubmit = { viewModel.submitTuitionRequest() }
                )
                "request_detail" -> RequestDetailScreen(
                    uiState = uiState,
                    onBack = { viewModel.navigateTo("my_requests") },
                    onAcceptApplicant = { reqId, appId -> viewModel.acceptApplicant(reqId, appId) }
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
                    onTabChange = { viewModel.setAvailabilityTab(it) },
                    onToggleDay = { viewModel.toggleActiveDay(it) }
                )
                "claims_export" -> TutorExportClaimsScreen(
                    uiState = uiState,
                    onBack = { viewModel.navigateTo("tutor_home") },
                    onSelectInstitution = { viewModel.selectInstitution(it) },
                    onSelectClaimPeriod = { viewModel.selectClaimPeriod(it) }
                )
                "session_prep" -> SessionPrepScreen(
                    uiState = uiState,
                    onBack = { viewModel.navigateTo("calendar") },
                    onToggleGoal = { viewModel.toggleSessionGoal(it) }
                )
                "edit_tutor_profile" -> EditTutorProfileScreen(
                    uiState = uiState,
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
                "settings" -> SettingsScreen(
                    uiState = uiState,
                    onToggleDark = { viewModel.toggleDarkTheme(it) },
                    onToggleOffline = { viewModel.toggleOfflineMode(it) },
                    onSelectLanguage = { viewModel.setLanguage(it) },
                    onDeleteAccountClick = { viewModel.navigateTo("delete_account") },
                    onSignOut = { viewModel.signOut() },
                    onToggleBookingUpdates = { viewModel.toggleBookingUpdates(it) },
                    onToggleSessionReminders = { viewModel.toggleSessionReminders(it) },
                    onToggleSmsFallback = { viewModel.toggleSmsFallback(it) },
                    onToggleBiometric = { viewModel.toggleBiometric(it) }
                )
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
