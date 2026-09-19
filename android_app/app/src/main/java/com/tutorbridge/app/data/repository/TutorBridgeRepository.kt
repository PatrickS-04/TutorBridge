package com.tutorbridge.app.data.repository

import com.google.gson.Gson
import com.tutorbridge.app.Apiinterface.TutorBridgeApi
import com.tutorbridge.app.data.model.*
import retrofit2.Response
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The one place backend documents become the `*Ui` models the screens render.
 *
 * Everything returns kotlin.Result so a failed call carries the server's
 * message up to the ViewModel instead of silently turning into an empty list.
 */
class TutorBridgeRepository(private val api: TutorBridgeApi) {

    // ==================== PROFILE, SETTINGS, REFERENCE DATA ====================

    suspend fun me(): Result<UserDto> = call { api.me() }

    suspend fun settings(): Result<UserSettingsDto> = call { api.settings() }

    suspend fun saveSettings(body: UpdateSettingsRequest): Result<UserSettingsDto> =
        call { api.updateSettings(body) }

    suspend fun saveAvailability(availability: List<AvailabilityDto>): Result<UserDto> =
        call { api.updateMe(UpdateProfileRequest(availability = availability)) }

    suspend fun scheduleAccountDeletion(): Result<Map<String, Any>> = call { api.scheduleAccountDeletion() }

    suspend fun institution(): Result<InstitutionDto> = call { api.institution() }

    suspend fun referenceData(): Result<ReferenceDataDto> = call { api.referenceData() }

    // ==================== TUTORS ====================

    suspend fun tutors(): Result<List<TutorUi>> =
        call { api.tutors() }.map { page -> page.data.map { it.toTutorUi() } }

    // ==================== TUITION REQUESTS ====================

    suspend fun myRequests(): Result<List<StudentRequestUi>> =
        call { api.tuitionRequests(mine = true) }.map { page -> page.data.map { it.toRequestUi() } }

    suspend fun openRequests(): Result<List<StudentRequestUi>> =
        call { api.tuitionRequests() }.map { page -> page.data.map { it.toRequestUi() } }

    suspend fun applicants(requestId: String): Result<List<TutorApplicant>> =
        call { api.requestApplications(requestId) }.map { page ->
            page.data.map { application ->
                TutorApplicant(
                    id = application._id,
                    name = application.tutorId?.fullName ?: "Tutor",
                    initials = initialsOf(application.tutorId?.fullName),
                    verified = application.tutorId?.verificationStatus == "VERIFIED",
                    note = application.message.orEmpty()
                )
            }
        }

    suspend fun createRequest(body: CreateTuitionRequest): Result<Unit> =
        call { api.createTuitionRequest(body) }.map { }

    suspend fun applyToRequest(requestId: String, message: String?, proposedRate: Double): Result<Unit> =
        call { api.applyToRequest(requestId, ApplyRequest(message, proposedRate)) }.map { }

    suspend fun decideApplication(applicationId: String, accept: Boolean): Result<Unit> =
        call { api.decideApplication(applicationId, DecisionRequest(if (accept) "accept" else "reject")) }.map { }

    /** The tutor's own applications, shown on their requests board. */
    suspend fun myApplications(): Result<List<StudentRequestUi>> =
        call { api.myApplications() }.map { page ->
            page.data.mapNotNull { application ->
                application.tuitionRequestId?.toRequestUi()?.copy(
                    id = application._id,
                    status = when (application.status) {
                        "ACCEPTED" -> RequestStatus.MATCHED
                        "PENDING" -> RequestStatus.OFFERED
                        else -> RequestStatus.COMPLETED
                    },
                    timestamp = relativeTime(application.createdAt)
                )
            }
        }

    // ==================== BOOKINGS ====================

    suspend fun bookings(): Result<List<BookingDto>> = call { api.bookings() }.map { it.data }

    suspend fun bookTutorDirectly(body: DirectBookingRequest): Result<BookingAckDto> =
        call { api.bookTutorDirectly(body) }

    suspend fun confirmBooking(id: String): Result<BookingAckDto> = call { api.confirmBooking(id) }

    suspend fun cancelBooking(id: String, reason: String?): Result<BookingAckDto> =
        call { api.cancelBooking(id, CancelRequest(reason)) }

    // ==================== NOTIFICATIONS ====================

    suspend fun notifications(): Result<List<NotificationUi>> =
        call { api.notifications() }.map { page -> page.data.map { it.toNotificationUi() } }

    suspend fun markNotificationRead(id: String): Result<Unit> = call { api.markNotificationRead(id) }.map { }

    suspend fun registerPushToken(token: String): Result<Unit> =
        call { api.registerPushToken(RegisterPushTokenRequest(token)) }.map { }

