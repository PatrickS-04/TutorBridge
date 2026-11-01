package com.tutorbridge.app

import com.tutorbridge.app.data.model.SERIES_MAX
import com.tutorbridge.app.data.model.sessionDates
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dates of a pattern over several days. These are the same cases the server tests (utils/series.test.ts), so the
 * two sides cannot quietly drift apart: what the phone previews is what the server books.
 */
class SessionDatesTest {

    private val monday = LocalDate.of(2026, 11, 2)

    private fun List<LocalDate>.text() = map { it.toString() }

    @Test
    fun `three days over four weeks is twelve dates in date order`() {
        val dates = sessionDates(monday, setOf(TUESDAY, WEDNESDAY), 4)

        assertEquals(12, dates.size)
        assertEquals(
            listOf("2026-11-02", "2026-11-03", "2026-11-04", "2026-11-09", "2026-11-10", "2026-11-11"),
            dates.take(6).text()
        )
        assertEquals(dates.sorted(), dates)
    }

    @Test
    fun `each chosen weekday comes once a week`() {
        val dates = sessionDates(monday, setOf(TUESDAY, WEDNESDAY), 4)
        for (day in listOf(MONDAY, TUESDAY, WEDNESDAY)) {
            assertEquals(day.name, 4, dates.count { it.dayOfWeek == day })
        }
    }

    @Test
    fun `starting mid-week counts each other day from the first date, so nothing is skipped`() {
        val wednesday = LocalDate.of(2026, 11, 4)
        val dates = sessionDates(wednesday, setOf(MONDAY, TUESDAY, WEDNESDAY), 2)

        assertEquals(
            listOf("2026-11-04", "2026-11-09", "2026-11-10", "2026-11-11", "2026-11-16", "2026-11-17"),
            dates.text()
        )
    }

    @Test
    fun `one day is the same as a plain weekly series`() {
        val weekly = listOf("2026-11-02", "2026-11-09", "2026-11-16", "2026-11-23")
        assertEquals(weekly, sessionDates(monday, emptySet(), 4).text())
        assertEquals(weekly, sessionDates(monday, setOf(MONDAY), 4).text())
    }

    @Test
    fun `a pattern carries on across a month and a year`() {
        val thursday = LocalDate.of(2026, 12, 24)
        assertEquals(
            listOf("2026-12-24", "2026-12-26", "2026-12-31", "2027-01-02"),
            sessionDates(thursday, setOf(THURSDAY, SATURDAY), 2).text()
        )
    }

    @Test
    fun `all seven days for one week is seven days in a row`() {
        val all = DayOfWeek.values().toSet()
        val dates = sessionDates(monday, all, 1)
        assertEquals(7, dates.size)
        dates.forEachIndexed { i, date -> assertEquals(monday.plusDays(i.toLong()), date) }
    }

    @Test
    fun `the first date is always included even if its weekday was not chosen`() {
        val dates = sessionDates(monday, setOf(FRIDAY), 1)
        assertEquals(listOf("2026-11-02", "2026-11-06"), dates.text())
    }

    @Test
    fun `a day listed twice or out of order gives the same dates`() {
        assertEquals(
            sessionDates(monday, setOf(MONDAY, WEDNESDAY), 2),
            sessionDates(monday, setOf(WEDNESDAY, MONDAY, WEDNESDAY), 2)
        )
    }

    @Test
    fun `the number of weeks is kept within what the server accepts`() {
        assertEquals(1, sessionDates(monday, setOf(TUESDAY), 0).count { it.dayOfWeek == TUESDAY })
        assertEquals(SERIES_MAX, sessionDates(monday, setOf(TUESDAY), 99).count { it.dayOfWeek == TUESDAY })
    }

    @Test
    fun `the largest pattern is 24 dates`() {
        assertEquals(24, sessionDates(monday, setOf(TUESDAY, WEDNESDAY), 8).size)
        assertTrue(sessionDates(monday, setOf(TUESDAY, WEDNESDAY, THURSDAY), 6).size == 24)
    }
}
