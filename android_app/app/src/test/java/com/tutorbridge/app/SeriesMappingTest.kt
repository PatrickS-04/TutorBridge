package com.tutorbridge.app

import com.google.gson.Gson
import com.tutorbridge.app.data.model.BookingDto
import com.tutorbridge.app.data.model.UserBriefDto
import com.tutorbridge.app.data.repository.toCalendarEvent
import com.tutorbridge.app.data.repository.toPendingBookingUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** A session of a weekly series keeps its place in the series all the way to the screens. */
class SeriesMappingTest {

    @Before
    fun useEnglish() = Locale.setDefault(Locale.ENGLISH)

    private fun session(index: Int?, count: Int?, seriesId: String? = "s1") = BookingDto(
        _id = "b$index",
        tutorId = UserBriefDto("t1", "Thabo Mokoena"),
        studentId = UserBriefDto("u1", "Zola Khumalo"),
        subjectName = "Mathematics",
        startTime = "2026-11-02T12:00:00.000Z",
        endTime = "2026-11-02T13:00:00.000Z",
        status = "REQUESTED",
        seriesId = seriesId,
        seriesIndex = index,
        seriesCount = count
    )

    @Test
    fun `a calendar event knows which series it is in and where`() {
        val event = session(2, 4).toCalendarEvent(selfIsTutor = false)!!
        assertEquals("s1", event.seriesId)
        assertEquals(2, event.seriesIndex)
        assertEquals(4, event.seriesCount)
        assertTrue(event.isSeries)
    }

    @Test
    fun `a single session is not part of a series`() {
        val event = session(null, null, seriesId = null).toCalendarEvent(selfIsTutor = false)!!
        assertNull(event.seriesId)
        assertEquals(0, event.seriesIndex)
        assertEquals(0, event.seriesCount)
        assertFalse(event.isSeries)
    }

    @Test
    fun `a series id without a count of two or more is not shown as a series`() {
        assertFalse(session(1, 1).toCalendarEvent(selfIsTutor = false)!!.isSeries)
        assertFalse(session(null, null).toCalendarEvent(selfIsTutor = false)!!.isSeries)
    }

    @Test
    fun `a waiting session for the tutor carries its series too`() {
        val pending = session(3, 4).toPendingBookingUi()!!
        assertEquals("s1", pending.seriesId)
        assertEquals(3, pending.seriesIndex)
        assertEquals(4, pending.seriesCount)
        assertEquals("Zola Khumalo", pending.studentName)
    }

    @Test
    fun `a booking list from before series existed still reads`() {
        val json = "{\"_id\":\"b1\",\"subjectName\":\"Maths\",\"startTime\":\"2026-11-02T12:00:00.000Z\"," +
            "\"endTime\":\"2026-11-02T13:00:00.000Z\",\"status\":\"CONFIRMED\"}"
        val booking = Gson().fromJson(json, BookingDto::class.java)
        assertNull(booking.seriesId)
        assertNull(booking.seriesIndex)
        assertNull(booking.seriesCount)
        assertFalse(booking.toCalendarEvent(selfIsTutor = false)!!.isSeries)
    }

    @Test
    fun `a booking from a series reads its fields from the server's json`() {
        val json = "{\"_id\":\"b1\",\"subjectName\":\"Maths\",\"startTime\":\"2026-11-02T12:00:00.000Z\"," +
            "\"endTime\":\"2026-11-02T13:00:00.000Z\",\"status\":\"REQUESTED\"," +
            "\"seriesId\":\"s9\",\"seriesIndex\":2,\"seriesCount\":4}"
        val event = Gson().fromJson(json, BookingDto::class.java).toCalendarEvent(selfIsTutor = true)!!
        assertEquals("s9", event.seriesId)
        assertEquals(2, event.seriesIndex)
        assertEquals(4, event.seriesCount)
    }

    @Test
    fun `a waiting session knows which weekday it falls on, in the phone's time zone`() {
        val saved = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Africa/Johannesburg"))
        try {
            // 22:30 UTC on Monday 2 November is 00:30 on Tuesday 3 November in South Africa.
            val lateMonday = session(1, 4).copy(startTime = "2026-11-02T22:30:00.000Z", endTime = "2026-11-02T23:30:00.000Z")
            assertEquals(java.time.DayOfWeek.TUESDAY, lateMonday.toPendingBookingUi()!!.dayOfWeek)
            assertEquals(java.time.DayOfWeek.MONDAY, session(1, 4).toPendingBookingUi()!!.dayOfWeek)
        } finally {
            java.util.TimeZone.setDefault(saved)
        }
    }
}
