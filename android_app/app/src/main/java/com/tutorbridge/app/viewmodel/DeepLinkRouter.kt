package com.tutorbridge.app.viewmodel

import com.tutorbridge.app.data.model.UserRole
import java.net.URI

/** Maps a notify() deepLink onto one of the app's routes. Null means ignore. */
fun routeForDeepLink(link: String?, role: UserRole?): String? {
    if (link == null) return null
    val uri = runCatching { URI(link) }.getOrNull() ?: return null
    if (uri.scheme != "tutorbridge") return null
    return when (uri.host) {
        "bookings" -> if (role == UserRole.TUTOR) "tutor_home" else "calendar" // tutor_home lists pending bookings
        "applications" -> "tutor_requests"   // notify() sends these to the tutor
        "requests" -> "my_requests"          // ...and these to the student
        else -> null
    }
}