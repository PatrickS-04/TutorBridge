package com.tutorbridge.app

import com.tutorbridge.app.data.model.CalendarEventType
import com.tutorbridge.app.data.model.CalendarEventUi
import com.tutorbridge.app.data.model.EventStatus
import com.tutorbridge.app.viewmodel.canCheckInNow
import com.tutorbridge.app.viewmodel.parseCheckInQr
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.experimental.theories.suppliers.TestedOn
import java.time.Instant

class CheckInTest {
    private fun session(confirmed: Boolean = true, checkedInTime: String = "") = CalendarEventUi(
        id = "b1", title = "Maths with Thandi", subject = "Maths", date = "2026-11-02",
        startTime = "10:00", endTime = "11:00", type = CalendarEventType.LESSON,
        participantName = "Thandi", participantRole = "student", location = "Library",
        status = EventStatus.CONFIRMED, forRole = "both",
        startIso = "2026-11-02T10:00:00.000Z", endIso = "2026-11-02T11:00:00.000Z",
        isConfirmed = confirmed, checkedInTime = checkedInTime
    )

    @Test
    fun read_a_tutorbridge_check_in_qr() {
        val scanned = parseCheckInQr("tutorbridge://checkin?booking=b1&code=042917")
        assertEquals("b1", scanned?.bookingId)
        assertEquals("042917", scanned?.code)
    }

    @Test
    fun ignores_other_qr_codes() {
        assertNull(parseCheckInQr("https://example.com"))
        assertNull(parseCheckInQr("tutorbridge://checkin?booking=b1&code=12"))
        assertNull(parseCheckInQr(null))
    }

    @Test
    fun check_in_window_matches_the_api() {
        assertFalse(session().canCheckInNow(Instant.parse("2026-11-02T09:44:00Z")))
        assertTrue(session().canCheckInNow(Instant.parse("2026-11-02T09:45:00Z")))
        assertFalse(session().canCheckInNow(Instant.parse("2026-11-02T11:00:01Z")))
    }

    @Test
    fun no_check_in_when_unconfirmed_or_already_done() {
        val during = Instant.parse("2026-11-02T10:30:00Z")
        assertFalse(session(confirmed = false).canCheckInNow(during))
        assertFalse(session(checkedInTime = "10:02").canCheckInNow(during))
    }
}