    // DELETE answers 204 with no body, which `call` treats as a failure, so check isSuccessful directly.
    suspend fun unregisterPushToken(token: String): Result<Unit> = try {
        val response = api.unregisterPushToken(token)
        if (response.isSuccessful) Result.success(Unit)
        else Result.failure(IllegalStateException(parseApiError(response)))
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    // ==================== CLAIMS ====================

    suspend fun claims(institutionName: String): Result<List<InstitutionUi>> =
        call { api.claimPeriods() }.map { claims -> listOf(claims.toInstitutionUi(institutionName)) }

    // ==================== PLUMBING ====================

    private suspend fun <T> call(block: suspend () -> Response<T>): Result<T> = try {
        val response = block()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            Result.failure(IllegalStateException(parseApiError(response)))
        }
    } catch (exception: Exception) {
        Result.failure(exception)
    }
}

// ==================== MAPPERS ====================

private val timeOfDay = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
private val monthLabel = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

fun UserDto.toTutorUi(): TutorUi {
    val windows = availability.mapNotNull { it.toWindowUi() }
    val today = LocalDate.now().dayOfWeek
    val now = LocalTime.now()
    val todaysWindows = windows.filter { it.day == today }

    return TutorUi(
        id = _id,
        name = fullName,
        initials = initialsOf(fullName),
        subject = subjectIds.joinToString(" & ") { it.name }.ifBlank { "General tutoring" },
        courseCode = subjectIds.firstOrNull()?.slug?.uppercase(Locale.ENGLISH).orEmpty(),
        // No reviews collection exists yet, so the screens hide the rating
        // until there is something real to show (reviewsCount > 0).
        rating = 0.0,
        reviewsCount = 0,
        hourlyRate = hourlyRate?.let { "R ${it.toInt()}/hr" } ?: "Rate on request",
        campus = areaId?.name.orEmpty(),
        availableSlots = emptyList(), // filled per selected date - see slotsOn()
        bio = bio.orEmpty(),
        tagList = subjectIds.map { it.name },
        matchPercent = 0,
        activeNow = todaysWindows.any { now >= it.start && now < it.end },
        activeToday = todaysWindows.isNotEmpty(),
        staffOrTutorId = staffNumber.orEmpty(),
        verified = verificationStatus == "VERIFIED",
        department = department.orEmpty(),
        languages = listOf(languageName(locale)),
        totalConsultations = completedSessions,
        consultationVenue = areaId?.name.orEmpty(),
        windows = windows
    )
}

fun TuitionRequestDto.toRequestUi(): StudentRequestUi = StudentRequestUi(
    id = _id,
    subject = subjectId?.name ?: "Tuition request",
    courseCode = subjectId?.slug?.uppercase(Locale.ENGLISH).orEmpty(),
    topic = description.orEmpty().lineSequence().firstOrNull().orEmpty(),
    description = description.orEmpty(),
    level = gradeLevel.orEmpty(),
    timeWindow = availability.toTimeWindowLabel(),
    studentName = studentId?.fullName ?: "",
    studentInitials = initialsOf(studentId?.fullName),
    status = when (status) {
        "MATCHED" -> RequestStatus.MATCHED
        "CLOSED" -> RequestStatus.COMPLETED
        else -> RequestStatus.OPEN
    },
    timestamp = relativeTime(createdAt),
    preferredVenue = areaId?.name.orEmpty(),
    matchedTutorName = matchedTutorName.orEmpty(),
    applicantCount = applicationCount
)

/** A booking as the calendar renders it - [selfIsTutor] decides whose name is shown. */
fun BookingDto.toCalendarEvent(selfIsTutor: Boolean): CalendarEventUi? {
    val start = parseInstant(startTime) ?: return null
    val end = parseInstant(endTime)
    val other = if (selfIsTutor) studentId else tutorId

    return CalendarEventUi(
        id = _id,
        title = "$subjectName with ${other?.fullName ?: "TutorBridge"}",
        subject = subjectName,
        date = start.toLocalDate().toString(),
        startTime = start.format(timeOfDay),
        endTime = end?.format(timeOfDay).orEmpty(),
        type = CalendarEventType.LESSON,
        participantName = other?.fullName.orEmpty(),
        participantRole = if (selfIsTutor) "student" else "tutor",
        location = location.orEmpty(),
        status = when (status) {
            "COMPLETED" -> EventStatus.COMPLETED
            "CANCELLED" -> EventStatus.CANCELLED
            else -> EventStatus.CONFIRMED
        },
        forRole = "both"
    )
}

/** A REQUESTED booking waiting for the tutor to confirm or decline. */
fun BookingDto.toPendingBookingUi(): PendingBookingUi? {
    val start = parseInstant(startTime) ?: return null
    return PendingBookingUi(
        id = _id,
        studentName = studentId?.fullName ?: "Student",
        initials = initialsOf(studentId?.fullName),
        subjectName = subjectName,
        whenLabel = "${start.toLocalDate()} • ${start.format(timeOfDay)}",
        location = location.orEmpty()
    )
}

