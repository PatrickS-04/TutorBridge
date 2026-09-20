package com.tutorbridge.app.viewmodel

import com.tutorbridge.app.data.model.UserRole

// Anything not listed here (auth, calendar, notifications, settings...) is shared.
// Guardians sign in as STUDENT, so they get the student pages.
private val studentOnly = setOf(
    "student_home", "find_tutors", "tutor_profile", "booking_flow", "review_booking",
    "booking_sent", "booking_confirmed", "booking_declined", "my_requests", "new_request",
    "request_detail", "learning_history"
)
// session_prep holds the tutor's own notes on a session, which the server
// never sends to the student, so the page is theirs alone.
private val tutorOnly = setOf(
    "tutor_home", "tutor_requests", "session_offer_sent", "availability", "claims_export",
    "edit_tutor_profile", "session_prep"
)

/** The route this role may open: the one asked for, or its own home if that page belongs to the other role. */
fun routeForRole(route: String, role: UserRole?): String = when {
    role == null -> route
    role == UserRole.TUTOR && route in studentOnly -> "tutor_home"
    role != UserRole.TUTOR && route in tutorOnly -> "student_home"
    else -> route
}
