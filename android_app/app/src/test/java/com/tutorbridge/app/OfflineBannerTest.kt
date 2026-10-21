package com.tutorbridge.app

import com.tutorbridge.app.data.local.MessagePayload
import com.tutorbridge.app.data.local.OutboxEntity
import com.tutorbridge.app.data.local.OutboxState
import com.tutorbridge.app.data.local.OutboxType
import com.tutorbridge.app.ui.components.bannerVisible
import com.tutorbridge.app.viewmodel.TutorBridgeUiState
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Reference: Android Developers. 2026. Build local unit tests. [Online].
//   Available at: https://developer.android.com/training/testing/local-tests [Accessed 21 September 2026].
/**
 * How the phone's connection and the outbox turn into what the user is told:
 * when the offline banner shows, and what the counts behind it say.
 */
class OfflineBannerTest {

    private fun queued(state: String, id: Long = 1) = OutboxEntity(
        id = id,
        userId = "u1",
        type = OutboxType.SEND_MESSAGE.name,
        payload = Gson().toJson(MessagePayload("t", "hi", "c$id")),
        createdAtMillis = 0,
        state = state
    )

    @Test
    fun `online with nothing refused there is nothing to say`() {
        assertFalse(bannerVisible(TutorBridgeUiState()))
    }

    @Test
    fun `waiting items alone do not raise the banner while online`() {
        // They are on their way and about to be gone; flashing a banner for each send would be noise.
        assertFalse(bannerVisible(TutorBridgeUiState(outboxItems = listOf(queued(OutboxState.PENDING)))))
    }

    @Test
    fun `losing the connection raises it`() {
        assertTrue(bannerVisible(TutorBridgeUiState(networkAvailable = false)))
    }

    @Test
    fun `forcing offline mode raises it even with a connection`() {
        assertTrue(bannerVisible(TutorBridgeUiState(isOffline = true, networkAvailable = true)))
    }

    @Test
    fun `a refusal from the server raises it even while online`() {
        assertTrue(bannerVisible(TutorBridgeUiState(outboxItems = listOf(queued(OutboxState.FAILED)))))
    }

    @Test
    fun `the counts split waiting from refused`() {
        val state = TutorBridgeUiState(
            outboxItems = listOf(
                queued(OutboxState.PENDING, 1),
                queued(OutboxState.PENDING, 2),
                queued(OutboxState.FAILED, 3)
            )
        )
        assertEquals(2, state.pendingSyncCount)
        assertEquals(1, state.failedSyncCount)
    }

    @Test
    fun `coming back online with a clean queue clears the banner`() {
        val offline = TutorBridgeUiState(networkAvailable = false, outboxItems = listOf(queued(OutboxState.PENDING)))
        assertTrue(bannerVisible(offline))

        val back = offline.copy(networkAvailable = true, outboxItems = emptyList())
        assertFalse(bannerVisible(back))
    }
}
