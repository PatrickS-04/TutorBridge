package com.tutorbridge.app.sync

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.tutorbridge.app.data.local.MessagePayload
import com.tutorbridge.app.data.local.NotesPayload
import com.tutorbridge.app.data.local.OutboxEntity
import com.tutorbridge.app.data.local.OutboxType
import com.tutorbridge.app.data.local.RequestPayload
import com.tutorbridge.app.data.local.SettingsPayload
import com.tutorbridge.app.data.repository.ApiException
import com.tutorbridge.app.data.repository.TutorBridgeRepository
import java.io.IOException

/**
 * Sends queued items through the same repository the screens use, so a replayed
 * item takes exactly the path it would have taken online.
 *
 * Replay is safe because of what the server does with a repeat: messages and
 * requests carry client-made ids, so a send that got through but whose reply was
 * lost comes back as "already have it"; notes and settings replace what is
 * stored, so sending them twice changes nothing.
 */
class RepositoryOutboxSender(
    private val repository: TutorBridgeRepository,
    private val gson: Gson = Gson()
) : OutboxSender {

    override suspend fun send(item: OutboxEntity): SendResult {
        val type = runCatching { OutboxType.valueOf(item.type) }.getOrNull()
            ?: return SendResult.Rejected("Unknown item type ${item.type}")

        // A payload that no longer parses can never be sent, so retrying would only loop.
        return try {
            when (type) {
                OutboxType.SEND_MESSAGE -> {
                    val p = gson.fromJson(item.payload, MessagePayload::class.java)
                    // The sender's id is only used to draw the reply, which is thrown away here.
                    classify(repository.sendMessage(p.threadId, selfId = "", p.body, p.clientMessageId))
                }
                OutboxType.CREATE_REQUEST -> {
                    val p = gson.fromJson(item.payload, RequestPayload::class.java)
                    classify(repository.createRequest(p.request))
                }
                OutboxType.SAVE_NOTES -> {
                    val p = gson.fromJson(item.payload, NotesPayload::class.java)
                    classify(repository.saveSessionNotes(p.bookingId, p.notes))
                }
                OutboxType.SAVE_SETTINGS -> {
                    val p = gson.fromJson(item.payload, SettingsPayload::class.java)
                    classify(repository.saveSettings(p.settings))
                }
            }
        } catch (e: JsonParseException) {
            SendResult.Rejected("Unreadable queued item: ${e.message}")
        }
    }

    /** Turns a repository answer into what the processor needs to know. */
    private fun classify(result: Result<*>): SendResult {
        if (result.isSuccess) return SendResult.Sent
        return when (val error = result.exceptionOrNull()) {
            is IOException -> SendResult.Retry(error.message ?: "No connection")
            is ApiException -> classifyStatus(error)
            else -> SendResult.Rejected(error?.message ?: "Unknown error")
        }
    }

    companion object {
        /**
         * 401: the user must sign in again. Timeouts, rate limits and server
         * errors are the server's trouble, not the item's, so they are retried.
         * Any other 4xx means the server read the item and refused it.
         */
        fun classifyStatus(error: ApiException): SendResult = when {
            error.code == 401 -> SendResult.AuthRequired
            error.code == 408 || error.code == 425 || error.code == 429 || error.code >= 500 ->
                SendResult.Retry("Server answered ${error.code}")
            else -> SendResult.Rejected(error.message ?: "Server answered ${error.code}")
        }
    }
}
