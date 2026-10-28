package com.tutorbridge.app

import com.tutorbridge.app.data.model.*
import com.tutorbridge.app.data.repository.slotsOn
import com.tutorbridge.app.data.repository.toCalendarEvent
import com.tutorbridge.app.data.repository.toRequestUi
import com.tutorbridge.app.data.repository.toInstitutionUi
import com.tutorbridge.app.data.repository.toTutorUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// Reference: Android Developers. 2026. Build local unit tests. [Online].
//   Available at: https://developer.android.com/training/testing/local-tests [Accessed 21 September 2026].
/**
 * Covers the mapping between backend documents and what the screens draw -
 * the part that fails quietly (wrong day, no slots, blank name) rather than
 * crashing.
 */
class MappingTest {

    // Day and month names follow the app's language; pin it so the expected text does not depend on the machine running the test.
    @org.junit.Before
    fun useEnglish() = java.util.Locale.setDefault(java.util.Locale.ENGLISH)

    private val monday = LocalDate.of(2026, 9, 21) // a Monday

    private val tutorDto = UserDto(
        _id = "t1",
        fullName = "Thabo Mokoena",
        email = "thabo@uct.ac.za",
        role = "TUTOR",
        hourlyRate = 220.0,
        subjectIds = listOf(SubjectDto("s1", "Mathematics", "mam1000w")),
        areaId = AreaDto("a1", "Upper Campus"),
        staffNumber = "TUT-UCT-2024-089",
        availability = listOf(AvailabilityDto("MONDAY", "09:00", "12:00")),
        verificationStatus = "VERIFIED",
        completedSessions = 7
    )

    @Test
    fun `tutor maps onto the card the find screen draws`() {
        val tutor = tutorDto.toTutorUi()

        assertEquals("Thabo Mokoena", tutor.name)
        assertEquals("TM", tutor.initials)
        assertEquals("Mathematics", tutor.subject)
        assertEquals("MAM1000W", tutor.courseCode)
        assertEquals("R 220/hr", tutor.hourlyRate)
        assertEquals(7, tutor.totalConsultations)
        assertTrue(tutor.verified)
        // Nothing fabricated: no reviews behind this tutor, so no rating.
        assertEquals(0, tutor.reviewsCount)
    }

    @Test
    fun `booking slots come from the tutor's own availability`() {
        val tutor = tutorDto.toTutorUi()

        // 09:00-12:00 is three whole hours, and none of them spill past the window.
        assertEquals(listOf("09:00 AM", "10:00 AM", "11:00 AM"), tutor.slotsOn(monday))
        assertEquals(emptyList<String>(), tutor.slotsOn(monday.plusDays(1)))
    }

    @Test
    fun `a booking becomes a calendar event for whoever is not you`() {
        val booking = BookingDto(
            _id = "b1",
            tutorId = UserBriefDto("t1", "Thabo Mokoena"),
            studentId = UserBriefDto("u1", "Zola Khumalo"),
            subjectName = "Mathematics",
            startTime = "2026-09-21T08:30:00.000Z",
            endTime = "2026-09-21T09:30:00.000Z",
            location = "Upper Campus",
            status = "CONFIRMED"
        )

        val forStudent = booking.toCalendarEvent(selfIsTutor = false)!!
        assertEquals("Thabo Mokoena", forStudent.participantName)
        assertEquals(EventStatus.CONFIRMED, forStudent.status)
        assertTrue(forStudent.startTime.matches(Regex("""\d\d:\d\d [AP]M""")))

        val forTutor = booking.toCalendarEvent(selfIsTutor = true)!!
        assertEquals("Zola Khumalo", forTutor.participantName)

        // A booking with an unreadable time is dropped, not rendered blank.
        assertEquals(null, booking.copy(startTime = "").toCalendarEvent(selfIsTutor = false))
    }

