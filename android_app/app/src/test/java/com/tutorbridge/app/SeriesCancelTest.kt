package com.tutorbridge.app

import com.google.gson.Gson
import com.tutorbridge.app.data.model.CancelSeriesRequest
import com.tutorbridge.app.data.model.SeriesActionDto
import com.tutorbridge.app.data.model.sessionsFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cancelling "this and following", and answering to the tutor's series confirm or decline. */
class SeriesCancelTest {

    @Test
    fun `cancelling from a session reaches it and every one after it`() {
        assertEquals(4, sessionsFrom(1, 4))
        assertEquals(3, sessionsFrom(2, 4))
        assertEquals(2, sessionsFrom(3, 4))
        assertEquals(1, sessionsFrom(4, 4))
    }

    @Test
    fun `the number is never below one or above the whole series`() {
        assertEquals(1, sessionsFrom(9, 4))
        assertEquals(4, sessionsFrom(0, 4))
        assertEquals(4, sessionsFrom(-2, 4))
        assertEquals(1, sessionsFrom(1, 0))
    }

    @Test
    fun `cancelling from a session sends that session's id, and cancelling it all sends none`() {
        val from = Gson().toJson(CancelSeriesRequest(reason = null, fromBookingId = "b3"))
        assertTrue(from.contains("\"fromBookingId\":\"b3\""))

        val all = Gson().toJson(CancelSeriesRequest(reason = "Off sick"))
        assertFalse(all.contains("fromBookingId"))
        assertTrue(all.contains("\"reason\":\"Off sick\""))
    }

    @Test
    fun `the answer to a series cancel says how many were cancelled`() {
        val json = "{\"seriesId\":\"s1\",\"cancelled\":3,\"cancelledBy\":\"u1\",\"reason\":null," +
            "\"bookings\":[{\"id\":\"b2\",\"status\":\"CANCELLED\",\"seriesIndex\":2}]}"
        val ack = Gson().fromJson(json, SeriesActionDto::class.java)
        assertEquals(3, ack.cancelled)
        assertNull(ack.confirmed)
        assertEquals(2, ack.bookings!!.single().seriesIndex)
    }

    @Test
    fun `the answer to a series confirm says how many were confirmed`() {
        val ack = Gson().fromJson("{\"seriesId\":\"s1\",\"confirmed\":4}", SeriesActionDto::class.java)
        assertEquals(4, ack.confirmed)
        assertNull(ack.cancelled)
        assertNull(ack.bookings)
    }
}
