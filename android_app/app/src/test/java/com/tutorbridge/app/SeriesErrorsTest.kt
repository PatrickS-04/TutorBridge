package com.tutorbridge.app

import com.tutorbridge.app.data.model.errorMessageRes
import com.tutorbridge.app.data.model.formatSeriesDates
import com.tutorbridge.app.data.model.seriesErrorMessageRes
import com.tutorbridge.app.data.repository.ApiException
import com.tutorbridge.app.data.repository.parseApiErrorDetail
import java.time.ZoneId
import java.util.Locale
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/** What a person is told when a weekly series cannot be booked or confirmed. */
class SeriesErrorsTest {

    private val johannesburg = ZoneId.of("Africa/Johannesburg")

    @Test
    fun `the two series errors have their own words, which list weeks`() {
        assertNotNull(seriesErrorMessageRes("SERIES_CONFLICT"))
        assertNotNull(seriesErrorMessageRes("OUTSIDE_AVAILABILITY"))
        assertTrue(seriesErrorMessageRes("SERIES_CONFLICT") != seriesErrorMessageRes("OUTSIDE_AVAILABILITY"))
    }

    @Test
    fun `every other error is left to the usual mapping`() {
        listOf("SLOT_CONFLICT", "NOT_FOUND", "VALIDATION_ERROR", "INVALID_STATE", null)
            .forEach { assertNull(it, seriesErrorMessageRes(it)) }
    }

    @Test
    fun `the series errors are not also in the plain mapping, which has no weeks to fill in`() {
        assertNull(errorMessageRes("SERIES_CONFLICT"))
        assertNull(errorMessageRes("OUTSIDE_AVAILABILITY"))
    }

    @Test
    fun `weeks are listed as short dates in the order sent`() {
        val text = formatSeriesDates(
            listOf("2026-11-16T12:00:00.000Z", "2026-11-30T12:00:00.000Z"),
            johannesburg,
            Locale.ENGLISH
        )
        assertEquals("Mon 16 Nov, Mon 30 Nov", text)
    }

    @Test
    fun `a time late in the evening UTC is shown on the South African day it falls in`() {
        // 23:00 UTC on Sunday 15 November is 01:00 on Monday 16 November in South Africa.
        assertEquals(
            "Mon 16 Nov",
            formatSeriesDates(listOf("2026-11-15T23:00:00.000Z"), johannesburg, Locale.ENGLISH)
        )
    }

    @Test
    fun `dates follow the language they are shown in`() {
        val english = formatSeriesDates(listOf("2026-11-16T12:00:00.000Z"), johannesburg, Locale.ENGLISH)
        val afrikaans = formatSeriesDates(listOf("2026-11-16T12:00:00.000Z"), johannesburg, Locale("af"))
        assertEquals("Mon 16 Nov", english)
        assertTrue("expected an Afrikaans day name, got $afrikaans", afrikaans != english && afrikaans.contains("16"))
    }

    @Test
    fun `a time that cannot be read is skipped, not shown as an error`() {
        assertEquals(
            "Mon 16 Nov",
            formatSeriesDates(listOf("not a time", "2026-11-16T12:00:00.000Z", ""), johannesburg, Locale.ENGLISH)
        )
        assertEquals("", formatSeriesDates(emptyList(), johannesburg, Locale.ENGLISH))
    }

    @Test
    fun `the weeks in a server error are read out of its body`() {
        val json = """{"error":{"code":"SERIES_CONFLICT","message":"m","field":"startTime",
            "dates":["2026-11-16T12:00:00.000Z","2026-11-30T12:00:00.000Z"]}}"""
        val response = Response.error<Any>(409, json.toResponseBody("application/json".toMediaType()))

        val detail = parseApiErrorDetail(response)!!

        assertEquals("SERIES_CONFLICT", detail.code)
        assertEquals(listOf("2026-11-16T12:00:00.000Z", "2026-11-30T12:00:00.000Z"), detail.dates)
    }

    @Test
    fun `an api exception carries the weeks, and an ordinary one has none`() {
        val series = ApiException(409, "m", "SERIES_CONFLICT", listOf("2026-11-16T12:00:00.000Z"))
        assertEquals(1, series.dates.size)
        assertTrue(ApiException(404, "m", "NOT_FOUND").dates.isEmpty())
        assertTrue(ApiException(500, "m").dates.isEmpty())
    }
}
