package com.tutorbridge.app.viewmodel

import com.tutorbridge.app.data.model.MessageUi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The server's cap on one message (models/Message.ts); the compose box enforces it too. */
const val MAX_MESSAGE_LENGTH = 2000

/** How often an open chat looks for new messages. */
const val CHAT_POLL_MILLIS = 5_000L

/**
 * Folds a freshly fetched page into the conversation on screen. A message the
 * server sent again replaces what was there (its status may have moved on),
 * nothing appears twice, and the result is oldest first - sentAt is an ISO-8601
 * UTC string, so ordering it as text is ordering it in time.
 *
 * A message still on its way is dropped once the server's copy of it is here.
 * The two have different ids, but the server hands back the clientId the app
 * made when the message was written, so that is what ties them together.
 */
fun mergeMessages(existing: List<MessageUi>, incoming: List<MessageUi>): List<MessageUi> {
    val arrived = incoming.filter { !it.pending && it.clientId.isNotEmpty() }.map { it.clientId }.toSet()
    val stillWaiting = existing.filterNot { it.pending && it.clientId in arrived }
    return (incoming + stillWaiting).distinctBy { it.id }.sortedBy { it.sentAt }
}

/** The conversation split by the day each message was sent, on this phone's calendar. An unreadable sentAt lands under null. */
fun messagesByDay(messages: List<MessageUi>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate?, List<MessageUi>> =
    messages.groupBy { runCatching { Instant.parse(it.sentAt).atZone(zone).toLocalDate() }.getOrNull() }
