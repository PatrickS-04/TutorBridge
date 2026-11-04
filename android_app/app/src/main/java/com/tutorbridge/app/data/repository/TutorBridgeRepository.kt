package com.tutorbridge.app.data.repository

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tutorbridge.app.Apiinterface.TutorBridgeApi
import com.tutorbridge.app.data.local.LocalCache
import com.tutorbridge.app.data.model.*
import retrofit2.Response
import java.io.IOException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// Reference: Android Developers. 2026. Guide to app architecture. [Online].
//   Available at: https://developer.android.com/topic/architecture [Accessed 21 September 2026].
/**
 * The one place backend documents become the `*Ui` models the screens render.
 *
 * Everything returns kotlin.Result so a failed call carries the server's
 * message up to the ViewModel instead of silently turning into an empty list.
 */
class TutorBridgeRepository(
    private val api: TutorBridgeApi,
    /** Where reads are remembered for offline use; null turns the cache off. */
    private val cache: LocalCache? = null
) {

    // ==================== PROFILE, SETTINGS, REFERENCE DATA ====================

    suspend fun me(): Result<UserDto> = cachedCall("me") { api.me() }
    suspend fun updateProfile(body: UpdateProfileRequest): Result<UserDto> = call { api.updateMe(body) }

    suspend fun settings(): Result<UserSettingsDto> = cachedCall("settings") { api.settings() }

    suspend fun saveSettings(body: UpdateSettingsRequest): Result<UserSettingsDto> =
        call { api.updateSettings(body) }

    suspend fun saveAvailability(availability: List<AvailabilityDto>): Result<UserDto> =
        call { api.updateMe(UpdateProfileRequest(availability = availability)) }

    suspend fun scheduleAccountDeletion(): Result<Map<String, Any>> = call { api.scheduleAccountDeletion() }

    suspend fun institution(): Result<InstitutionDto> = cachedCall("institution") { api.institution() }

    suspend fun referenceData(): Result<ReferenceDataDto> = cachedCall("reference") { api.referenceData() }

    // ==================== TUTORS ====================

    suspend fun tutors(): Result<List<TutorUi>> =
        // The server already sends best fit first; sorting again keeps that true
        // whatever order the page arrives in (the sort is stable, so ties stay by name).
        cachedCall("tutors") { api.tutors() }.map { page -> page.data.map { it.toTutorUi() }.sortedByDescending { it.matchPercent } }

    // ==================== TUITION REQUESTS ====================

    suspend fun myRequests(): Result<List<StudentRequestUi>> =
        cachedCall("my_requests") { api.tuitionRequests(mine = true) }.map { page -> page.data.map { it.toRequestUi() } }

    suspend fun openRequests(): Result<List<StudentRequestUi>> =
        cachedCall("open_requests") { api.tuitionRequests() }.map { page -> page.data.map { it.toRequestUi() } }

    suspend fun applicants(requestId: String): Result<List<TutorApplicant>> =
        cachedCall("applicants:$requestId") { api.requestApplications(requestId) }.map { page ->
            page.data.map { application ->
                TutorApplicant(
                    id = application._id,
                    tutorId = application.tutorId?._id.orEmpty(),
                    name = application.tutorId?.fullName ?: "Tutor",
                    initials = initialsOf(application.tutorId?.fullName),
                    verified = application.tutorId?.verificationStatus == "VERIFIED",
                    note = application.message.orEmpty()
                )
            }
        }

    suspend fun createRequest(body: CreateTuitionRequest): Result<Unit> =
        call { api.createTuitionRequest(body) }.map { }

    suspend fun updateRequest(requestId: String, body: UpdateTuitionRequest): Result<Unit> =
        call { api.updateTuitionRequest(requestId, body) }.map { }

    suspend fun closeRequest(requestId: String): Result<Unit> =
        call { api.closeTuitionRequest(requestId) }.map { }

    suspend fun withdrawRequest(requestId: String): Result<Unit> =
        call { api.withdrawTuitionRequest(requestId) }.map { }

    suspend fun applyToRequest(requestId: String, message: String?, proposedRate: Double): Result<Unit> =
        call { api.applyToRequest(requestId, ApplyRequest(message, proposedRate)) }.map { }

    suspend fun decideApplication(applicationId: String, accept: Boolean): Result<Unit> =
        call { api.decideApplication(applicationId, DecisionRequest(if (accept) "accept" else "reject")) }.map { }

    /** The tutor's own applications, shown on their requests board. */
    suspend fun myApplications(): Result<List<StudentRequestUi>> =
        cachedCall("my_applications") { api.myApplications() }.map { page ->
            page.data.mapNotNull { application ->
                application.tuitionRequestId?.toRequestUi()?.copy(
                    id = application._id,
                    status = when (application.status) {
                        "ACCEPTED" -> RequestStatus.MATCHED
                        "PENDING" -> RequestStatus.OFFERED
                        else -> RequestStatus.COMPLETED
                    },
                    timestamp = application.createdAt.orEmpty()
                )
            }
        }

    // ==================== BOOKINGS ====================

    suspend fun bookings(): Result<List<BookingDto>> = cachedCall("bookings") { api.bookings() }.map { it.data }

    suspend fun bookTutorDirectly(body: DirectBookingRequest): Result<BookingAckDto> =
        call { api.bookTutorDirectly(body) }

    suspend fun scheduleFromApplication(body: ApplicationBookingRequest): Result<BookingAckDto> =
        call { api.scheduleFromApplication(body) }

    suspend fun confirmBooking(id: String): Result<BookingAckDto> = call { api.confirmBooking(id) }

    suspend fun cancelBooking(id: String, reason: String?): Result<BookingAckDto> =
        call { api.cancelBooking(id, CancelRequest(reason)) }

    suspend fun confirmSeries(seriesId: String): Result<SeriesActionDto> = call { api.confirmSeries(seriesId) }

    suspend fun cancelSeries(seriesId: String, reason: String?, fromBookingId: String? = null): Result<SeriesActionDto> =
        call { api.cancelSeries(seriesId, CancelSeriesRequest(reason, fromBookingId)) }

    suspend fun rescheduleBooking(id: String, body: RescheduleRequest): Result<RescheduleAckDto> =
        call { api.rescheduleBooking(id, body) }

    /** Saves the tutor's checklist and side notes for one session, in one write. */
    suspend fun saveSessionNotes(
        bookingId: String,
        checklist: List<SessionGoalUi>,
        sideNotes: String
    ): Result<TutorNotesDto> = saveSessionNotes(bookingId, notesOf(checklist, sideNotes))

    /** The same save from the document itself - what the offline queue stores and replays. */
    suspend fun saveSessionNotes(bookingId: String, notes: TutorNotesDto): Result<TutorNotesDto> =
        call { api.saveSessionNotes(bookingId, notes) }

    suspend fun checkInCode(bookingId: String): Result<CheckInCodeDto> = call { api.checkInCode(bookingId) }

    suspend fun checkIn(bookingId: String, code: String, method: String): Result<CheckInAckDto> =
        call { api.checkIn(bookingId, CheckInRequest(code, method)) }

    // ==================== MESSAGING ====================

    suspend fun threads(selfId: String): Result<List<ThreadUi>> =
        cachedCall("threads") { api.threads() }.map { page -> page.data.map { it.toThreadUi(selfId) } }

    /** One page of a conversation, oldest first, with how many pages there are in all. */
    suspend fun messages(threadId: String, selfId: String, page: Int): Result<Pair<List<MessageUi>, Int>> =
        // Only the newest page is kept: older pages are history the user asked
        // for, and offline they just see the latest conversation, not a patchwork.
        (if (page == 1) cachedCall("messages:$threadId") { api.messages(threadId, page) } else call { api.messages(threadId, page) })
            .map { result ->
            result.data.map { it.toMessageUi(selfId) }.sortedBy { it.sentAt } to result.totalPages
        }

    suspend fun sendMessage(threadId: String, selfId: String, body: String, clientMessageId: String): Result<MessageUi> =
        call { api.sendMessage(threadId, SendMessageRequest(body, clientMessageId)) }
            .map { it.toMessageUi(selfId) }

    // ==================== NOTIFICATIONS ====================

    suspend fun notifications(): Result<List<NotificationUi>> =
        cachedCall("notifications") { api.notifications() }.map { page -> page.data.map { it.toNotificationUi() } }

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

    // ==================== GOOGLE CALENDAR ====================

    suspend fun calendarLink(): Result<CalendarLinkDto> = call { api.calendarLink() }

    suspend fun linkCalendar(authCode: String): Result<CalendarLinkDto> =
        call { api.linkCalendar(LinkCalendarRequest(authCode)) }

    // 204, no body - same reason as unregisterPushToken above.
    suspend fun unlinkCalendar(): Result<Unit> = try {
        val response = api.unlinkCalendar()
        if (response.isSuccessful) Result.success(Unit)
        else Result.failure(IllegalStateException(parseApiError(response)))
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    // ==================== CLAIMS ====================

    /** The last [months] months; the server returns every month, so quiet ones are dropped in the mapper. */
    suspend fun claims(institutionName: String, months: Int): Result<List<InstitutionUi>> =
        cachedCall("claims") { api.claimPeriods(months) }.map { claims -> listOf(claims.toInstitutionUi(institutionName)) }

    /** The server-built CSV for one month, [month] as `YYYY-MM`. */
    suspend fun claimCsv(month: String): Result<String> =
        call { api.claimCsv(month) }.mapCatching { it.string() }

    // ==================== PLUMBING ====================

    private suspend fun <T> call(block: suspend () -> Response<T>): Result<T> = try {
        val response = block()
        val body = response.body()
        if (response.isSuccessful && body != null) {
            Result.success(body)
        } else {
            val detail = parseApiErrorDetail(response)
            Result.failure(ApiException(response.code(), detail?.message ?: "Something went wrong (${response.code()})", detail?.code, detail?.dates.orEmpty()))
        }
    } catch (exception: Exception) {
        Result.failure(exception)
    }

    /**
     * [call] for a read the offline cache backs. A good answer is saved as the
     * latest copy. If the request fails because the connection is down - an
     * IOException: no route, timeout, offline mode forced - the last saved copy
     * is returned instead, so the screen has something to show.
     *
     * Only a dead connection falls back. When the server answered with an error
     * (401, 403, 404, 500...) that error is the truth and is passed on, because
     * showing old data in place of "you no longer have access" would be wrong.
     */
    private suspend inline fun <reified T> cachedCall(
        resource: String,
        crossinline block: suspend () -> Response<T>
    ): Result<T> {
        val result = call { block() }
        val store = cache ?: return result

        result.getOrNull()?.let { fresh ->
            store.put(resource, fresh)
            return result
        }
        if (result.exceptionOrNull() !is IOException) return result

        val saved = store.get<T>(resource, object : TypeToken<T>() {}.type) ?: return result
        Log.i(TAG, "Offline: served $resource from the cache saved at ${saved.updatedAtMillis}")
        return Result.success(saved.value)
    }

    private companion object {
        const val TAG = "Cache"
    }
}

/** The server answered, but with an error. Distinct from an IOException, which means it never did. */
class ApiException(
    val code: Int,
    message: String,
    val errorCode: String? = null,
    /** The start times (ISO-8601) a series error is about; empty for any other error. */
    val dates: List<String> = emptyList()
) : IllegalStateException(message)

// ==================== MAPPERS ====================

/** The checklist and side notes as the one document the server stores. */
fun notesOf(checklist: List<SessionGoalUi>, sideNotes: String) = TutorNotesDto(
    items = checklist.map { TutorNoteItemDto(text = it.text, done = it.completed) },
    text = sideNotes
)

// These are read each time rather than built once: a language switch does not restart the
// process, so a formatter fixed at start-up would keep the old language's month names.
private val displayTime get() = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

/**
 * The booking flow's slot labels double as the slot's identity (the picked label is parsed
 * back into a time), so they stay in one fixed format whatever the display language is.
 */
internal val slotTime: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
private val monthLabel get() = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

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
        matchPercent = matchScore.coerceIn(0, 100),
        activeNow = todaysWindows.any { now >= it.start && now < it.end },
        activeToday = todaysWindows.isNotEmpty(),
        staffOrTutorId = staffNumber.orEmpty(),
        verified = verificationStatus == "VERIFIED",
        department = department.orEmpty(),
        languages = listOf(Language.fromServerCode(locale).displayName),
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
        "CANCELLED" -> RequestStatus.WITHDRAWN
        else -> RequestStatus.OPEN
    },
    // Not shown anywhere: kept as the raw timestamp rather than as English words.
    timestamp = createdAt.orEmpty(),
    preferredVenue = areaId?.name.orEmpty(),
    matchedTutorName = matchedTutorName.orEmpty(),
    applicantCount = applicationCount,
    subjectId = subjectId?._id.orEmpty(),
    areaId = areaId?._id.orEmpty(),
    availability = availability
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
        startTime = start.format(displayTime),
        endTime = end?.format(displayTime).orEmpty(),
        type = CalendarEventType.LESSON,
        participantName = other?.fullName.orEmpty(),
        participantRole = if (selfIsTutor) "student" else "tutor",
        location = location.orEmpty(),
        status = when (status) {
            "COMPLETED" -> EventStatus.COMPLETED
            "CANCELLED" -> EventStatus.CANCELLED
            else -> EventStatus.CONFIRMED
        },
        forRole = "both",
        // The server replaces the whole list on every save, so a row has no id
        // of its own - the position it came back in is what the screen keys on.
        checklist = tutorNotes?.items.orEmpty().mapIndexed { index, item ->
            SessionGoalUi(id = "n$index", text = item.text, completed = item.done)
        },
        sideNotes = tutorNotes?.text.orEmpty(),
        startIso = startTime,
        endIso = endTime,
        isConfirmed = status == "CONFIRMED",
        isPending = status == "REQUESTED",
        checkedInTime = parseInstant(attendance?.checkedInAt)?.format(displayTime).orEmpty(),
        seriesId = seriesId,
        seriesIndex = seriesIndex ?: 0,
        seriesCount = seriesCount ?: 0
    )
}

