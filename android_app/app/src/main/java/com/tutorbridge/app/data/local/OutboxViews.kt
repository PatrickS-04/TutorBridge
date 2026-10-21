package com.tutorbridge.app.data.local

import com.google.gson.Gson
import com.tutorbridge.app.data.model.MessageUi
import com.tutorbridge.app.data.model.NotificationPreferencesDto
import com.tutorbridge.app.data.model.RequestStatus
import com.tutorbridge.app.data.model.StudentRequestUi
import com.tutorbridge.app.data.model.TutorNotesDto
import com.tutorbridge.app.data.model.UpdateSettingsRequest
import com.tutorbridge.app.data.model.UserSettingsDto
import com.tutorbridge.app.data.repository.toTimeWindowLabel
import java.time.Instant

/**
 * What the outbox adds to the picture the screens draw. A queued item is not on
 * the server yet, so a fresh load would not show it - these put it back on top
 * of whatever the cache or the server said, so the user sees what they did.
 *
 * Only PENDING items: a FAILED one is not going anywhere until the user decides
 * what to do with it, so it is not drawn as if it were on its way.
 */

private val gson = Gson()

private fun List<OutboxEntity>.waiting(type: OutboxType) =
    filter { it.state == OutboxState.PENDING && it.type == type.name }

private inline fun <reified P> OutboxEntity.payloadOrNull(): P? =
    runCatching { gson.fromJson(payload, P::class.java) }.getOrNull()

/** Messages written but not yet sent, as bubbles for the open conversation. */
fun List<OutboxEntity>.pendingMessagesFor(threadId: String?): List<MessageUi> {
    if (threadId == null) return emptyList()
    return waiting(OutboxType.SEND_MESSAGE).mapNotNull { item ->
        val p = item.payloadOrNull<MessagePayload>() ?: return@mapNotNull null
        if (p.threadId != threadId) return@mapNotNull null
        MessageUi(
            // Same id the screen gave the bubble when it was written, so the two are one bubble.
            id = "local-${p.clientMessageId}",
            body = p.body,
            mine = true,
            time = "Waiting to send",
            sentAt = Instant.ofEpochMilli(item.createdAtMillis).toString(),
            clientId = p.clientMessageId,
            pending = true
        )
    }
}

/** Requests posted offline, as cards above the real ones. Newest first, like the board. */
fun List<OutboxEntity>.pendingRequestCards(): List<StudentRequestUi> =
    waiting(OutboxType.CREATE_REQUEST).mapNotNull { item ->
        val p = item.payloadOrNull<RequestPayload>() ?: return@mapNotNull null
        StudentRequestUi(
            id = "pending-${p.request.clientRequestId ?: item.id}",
            subject = p.subjectName,
            courseCode = "",
            topic = p.request.description.orEmpty().lineSequence().firstOrNull().orEmpty(),
            description = p.request.description.orEmpty(),
            level = p.request.gradeLevel.orEmpty(),
            timeWindow = p.request.availability.toTimeWindowLabel(),
            studentName = "",
            studentInitials = "",
            status = RequestStatus.OPEN,
            timestamp = "Waiting to sync",
            preferredVenue = p.areaName,
            pendingSync = true
        )
    }.reversed()

/** Notes saved offline, by booking. Saves of the same session are coalesced, so there is at most one each. */
fun List<OutboxEntity>.pendingNotesByBooking(): Map<String, TutorNotesDto> =
    waiting(OutboxType.SAVE_NOTES)
        .mapNotNull { item -> item.payloadOrNull<NotesPayload>() }
        .associate { it.bookingId to it.notes }

/**
 * Settings changed offline, folded into one request: each queued change in the
 * order it was made, a later value for a field replacing an earlier one.
 */
fun List<OutboxEntity>.pendingSettings(): UpdateSettingsRequest? {
    val changes = waiting(OutboxType.SAVE_SETTINGS).mapNotNull { it.payloadOrNull<SettingsPayload>()?.settings }
    if (changes.isEmpty()) return null
    return changes.reduce { merged, next ->
        UpdateSettingsRequest(
            language = next.language ?: merged.language,
            notifications = next.notifications ?: merged.notifications,
            smsFallback = next.smsFallback ?: merged.smsFallback,
            biometricEntry = next.biometricEntry ?: merged.biometricEntry,
            theme = next.theme ?: merged.theme
        )
    }
}

/** What the server last said, with the user's unsent changes on top - so a reload does not undo a toggle they just flipped. */
fun UserSettingsDto.withPending(pending: UpdateSettingsRequest?): UserSettingsDto {
    if (pending == null) return this
    return copy(
        language = pending.language ?: language,
        notifications = pending.notifications ?: notifications,
        smsFallback = pending.smsFallback ?: smsFallback,
        biometricEntry = pending.biometricEntry ?: biometricEntry,
        theme = pending.theme ?: theme
    )
}

fun List<OutboxEntity>.pendingCount(): Int = count { it.state == OutboxState.PENDING }

fun List<OutboxEntity>.failedCount(): Int = count { it.state == OutboxState.FAILED }
