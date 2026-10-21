package com.tutorbridge.app

import com.tutorbridge.app.data.local.MessagePayload
import com.tutorbridge.app.data.local.Outbox
import com.tutorbridge.app.data.local.OutboxEntity
import com.tutorbridge.app.data.local.OutboxState
import com.tutorbridge.app.data.local.OutboxType
import com.tutorbridge.app.data.local.SettingsPayload
import com.tutorbridge.app.data.model.UpdateSettingsRequest
import com.tutorbridge.app.sync.OutboxProcessor
import com.tutorbridge.app.sync.OutboxSender
import com.tutorbridge.app.sync.SendResult
import com.tutorbridge.app.sync.StopReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Reference: Android Developers. 2026. Build local unit tests. [Online].
//   Available at: https://developer.android.com/training/testing/local-tests [Accessed 21 September 2026].
/**
 * The sync engine's rules: in what order the queue is sent, and what each kind
 * of answer from the server does to an item. No Android, no network - a fake
 * table and a sender that answers from a script.
 */
class OutboxProcessorTest {

    private val dao = FakeOutboxDao()
    private var signedInAs: String? = "u1"
    private val outbox = Outbox(dao, userId = { signedInAs }, clock = { 1_000L })

    /** Sends in a scripted way: [answer] decides each item's fate, and every attempt is recorded. */
    private class ScriptedSender(val answer: (OutboxEntity) -> SendResult = { SendResult.Sent }) : OutboxSender {
        val attempts = mutableListOf<Long>()
        var onSend: suspend (OutboxEntity) -> Unit = {}
        override suspend fun send(item: OutboxEntity): SendResult {
            attempts += item.id
            onSend(item)
            return answer(item)
        }
    }

    private fun processor(sender: OutboxSender) = OutboxProcessor(outbox, sender) { signedInAs }

    private suspend fun queueMessage(text: String) =
        outbox.enqueue(OutboxType.SEND_MESSAGE, MessagePayload("t1", text, "c-$text"))!!