// ==================== MESSAGING ====================

private val chatDay get() = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault())
private val chatToday get() = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

/** "14:05" for something said today, "3 Oct 14:05" otherwise. */
private fun chatTime(iso: String?): String {
    val moment = parseInstant(iso) ?: return ""
    return if (moment.toLocalDate() == LocalDate.now()) moment.format(chatToday) else moment.format(chatDay)
}

/** A thread from [selfId]'s side: the other participant is whoever is not them. */
fun ThreadDto.toThreadUi(selfId: String): ThreadUi {
    val other = participants.firstOrNull { it._id != selfId }
    return ThreadUi(
        id = _id,
        otherName = other?.fullName?.ifBlank { null } ?: "TutorBridge user",
        initials = initialsOf(other?.fullName),
        preview = lastMessagePreview.orEmpty(),
        time = chatTime(lastMessageAt),
        unread = unread
    )
}

fun MessageDto.toMessageUi(selfId: String): MessageUi = MessageUi(
    id = _id,
    body = body,
    mine = senderId == selfId,
    // The chat's day headers carry the date, so the bubble only needs the time.
    time = parseInstant(sentAt)?.format(chatToday).orEmpty(),
    sentAt = sentAt,
    // Gson leaves a field the JSON lacks as null whatever default Kotlin declares, and a
    // message saved by an older app version, or sent by an older server, has no clientMessageId.
    clientId = (clientMessageId as String?).orEmpty()
)