fun NotificationEventDto.toNotificationUi(): NotificationUi = NotificationUi(
    id = _id,
    emoji = when (category) {
        "NEW_LISTING" -> "📝"
        "APPLICATION" -> "📨"
        "MESSAGE" -> "💬"
        "VERIFICATION" -> "🛡️"
        else -> "📅"
    },
    title = title,
    body = body,
    time = relativeTime(createdAt),
    unread = readAt == null
)

fun TimesheetPeriodsDto.toInstitutionUi(institutionName: String): InstitutionUi = InstitutionUi(
    id = "claims",
    shortName = institutionName.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.joinToString(""),
    fullName = institutionName,
    faculty = department,
    staffId = staffNumber,
    rate = ratePerHour,
    claimPeriods = periods.map { period ->
        ClaimPeriodUi(
            id = period.id,
            label = runCatching { YearMonth.parse(period.month).format(monthLabel) }.getOrDefault(period.month),
            // No payments collection: the month in progress is the working
            // draft, anything older is closed off.
            status = if (period.isCurrent) ClaimStatus.ACTIVE_DRAFT else ClaimStatus.PAID,
            totalHours = period.totalHours,
            sessionsCount = period.sessionCount,
            claimValue = period.claimValue,
            sessions = period.sessions.map { session ->
                ClaimSessionUi(
                    id = session.id,
                    courseCode = session.subjectName,
                    date = session.date,
                    timeRange = listOfNotNull(
                        parseInstant(session.startTime)?.format(timeOfDay),
                        parseInstant(session.endTime)?.format(timeOfDay)
                    ).joinToString(" - "),
                    amount = "R${session.amount.toInt()}",
                    studentName = session.studentName,
                    studentIdTag = "",
                    topic = session.location,
                    hours = session.hours
                )
            }
        )
    }
)

// ==================== AVAILABILITY ====================

fun AvailabilityDto.toWindowUi(): AvailabilityWindowUi? = runCatching {
    AvailabilityWindowUi(DayOfWeek.valueOf(day), LocalTime.parse(startTime), LocalTime.parse(endTime))
}.getOrNull()

fun AvailabilityWindowUi.toDto(): AvailabilityDto =
    AvailabilityDto(day.name, start.toString(), end.toString())

/** "Mondays" / "09:00 – 17:00" rows for the availability screen. */
fun List<AvailabilityWindowUi>.toDayWindows(): List<DayWindowUi> = sortedBy { it.day.value }.map {
    DayWindowUi(
        day = it.day.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + "s",
        timeRange = "${it.start} – ${it.end}",
        active = true
    )
}

/** "Mon, Wed 14:00–17:00" - the free-text time window the request cards show. */
fun List<AvailabilityDto>.toTimeWindowLabel(): String {
    val windows = mapNotNull { it.toWindowUi() }
    if (windows.isEmpty()) return ""
    val days = windows.map { it.day.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) }
        .distinct()
        .joinToString(", ")
    val first = windows.first()
    return "$days ${first.start}–${first.end}"
}

/** Hourly starts the tutor is free on [date], for the booking flow. */
fun TutorUi.slotsOn(date: LocalDate): List<String> {
    val windows = this.windows.filter { it.day == date.dayOfWeek }
    return windows.flatMap { window ->
        generateSequence(window.start) { it.plusHours(1) }
            .takeWhile { !it.plusHours(1).isAfter(window.end) }
            .map { it.format(timeOfDay) }
            .toList()
    }
}

// ==================== SHARED HELPERS ====================

internal fun <T> parseApiError(response: Response<T>): String {
    val raw = response.errorBody()?.string()
    return try {
        Gson().fromJson(raw, ApiErrorBody::class.java)?.error?.message
            ?: "Something went wrong (${response.code()})"
    } catch (exception: Exception) {
        "Something went wrong (${response.code()})"
    }
}

fun initialsOf(fullName: String?): String =
    fullName.orEmpty().split(" ").filter { it.isNotBlank() }.take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")

private fun languageName(locale: String): String = when (locale) {
    "ZU" -> "isiZulu"
    "AF" -> "Afrikaans"
    else -> "English"
}

private fun parseInstant(iso: String?) = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault())
}.getOrNull()

private fun relativeTime(iso: String?): String {
    val moment = parseInstant(iso) ?: return ""
    val minutes = ChronoUnit.MINUTES.between(moment, java.time.ZonedDateTime.now())
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 48 -> "Yesterday"
        else -> "${minutes / (60 * 24)} days ago"
    }
}
