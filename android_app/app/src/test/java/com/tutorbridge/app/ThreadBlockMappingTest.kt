package com.tutorbridge.app

import com.google.gson.Gson
import com.tutorbridge.app.data.model.BootstrapRequest
import com.tutorbridge.app.data.model.ThreadDto
import com.tutorbridge.app.data.repository.toThreadUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadBlockMappingTest {

    @Test
    fun `a block reaches the chat screen's state`() {
        val thread = ThreadDto(_id = "t1", blocked = true, blockedByMe = true).toThreadUi("me")
        assertTrue(thread.blocked)
        assertTrue(thread.blockedByMe)
    }

    @Test
    fun `a thread cached before blocking existed reads as not blocked`() {
        val thread = Gson().fromJson("""{"_id":"t1","participants":[],"unread":2}""", ThreadDto::class.java).toThreadUi("me")
        assertFalse(thread.blocked)
        assertFalse(thread.blockedByMe)
    }

    @Test
    fun `signing in without a declaration sends no ageConfirmed at all`() {
        assertEquals("""{"institutionId":"i1"}""", Gson().toJson(BootstrapRequest("i1")))
        assertTrue(Gson().toJson(BootstrapRequest("i1", ageConfirmed = true)).contains(""""ageConfirmed":true"""))
    }
}
