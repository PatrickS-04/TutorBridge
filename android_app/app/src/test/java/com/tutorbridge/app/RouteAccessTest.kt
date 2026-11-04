package com.tutorbridge.app

import com.tutorbridge.app.data.model.UserRole
import com.tutorbridge.app.viewmodel.nextRouteHistory
import com.tutorbridge.app.viewmodel.routeForDeepLink
import com.tutorbridge.app.viewmodel.routeForRole
import org.junit.Assert.assertEquals
import org.junit.Test

// Reference: Android Developers. 2026. Build local unit tests. [Online].
//   Available at: https://developer.android.com/training/testing/local-tests [Accessed 21 September 2026].
class RouteAccessTest {

    @Test
    fun `tutor is bounced off student pages and student off tutor pages`() {
        assertEquals("tutor_home", routeForRole("find_tutors", UserRole.TUTOR))
        assertEquals("tutor_home", routeForRole("my_requests", UserRole.TUTOR))
        assertEquals("student_home", routeForRole("claims_export", UserRole.STUDENT))
        assertEquals("student_home", routeForRole("tutor_requests", UserRole.STUDENT))
    }

    @Test
    fun `own and shared pages pass through`() {
        assertEquals("claims_export", routeForRole("claims_export", UserRole.TUTOR))
        assertEquals("availability", routeForRole("availability", UserRole.TUTOR))
        // The forced new-password step must never bounce a tutor on to their home.
        assertEquals("new_password", routeForRole("new_password", UserRole.TUTOR))
        assertEquals("booking_flow", routeForRole("booking_flow", UserRole.STUDENT))
        assertEquals("calendar", routeForRole("calendar", UserRole.TUTOR))
        assertEquals("settings", routeForRole("settings", UserRole.STUDENT))
        assertEquals("auth", routeForRole("auth", null))
    }

    private fun walk(vararg routes: String) = routes.fold(emptyList<String>(), ::nextRouteHistory)

    @Test
    fun `back returns through the pages visited, without looping`() {
        assertEquals(listOf("student_home", "messages", "chat"), walk("student_home", "messages", "chat"))
        // The chat's arrow goes to messages: that is a step back, not a new page.
        assertEquals(listOf("student_home", "messages"), walk("student_home", "messages", "chat", "messages"))
        assertEquals(listOf("student_home", "find_tutors", "calendar"), walk("student_home", "find_tutors", "calendar"))
        // Home is the bottom of the trail, so Back from it is the exit prompt.
        assertEquals(listOf("student_home"), walk("student_home", "find_tutors", "student_home"))
    }

    @Test
    fun `back cannot return through a finished flow or past a sign-in gate`() {
        assertEquals(
            listOf("student_home", "booking_sent"),
            walk("student_home", "find_tutors", "tutor_profile", "booking_flow", "review_booking", "booking_sent")
        )
        assertEquals(listOf("splash", "auth"), walk("splash", "auth"))
        assertEquals(listOf("auth"), walk("student_home", "settings", "auth"))
        assertEquals(listOf("student_home"), walk("splash", "auth", "student_home"))
        assertEquals(listOf("new_password"), walk("splash", "auth", "new_password"))
    }

    @Test
    fun `messages are open to both roles`() {
        for (role in listOf(UserRole.STUDENT, UserRole.TUTOR)) {
            assertEquals("messages", routeForRole("messages", role))
            assertEquals("chat", routeForRole("chat", role))
        }
    }

    @Test
    fun `sync status is open to both roles`() {
        for (role in listOf(UserRole.STUDENT, UserRole.TUTOR)) {
            assertEquals("sync_status", routeForRole("sync_status", role))
        }
    }

    @Test
    fun `a message notification opens the conversation list`() {
        assertEquals("messages", routeForDeepLink("tutorbridge://threads/66f1a2b3c4d5e6f708192a3b", UserRole.STUDENT))
        assertEquals("messages", routeForDeepLink("tutorbridge://threads/66f1a2b3c4d5e6f708192a3b", UserRole.TUTOR))
    }
}
