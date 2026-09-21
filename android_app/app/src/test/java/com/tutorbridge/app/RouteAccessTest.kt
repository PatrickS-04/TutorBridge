package com.tutorbridge.app

import com.tutorbridge.app.data.model.UserRole
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
        assertEquals("booking_flow", routeForRole("booking_flow", UserRole.STUDENT))
        assertEquals("calendar", routeForRole("calendar", UserRole.TUTOR))
        assertEquals("settings", routeForRole("settings", UserRole.STUDENT))
        assertEquals("auth", routeForRole("auth", null))
    }
}