    @Test
    fun `a session carries the tutor's notes, and nothing when the server sends none`() {
        val booking = BookingDto(
            _id = "b1",
            tutorId = UserBriefDto("t1", "Thabo Mokoena"),
            studentId = UserBriefDto("u1", "Zola Khumalo"),
            subjectName = "Mathematics",
            startTime = "2026-09-21T08:30:00.000Z",
            endTime = "2026-09-21T09:30:00.000Z",
            status = "CONFIRMED",
            tutorNotes = TutorNotesDto(
                items = listOf(
                    TutorNoteItemDto("Mark the problem set", done = true),
                    TutorNoteItemDto("Set homework")
                ),
                text = "Guardian wants a progress update."
            )
        )

        val forTutor = booking.toCalendarEvent(selfIsTutor = true)!!
        assertEquals(2, forTutor.checklist.size)
        assertEquals("Mark the problem set", forTutor.checklist[0].text)
        assertTrue(forTutor.checklist[0].completed)
        assertTrue(!forTutor.checklist[1].completed)
        assertEquals("Guardian wants a progress update.", forTutor.sideNotes)
        // Ids are positional, so two rows never collide in the list.
        assertEquals(2, forTutor.checklist.map { it.id }.toSet().size)

        // The student's own list never carries them, so the screen sees empty.
        val forStudent = booking.copy(tutorNotes = null).toCalendarEvent(selfIsTutor = false)!!
        assertTrue(forStudent.checklist.isEmpty())
        assertEquals("", forStudent.sideNotes)
    }

    @Test
    fun `a tuition request keeps its subject, venue and match`() {
        val request = TuitionRequestDto(
            _id = "r1",
            subjectId = SubjectDto("s1", "Mathematics", "mam1000w"),
            areaId = AreaDto("a1", "Upper Campus"),
            studentId = UserBriefDto("u1", "Zola Khumalo"),
            gradeLevel = "First year",
            description = "Limits and derivatives",
            availability = listOf(AvailabilityDto("WEDNESDAY", "14:00", "17:00")),
            status = "MATCHED",
            applicationCount = 2,
            matchedTutorName = "Thabo Mokoena"
        ).toRequestUi()

        assertEquals(RequestStatus.MATCHED, request.status)
        assertEquals("Upper Campus", request.preferredVenue)
        assertEquals("ZK", request.studentInitials)
        assertEquals("Thabo Mokoena", request.matchedTutorName)
        assertEquals(2, request.applicantCount)
        assertEquals("Wed 14:00–17:00", request.timeWindow)
    }

    @Test
    fun `a request keeps the ids and hours the edit form is prefilled from`() {
        val request = TuitionRequestDto(
            _id = "r1",
            subjectId = SubjectDto("s1", "Mathematics"),
            areaId = AreaDto("a1", "Upper Campus"),
            availability = listOf(AvailabilityDto("WEDNESDAY", "14:00", "17:00"))
        ).toRequestUi()

        assertEquals("s1", request.subjectId)
        assertEquals("a1", request.areaId)
        assertEquals("14:00", request.availability.first().startTime)
    }

    @Test
    fun `a cancelled request reads as withdrawn, a closed one as closed`() {
        assertEquals(RequestStatus.WITHDRAWN, TuitionRequestDto(_id = "r1", status = "CANCELLED").toRequestUi().status)
        assertEquals(RequestStatus.COMPLETED, TuitionRequestDto(_id = "r2", status = "CLOSED").toRequestUi().status)
        assertEquals(RequestStatus.OPEN, TuitionRequestDto(_id = "r3", status = "OPEN").toRequestUi().status)
    }

    @Test
    fun `claim history drops months with nothing to claim but keeps the current draft`() {
        val claims = TimesheetPeriodsDto(
            periods = listOf(
                TimesheetPeriodDto(id = "2026-10", month = "2026-10", isCurrent = true, sessionCount = 0),
                TimesheetPeriodDto(id = "2026-09", month = "2026-09", sessionCount = 4),
                TimesheetPeriodDto(id = "2026-08", month = "2026-08", sessionCount = 0),
                TimesheetPeriodDto(id = "2026-07", month = "2026-07", sessionCount = 2)
            )
        ).toInstitutionUi("Test University")

        assertEquals(listOf("2026-10", "2026-09", "2026-07"), claims.claimPeriods.map { it.id })
    }
}