/** A REQUESTED booking waiting for the tutor to confirm or decline. */
fun BookingDto.toPendingBookingUi(): PendingBookingUi? {
    val start = parseInstant(startTime) ?: return null
    return PendingBookingUi(
        id = _id,
        studentName = studentId?.fullName ?: "Student",
        initials = initialsOf(studentId?.fullName),
        subjectName = subjectName,
        whenLabel = "${start.toLocalDate()} • ${start.format(displayTime)}",
        location = location.orEmpty(),
        seriesId = seriesId,
        seriesIndex = seriesIndex ?: 0,
        seriesCount = seriesCount ?: 0,
        dayOfWeek = start.dayOfWeek
    )
}

private val historyDate get() = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/**
 * The student's finished sessions, newest first, as the learning history lists them.
 * The summary is tutor, time and venue only - no words, so it reads the same in every language.
 */
fun List<BookingDto>.toLearningHistory(): List<LearningHistoryEntryUi> =
    filter { it.status == "COMPLETED" }
        .mapNotNull { booking -> parseInstant(booking.startTime)?.let { booking to it } }
        .sortedByDescending { (_, start) -> start }
        .map { (booking, start) ->
            val end = parseInstant(booking.endTime)
            LearningHistoryEntryUi(
                id = booking._id,
                title = booking.subjectName,
                date = start.format(historyDate),
                summary = listOfNotNull(
                    booking.tutorId?.fullName?.ifBlank { null },
                    end?.let { "${start.format(displayTime)} – ${it.format(displayTime)}" },
                    booking.location?.ifBlank { null }
                ).joinToString(" • ")
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
    createdAt = createdAt.orEmpty(),
    unread = readAt == null
)

fun TimesheetPeriodsDto.toInstitutionUi(institutionName: String): InstitutionUi = InstitutionUi(
    id = "claims",
    shortName = institutionName.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.joinToString(""),
    fullName = institutionName,
    faculty = department,
    staffId = staffNumber,
    rate = ratePerHour,
    // The API answers every month in the range; a month with no sessions has
    // nothing to claim, so only the current draft is kept regardless.
    claimPeriods = periods.filter { it.isCurrent || it.sessionCount > 0 }.map { period ->
        ClaimPeriodUi(
            id = period.id,
            label = runCatching { YearMonth.parse(period.month).format(monthLabel) }.getOrDefault(period.month),
            // No payments collection: the month in progress is the working
            // draft, anything older is closed off.
            status = if (period.isCurrent) ClaimStatus.ACTIVE_DRAFT else ClaimStatus.PAID,
            totalHours = period.totalHours,
            sessionsCount = period.sessionCount,
            verifiedCount = period.verifiedCount,
            claimValue = period.claimValue,
            sessions = period.sessions.map { session ->
                ClaimSessionUi(
                    id = session.id,
                    courseCode = session.subjectName,
                    date = session.date,
                    timeRange = listOfNotNull(
                        parseInstant(session.startTime)?.format(displayTime),
                        parseInstant(session.endTime)?.format(displayTime)
                    ).joinToString(" - "),
                    amount = "R${session.amount.toInt()}",
                    studentName = session.studentName,
                    studentIdTag = "",
                    topic = session.location,
                    hours = session.hours,
                    checkedIn = session.checkedIn
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

/**
 * "Mondays" in English, which pluralises its weekdays to say "every Monday". Afrikaans and isiZulu
 * do not, so there the day's name stands as it is.
 */
private fun everyWeekday(day: DayOfWeek): String {
    val locale = Locale.getDefault()
    val name = day.getDisplayName(java.time.format.TextStyle.FULL, locale)
    return if (locale.language == "en") name + "s" else name
}

/** "Mondays" / "09:00 – 17:00" rows for the availability screen. */
fun List<AvailabilityWindowUi>.toDayWindows(): List<DayWindowUi> = sortedBy { it.day.value }.map {
    DayWindowUi(
        day = everyWeekday(it.day),
        dayName = it.day.name,
        timeRange = "${it.start} – ${it.end}",
        active = true
    )
}

/** "Mon, Wed 14:00–17:00" - the free-text time window the request cards show. */
fun List<AvailabilityDto>.toTimeWindowLabel(): String {
    val windows = mapNotNull { it.toWindowUi() }
    if (windows.isEmpty()) return ""
    val days = windows.map { it.day.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) }
        .distinct()
        .joinToString(", ")
    val first = windows.first()
    return "$days ${first.start}–${first.end}"
}

/** Hourly starts the tutor is free on [date] that are still ahead of [now], for the booking flow. */
fun TutorUi.slotsOn(date: LocalDate, now: java.time.LocalDateTime = java.time.LocalDateTime.now()): List<String> {
    val windows = this.windows.filter { it.day == date.dayOfWeek }
    return windows.flatMap { window ->
        generateSequence(window.start) { it.plusHours(1) }
            .takeWhile { !it.plusHours(1).isAfter(window.end) }
            .filter { date.atTime(it).isAfter(now) }
            .map { it.format(slotTime) }
            .toList()
    }
}

// ==================== SHARED HELPERS ====================

// Reference: Google. 2024. Gson (Version 2.11.0). [Computer program].
//   Available at: https://github.com/google/gson [Accessed 21 September 2026].
internal fun <T> parseApiErrorDetail(response: Response<T>): ApiErrorDetail? = try {
    Gson().fromJson(response.errorBody()?.string(), ApiErrorBody::class.java)?.error
} catch (exception: Exception) {
    null
}

internal fun <T> parseApiError(response: Response<T>): String =
    parseApiErrorDetail(response)?.message ?: "Something went wrong (${response.code()})"

fun initialsOf(fullName: String?): String =
    fullName.orEmpty().split(" ").filter { it.isNotBlank() }.take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")

private fun parseInstant(iso: String?) = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault())
}.getOrNull()