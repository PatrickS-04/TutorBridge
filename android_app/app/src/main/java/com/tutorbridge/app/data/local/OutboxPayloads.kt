package com.tutorbridge.app.data.local

import com.tutorbridge.app.data.model.CreateTuitionRequest
import com.tutorbridge.app.data.model.TutorNotesDto
import com.tutorbridge.app.data.model.UpdateSettingsRequest

/**
 * What each [OutboxType] carries, stored as JSON in the outbox row. They hold
 * exactly what the API call needs - nothing is looked up again at replay time,
 * so a queued item still makes sense after the app has been closed for a day.
 */

/** [clientMessageId] is made when the user taps send, so a retry cannot post the message twice. */
data class MessagePayload(val threadId: String, val body: String, val clientMessageId: String)

/**
 * [request] carries its own `clientRequestId` for the same reason. The names
 * are only for drawing the "Waiting to sync" card while the request is queued.
 */
data class RequestPayload(val request: CreateTuitionRequest, val subjectName: String, val areaName: String)

/** The whole notes document for one session; the latest save is the only one that matters. */
data class NotesPayload(val bookingId: String, val notes: TutorNotesDto)

data class SettingsPayload(val settings: UpdateSettingsRequest)
