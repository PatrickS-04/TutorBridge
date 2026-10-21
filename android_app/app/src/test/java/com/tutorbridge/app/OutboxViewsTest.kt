package com.tutorbridge.app

import com.google.gson.Gson
import com.tutorbridge.app.data.local.MessagePayload
import com.tutorbridge.app.data.local.NotesPayload
import com.tutorbridge.app.data.local.OutboxEntity
import com.tutorbridge.app.data.local.OutboxState
import com.tutorbridge.app.data.local.OutboxType
import com.tutorbridge.app.data.local.RequestPayload
import com.tutorbridge.app.data.local.SettingsPayload
import com.tutorbridge.app.data.local.pendingMessagesFor
import com.tutorbridge.app.data.local.pendingNotesByBooking
import com.tutorbridge.app.data.local.pendingRequestCards
import com.tutorbridge.app.data.local.pendingSettings
import com.tutorbridge.app.data.local.withPending
import com.tutorbridge.app.data.model.AvailabilityDto
import com.tutorbridge.app.data.model.CreateTuitionRequest
import com.tutorbridge.app.data.model.MessageUi
import com.tutorbridge.app.data.model.NotificationPreferencesDto
import com.tutorbridge.app.data.model.TutorNoteItemDto
import com.tutorbridge.app.data.model.TutorNotesDto
import com.tutorbridge.app.data.model.UpdateSettingsRequest
import com.tutorbridge.app.data.model.UserSettingsDto
import com.tutorbridge.app.viewmodel.TutorBridgeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Reference: Android Developers. 2026. Build local unit tests. [Online].
//   Available at: https://developer.android.com/training/testing/local-tests [Accessed 21 September 2026].
/** What the user sees of work that is queued but not yet on the server. */
class OutboxViewsTest {

    private val gson = Gson()
    private var nextId = 1L

    private fun item(type: OutboxType, payload: Any, state: String = OutboxState.PENDING) =
        OutboxEntity(id = nextId++, userId = "u1", type = type.name, payload = gson.toJson(payload), createdAtMillis = 1_700_000_000_000L, state = state)

    private fun server(id: String, clientId: String, at: String) =
        MessageUi(id = id, body = "hi", mine = true, time = "", sentAt = at, clientId = clientId)

    @Test
    fun `an unsent message is a bubble in its own conversation only`() {
        val items = listOf(
            item(OutboxType.SEND_MESSAGE, MessagePayload("t1", "see you at 3", "c-1")),
            item(OutboxType.SEND_MESSAGE, MessagePayload("t2", "other chat", "c-2"))
        )
        val bubbles = items.pendingMessagesFor("t1")

        assertEquals(1, bubbles.size)
        assertEquals("see you at 3", bubbles.single().body)
        assertEquals("local-c-1", bubbles.single().id)
        assertTrue(bubbles.single().pending && bubbles.single().mine)
        assertTrue(items.pendingMessagesFor(null).isEmpty())
    }

    @Test
    fun `a message the server refused is not shown as on its way`() {
        val items = listOf(item(OutboxType.SEND_MESSAGE, MessagePayload("t1", "x", "c-1"), OutboxState.FAILED))
        assertTrue(items.pendingMessagesFor("t1").isEmpty())
    }

    @Test
    fun `the bubble goes away once the server's copy is on screen`() {
        val queued = listOf(item(OutboxType.SEND_MESSAGE, MessagePayload("t1", "see you at 3", "c-1")))
        val state = TutorBridgeUiState(selectedThreadId = "t1", outboxItems = queued)
        assertEquals(1, state.visibleChat.size)

        val synced = state.copy(chatMessages = listOf(server("m9", "c-1", "2026-10-20T12:00:00.000Z")))
        assertEquals("one message, not the bubble and the copy", listOf("m9"), synced.visibleChat.map { it.id })
    }

    @Test
    fun `a request posted offline is a card with the names it was written with`() {
        val request = CreateTuitionRequest(
            subjectId = "s1", areaId = "a1", gradeLevel = "First year", description = "Limits\nand derivatives",
            availability = listOf(AvailabilityDto("WEDNESDAY", "14:00", "17:00")), clientRequestId = "r-1"
        )
        val cards = listOf(item(OutboxType.CREATE_REQUEST, RequestPayload(request, "Mathematics", "Upper Campus"))).pendingRequestCards()

        val card = cards.single()
        assertTrue(card.pendingSync)
        assertEquals("Mathematics", card.subject)
        assertEquals("Upper Campus", card.preferredVenue)
        assertEquals("Limits", card.topic)
        assertEquals("Wed 14:00–17:00", card.timeWindow)
        assertEquals("pending-r-1", card.id)
    }

    @Test
    fun `queued requests sit above the real ones, newest first`() {
        fun posted(id: String) = item(OutboxType.CREATE_REQUEST, RequestPayload(CreateTuitionRequest("s", "a", clientRequestId = id), id, "venue"))
        val state = TutorBridgeUiState(
            outboxItems = listOf(posted("older"), posted("newer")),
            myRequests = emptyList()
        )
        assertEquals(listOf("newer", "older"), state.visibleMyRequests.map { it.subject })
    }

    @Test
    fun `notes saved offline are by session`() {
        val notes = TutorNotesDto(listOf(TutorNoteItemDto("Mark homework", false)), "Call the parent")
        val unsent = listOf(item(OutboxType.SAVE_NOTES, NotesPayload("b1", notes))).pendingNotesByBooking()
        assertEquals("Call the parent", unsent.getValue("b1").text)
        assertNull(unsent["b2"])
    }

    @Test
    fun `settings changed offline fold into one, the last value winning`() {
        val items = listOf(
            item(OutboxType.SAVE_SETTINGS, SettingsPayload(UpdateSettingsRequest(theme = "DARK"))),
            item(OutboxType.SAVE_SETTINGS, SettingsPayload(UpdateSettingsRequest(language = "ZU"))),
            item(OutboxType.SAVE_SETTINGS, SettingsPayload(UpdateSettingsRequest(theme = "LIGHT")))
        )
        val merged = items.pendingSettings()!!

        assertEquals("LIGHT", merged.theme)
        assertEquals("ZU", merged.language)
        assertNull(merged.smsFallback)
        assertNull(emptyList<OutboxEntity>().pendingSettings())
    }

    @Test
    fun `a reload does not undo a setting that has not been sent yet`() {
        val fromServer = UserSettingsDto(language = "EN", theme = "LIGHT", smsFallback = true)
        val pending = UpdateSettingsRequest(theme = "DARK", notifications = NotificationPreferencesDto(booking = false))

        val shown = fromServer.withPending(pending)

        assertEquals("DARK", shown.theme)
        assertEquals(false, shown.notifications.booking)
        assertEquals("untouched settings keep the server's value", "EN", shown.language)
        assertEquals(fromServer, fromServer.withPending(null))
    }

    @Test
    fun `the state counts what is waiting and what was refused`() {
        val state = TutorBridgeUiState(
            outboxItems = listOf(
                item(OutboxType.SEND_MESSAGE, MessagePayload("t", "a", "1")),
                item(OutboxType.SEND_MESSAGE, MessagePayload("t", "b", "2")),
                item(OutboxType.SEND_MESSAGE, MessagePayload("t", "c", "3"), OutboxState.FAILED)
            )
        )
        assertEquals(2, state.pendingSyncCount)
        assertEquals(1, state.failedSyncCount)
    }
}
