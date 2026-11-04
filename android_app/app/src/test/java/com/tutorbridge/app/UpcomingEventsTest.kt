package com.tutorbridge.app

import com.tutorbridge.app.data.model.CalendarEventType
import com.tutorbridge.app.data.model.CalendarEventUi
import com.tutorbridge.app.data.model.EventStatus
import com.tutorbridge.app.viewmodel.upcomingOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** The home screens' "coming up" list: finished sessions must not show there. */
class UpcomingEventsTest {

    private val now = Instant.parse("2026-11-01T12:00:00Z")

    private fun event(id: String, start: String, end: String, status: EventStatus = EventStatus.CONFIRMED) = CalendarEventUi(
        id = id, title = "", subject = "", date = "", startTime = "", endTime = "",
        type = CalendarEventType.LESSON, participantName = "", participantRole = "", location = "",
        status = status, forRole = "both", startIso = start, endIso = end
    )

    @Test
    fun `only sessions not yet over, soonest first`() {
        val upcoming = upcomingOf(
            listOf(
                event("later", "2026-11-20T09:00:00Z", "2026-11-20T10:00:00Z"),
                event("ended", "2026-09-15T10:00:00Z", "2026-09-15T11:00:00Z"),
                event("completed", "2026-11-05T09:00:00Z", "2026-11-05T10:00:00Z", EventStatus.COMPLETED),
                event("under way", "2026-11-01T11:30:00Z", "2026-11-01T12:30:00Z"),
                event("soon", "2026-11-03T09:00:00Z", "2026-11-03T10:00:00Z")
            ),
            now
        )

        assertEquals(listOf("under way", "soon", "later"), upcoming.map { it.id })
    }
}
