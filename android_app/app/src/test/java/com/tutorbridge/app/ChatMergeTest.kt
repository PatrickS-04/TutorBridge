package com.tutorbridge.app

import com.tutorbridge.app.data.model.MessageUi
import com.tutorbridge.app.viewmodel.mergeMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Reference: Android Developers. 2026. Build local unit tests. [Online].
//   Available at: https://developer.android.com/training/testing/local-tests [Accessed 21 September 2026].
/** The conversation on screen is rebuilt from pages that overlap, repeat and arrive late. */
class ChatMergeTest {

    private fun message(id: String, at: String, body: String = id, pending: Boolean = false) =
        MessageUi(id = id, body = body, mine = false, time = "", sentAt = at, pending = pending)

    @Test
    fun `a page that repeats what is already there adds nothing twice`() {
        val shown = listOf(message("a", "2026-10-07T10:00:00.000Z"), message("b", "2026-10-07T10:01:00.000Z"))
        val merged = mergeMessages(shown, shown)
        assertEquals(listOf("a", "b"), merged.map { it.id })
    }

    @Test
    fun `an earlier page lands above, a newer one below`() {
        val shown = listOf(message("b", "2026-10-07T10:01:00.000Z"))
        val withEarlier = mergeMessages(shown, listOf(message("a", "2026-10-07T10:00:00.000Z")))
        val withNewer = mergeMessages(withEarlier, listOf(message("c", "2026-10-07T10:02:00.000Z")))
        assertEquals(listOf("a", "b", "c"), withNewer.map { it.id })
    }

    @Test
    fun `a message the server sends again replaces the old copy`() {
        val shown = listOf(message("a", "2026-10-07T10:00:00.000Z", body = "old"))
        val merged = mergeMessages(shown, listOf(message("a", "2026-10-07T10:00:00.000Z", body = "new")))
        assertEquals(1, merged.size)
        assertEquals("new", merged.single().body)
    }

    @Test
    fun `a message still being sent stays until it is swapped for the stored one`() {
        val pending = message("local-1", "2026-10-07T10:05:00.000Z", pending = true)
        val shown = listOf(message("a", "2026-10-07T10:00:00.000Z"), pending)

        val polled = mergeMessages(shown, listOf(message("a", "2026-10-07T10:00:00.000Z")))
        assertTrue(polled.any { it.pending })

        val sent = mergeMessages(polled.filterNot { it.pending }, listOf(message("b", "2026-10-07T10:05:00.000Z")))
        assertEquals(listOf("a", "b"), sent.map { it.id })
    }
}
