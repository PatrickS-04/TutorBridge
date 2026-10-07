package com.tutorbridge.app.viewmodel

import com.tutorbridge.app.data.model.MessageUi

/** The server's cap on one message (models/Message.ts); the compose box enforces it too. */
const val MAX_MESSAGE_LENGTH = 2000

/** How often an open chat looks for new messages. */
const val CHAT_POLL_MILLIS = 5_000L

/**
 * Folds a freshly fetched page into the conversation on screen. A message the
 * server sent again replaces what was there (its status may have moved on),
 * nothing appears twice, and the result is oldest first - sentAt is an ISO-8601
 * UTC string, so ordering it as text is ordering it in time.
 */
fun mergeMessages(existing: List<MessageUi>, incoming: List<MessageUi>): List<MessageUi> =
    (incoming + existing).distinctBy { it.id }.sortedBy { it.sentAt }
