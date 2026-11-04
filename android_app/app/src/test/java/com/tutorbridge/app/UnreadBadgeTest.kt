package com.tutorbridge.app

import com.tutorbridge.app.ui.components.unreadBadgeText
import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadBadgeTest {

    @Test
    fun `the badge shows the count until it no longer fits`() {
        assertEquals("1", unreadBadgeText(1))
        assertEquals("99", unreadBadgeText(99))
        assertEquals("99+", unreadBadgeText(100))
    }
}
