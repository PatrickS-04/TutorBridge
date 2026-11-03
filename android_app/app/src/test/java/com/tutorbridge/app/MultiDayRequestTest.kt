package com.tutorbridge.app

import com.google.gson.Gson
import com.tutorbridge.app.data.model.DirectBookingRequest
import com.tutorbridge.app.data.model.RepeatDto
import com.tutorbridge.app.data.model.SERIES_MAX
import com.tutorbridge.app.data.model.SERIES_MAX_SESSIONS
import com.tutorbridge.app.data.model.SERIES_MIN
import com.tutorbridge.app.data.model.maxWeeksFor
import com.tutorbridge.app.data.model.repeatFor
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What is sent to the server for one session, a weekly series, or the same time on several days. */
class MultiDayRequestTest {

    private fun json(repeat: RepeatDto?) =
        Gson().toJson(DirectBookingRequest(tutorId = "t1", startTime = "s", endTime = "e", repeat = repeat))

    @Test
    fun `a single weekday booked once sends no repeat`() {
        assertNull(repeatFor(weekly = false, weeks = 4, firstDay = MONDAY, extraDays = emptySet()))
    }

    @Test
    fun `one weekday repeated weekly is the original shape, a count`() {
        val repeat = repeatFor(weekly = true, weeks = 4, firstDay = MONDAY, extraDays = emptySet())!!
        assertEquals(4, repeat.count)
        assertNull(repeat.days)
        assertNull(repeat.weeks)
        val sent = json(repeat)
        assertTrue(sent.contains("\"count\":4"))
        assertFalse(sent.contains("days"))
    }

    @Test
    fun `several days repeated weekly is a pattern of days over weeks`() {
        val repeat = repeatFor(true, 4, MONDAY, setOf(TUESDAY, WEDNESDAY))!!
        assertEquals(listOf("MONDAY", "TUESDAY", "WEDNESDAY"), repeat.days)
        assertEquals(4, repeat.weeks)
        assertNull(repeat.count)
        val sent = json(repeat)
        assertTrue(sent.contains("\"days\":[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\"]"))
        assertTrue(sent.contains("\"weeks\":4"))
        assertFalse(sent.contains("count"))
    }

    @Test
    fun `several days without repeating is one week of those days`() {
        val repeat = repeatFor(false, 4, MONDAY, setOf(TUESDAY))!!
        assertEquals(listOf("MONDAY", "TUESDAY"), repeat.days)
        assertEquals(1, repeat.weeks)
    }

    @Test
    fun `days are sent in week order whatever order they were chosen in`() {
        val repeat = repeatFor(true, 2, WEDNESDAY, setOf(FRIDAY, MONDAY))!!
        assertEquals(listOf("MONDAY", "WEDNESDAY", "FRIDAY"), repeat.days)
    }

    @Test
    fun `the first day counts once even if it is also among the extra days`() {
        val repeat = repeatFor(true, 3, MONDAY, setOf(MONDAY, TUESDAY))!!
        assertEquals(listOf("MONDAY", "TUESDAY"), repeat.days)
    }

    @Test
    fun `weeks are kept between the smallest and largest the server accepts`() {
        assertEquals(SERIES_MIN, repeatFor(true, 0, MONDAY, setOf(TUESDAY))!!.weeks)
        assertEquals(SERIES_MAX, repeatFor(true, 99, MONDAY, setOf(TUESDAY))!!.weeks)
        assertEquals(SERIES_MIN, repeatFor(true, -3, MONDAY, emptySet())!!.count)
        assertEquals(SERIES_MAX, repeatFor(true, 99, MONDAY, emptySet())!!.count)
    }

    @Test
    fun `a pattern never passes 24 sessions, so more days means fewer weeks`() {
        val sevenDays = setOf(TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY)
        assertEquals(3, repeatFor(true, 8, MONDAY, sevenDays)!!.weeks) // 7 x 3 = 21
        assertEquals(6, repeatFor(true, 8, MONDAY, setOf(TUESDAY, WEDNESDAY, THURSDAY))!!.weeks) // 4 x 6 = 24
        assertEquals(8, repeatFor(true, 8, MONDAY, setOf(TUESDAY, WEDNESDAY))!!.weeks) // 3 x 8 = 24
    }

    @Test
    fun `the most weeks for each number of days keeps within 24 sessions`() {
        for (days in 1..7) {
            val weeks = maxWeeksFor(days)
            assertTrue("$days days x $weeks weeks", days * weeks <= SERIES_MAX_SESSIONS)
            assertTrue(weeks in 1..SERIES_MAX)
        }
        assertEquals(8, maxWeeksFor(1))
        assertEquals(8, maxWeeksFor(3))
        assertEquals(6, maxWeeksFor(4))
        assertEquals(4, maxWeeksFor(6))
        assertEquals(3, maxWeeksFor(7))
    }

    @Test
    fun `every pattern sent is a series the server will accept`() {
        val all = DayOfWeek.values().toList()
        for (size in 1..7) {
            for (weekly in listOf(true, false)) {
                val repeat = repeatFor(weekly, 8, MONDAY, all.drop(1).take(size - 1).toSet()) ?: continue
                val sessions = if (repeat.days != null) repeat.days!!.size * repeat.weeks!! else repeat.count!!
                assertTrue("size $size weekly $weekly gave $sessions sessions", sessions in SERIES_MIN..SERIES_MAX_SESSIONS)
            }
        }
    }
}