    @Test
    fun `everything waiting is sent oldest first and removed`() = runBlocking {
        val first = queueMessage("one")
        val second = queueMessage("two")
        val third = queueMessage("three")
        val sender = ScriptedSender()

        val report = processor(sender).drain()

        assertEquals(listOf(first, second, third), sender.attempts)
        assertEquals(3, report.sent)
        assertNull(report.stoppedBy)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `an empty queue is a quiet success`() = runBlocking {
        val report = processor(ScriptedSender()).drain()
        assertEquals(0, report.sent)
        assertNull(report.stoppedBy)
    }

    @Test
    fun `a refused item is marked failed and the rest still go`() = runBlocking {
        val first = queueMessage("one")
        val refused = queueMessage("two")
        val third = queueMessage("three")
        val sender = ScriptedSender { if (it.id == refused) SendResult.Rejected("Thread not found") else SendResult.Sent }

        val report = processor(sender).drain()

        assertEquals(listOf(first, refused, third), sender.attempts)
        assertEquals(2, report.sent)
        assertEquals(1, report.rejected)
        val left = dao.rows.value.single()
        assertEquals(refused, left.id)
        assertEquals(OutboxState.FAILED, left.state)
        assertEquals("Thread not found", left.lastError)
    }

    @Test
    fun `a failed item is not tried again by the next drain`() = runBlocking {
        val refused = queueMessage("one")
        val sender = ScriptedSender { SendResult.Rejected("no") }
        processor(sender).drain()
        processor(sender).drain()
        assertEquals("only the first drain tried it", listOf(refused), sender.attempts)
    }

    @Test
    fun `a connection problem stops the drain so later items cannot overtake`() = runBlocking {
        val first = queueMessage("one")
        val second = queueMessage("two")
        val third = queueMessage("three")
        val sender = ScriptedSender { if (it.id == second) SendResult.Retry("timeout") else SendResult.Sent }

        val report = processor(sender).drain()

        assertEquals("third is never attempted", listOf(first, second), sender.attempts)
        assertEquals(1, report.sent)
        assertEquals(StopReason.RETRY_LATER, report.stoppedBy)
        val remaining = dao.rows.value
        assertEquals(listOf(second, third), remaining.map { it.id })
        assertEquals("the attempt is counted", 1, remaining.first().attempts)
        assertEquals("timeout", remaining.first().lastError)
        assertTrue("and it is still waiting, not failed", remaining.all { it.state == OutboxState.PENDING })
    }

    @Test
    fun `a lapsed sign-in stops the drain and keeps everything`() = runBlocking {
        queueMessage("one")
        queueMessage("two")
        val sender = ScriptedSender { SendResult.AuthRequired }

        val report = processor(sender).drain()

        assertEquals(StopReason.AUTH_REQUIRED, report.stoppedBy)
        assertEquals(1, sender.attempts.size)
        assertEquals(2, dao.rows.value.size)
        assertTrue(dao.rows.value.all { it.state == OutboxState.PENDING })
    }

    @Test
    fun `nothing is sent while nobody is signed in`() = runBlocking {
        queueMessage("one")
        signedInAs = null
        val sender = ScriptedSender()

        val report = processor(sender).drain()

        assertEquals(StopReason.NOT_SIGNED_IN, report.stoppedBy)
        assertTrue(sender.attempts.isEmpty())
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun `one user's queue is never sent as another's`() = runBlocking {
        val mine = queueMessage("mine")
        signedInAs = "u2"
        val theirs = queueMessage("theirs")
        val sender = ScriptedSender()

        processor(sender).drain()

        assertEquals("only u2's item was sent", listOf(theirs), sender.attempts)
        assertEquals("u1's is still waiting for u1", listOf(mine), dao.rows.value.map { it.id })
    }

    @Test
    fun `work queued while a drain runs is picked up by the same drain`() = runBlocking {
        val first = queueMessage("one")
        var late: Long? = null
        val sender = ScriptedSender()
        sender.onSend = { item -> if (item.id == first && late == null) late = queueMessage("late") }

        val report = processor(sender).drain()

        assertEquals(listOf(first, late!!), sender.attempts)
        assertEquals(2, report.sent)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `failed items go back in the queue when the user retries`() = runBlocking {
        val id = queueMessage("one")
        processor(ScriptedSender { SendResult.Rejected("no") }).drain()
        assertEquals(OutboxState.FAILED, dao.rows.value.single().state)

        outbox.retryFailed()
        assertEquals(OutboxState.PENDING, dao.rows.value.single().state)

        val sender = ScriptedSender()
        processor(sender).drain()
        assertEquals(listOf(id), sender.attempts)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `a newer save of the same document replaces the unsent older one`() = runBlocking {
        outbox.enqueue(OutboxType.SAVE_SETTINGS, SettingsPayload(UpdateSettingsRequest(theme = "DARK")), dedupeKey = "k")
        outbox.enqueue(OutboxType.SAVE_SETTINGS, SettingsPayload(UpdateSettingsRequest(theme = "LIGHT")), dedupeKey = "k")

        val rows = dao.rows.value
        assertEquals(1, rows.size)
        assertTrue(rows.single().payload.contains("LIGHT"))
    }

    @Test
    fun `items without a key are all kept`() = runBlocking {
        outbox.enqueue(OutboxType.SAVE_SETTINGS, SettingsPayload(UpdateSettingsRequest(theme = "DARK")))
        outbox.enqueue(OutboxType.SAVE_SETTINGS, SettingsPayload(UpdateSettingsRequest(theme = "LIGHT")))
        assertEquals(2, dao.rows.value.size)
    }

    @Test
    fun `queueing needs a signed-in user`() = runBlocking {
        signedInAs = null
        assertNull(outbox.enqueue(OutboxType.SEND_MESSAGE, MessagePayload("t", "b", "c")))
        assertTrue(dao.rows.value.isEmpty())
    }
}
