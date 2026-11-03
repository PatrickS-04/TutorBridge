package com.tutorbridge.app

import com.google.gson.Gson
import com.tutorbridge.app.data.model.ApiErrorBody
import com.tutorbridge.app.data.model.BookingAckDto
import com.tutorbridge.app.data.model.DirectBookingRequest
import com.tutorbridge.app.data.model.SERIES_DEFAULT
import com.tutorbridge.app.data.model.SERIES_MAX
import com.tutorbridge.app.data.model.SERIES_MIN
import com.tutorbridge.app.data.model.repeatFor
import java.time.DayOfWeek.MONDAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The limits, and how a request for a series and the server's answers to it are written. */
class SeriesDatesTest {

    @Test
    fun `the limits match the server and a month is the default`() {
        assertEquals(2, SERIES_MIN)
        assertEquals(8, SERIES_MAX)
        assertEquals(4, SERIES_DEFAULT)
    }

    @Test
    fun `a single booking is sent without a repeat and a series with one`() {
        val single = Gson().toJson(DirectBookingRequest(tutorId = "t1", startTime = "s", endTime = "e"))
        assertFalse(single.contains("repeat"))

        val series = Gson().toJson(
            DirectBookingRequest(
                tutorId = "t1", startTime = "s", endTime = "e",
                repeat = repeatFor(true, 4, MONDAY, emptySet())
            )
        )
        assertTrue(series.contains("\"repeat\":{\"frequency\":\"WEEKLY\",\"count\":4}"))
    }

    @Test
    fun `the answer to a single booking reads without series fields`() {
        val ack = Gson().fromJson("{\"id\":\"b1\",\"status\":\"REQUESTED\"}", BookingAckDto::class.java)
        assertEquals("b1", ack.id)
        assertNull(ack.seriesId)
        assertNull(ack.seriesCount)
        assertNull(ack.bookings)
    }

    @Test
    fun `the answer to a series lists every session in order`() {
        val json = """{"id":"b1","status":"REQUESTED","seriesId":"s1","seriesCount":3,
            "bookings":[{"id":"b1","status":"REQUESTED","seriesIndex":1},
                        {"id":"b2","status":"REQUESTED","seriesIndex":2},
                        {"id":"b3","status":"REQUESTED","seriesIndex":3}]}"""
        val ack = Gson().fromJson(json, BookingAckDto::class.java)
        assertEquals("s1", ack.seriesId)
        assertEquals(3, ack.seriesCount)
        assertEquals(listOf(1, 2, 3), ack.bookings!!.map { it.seriesIndex })
    }

    @Test
    fun `a series error carries the weeks it is about`() {
        val json = """{"error":{"code":"SERIES_CONFLICT","message":"m","field":"startTime",
            "dates":["2026-11-16T12:00:00.000Z","2026-11-30T12:00:00.000Z"]}}"""
        val detail = Gson().fromJson(json, ApiErrorBody::class.java).error!!
        assertEquals("SERIES_CONFLICT", detail.code)
        assertEquals(2, detail.dates!!.size)
    }

    @Test
    fun `an ordinary error has no dates`() {
        val json = "{\"error\":{\"code\":\"NOT_FOUND\",\"message\":\"m\"}}"
        val detail = Gson().fromJson(json, ApiErrorBody::class.java).error!!
        assertNull(detail.dates)
    }
}
