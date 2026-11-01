package com.tutorbridge.app

import com.tutorbridge.app.data.model.SERIES_DEFAULT
import com.tutorbridge.app.data.model.repeatFor
import com.tutorbridge.app.viewmodel.TutorBridgeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** A booking starts as one session, from the tutor's profile; repeating is something the student switches on. */
class RepeatStateTest {

    @Test
    fun `a new booking is a single session of the default series size, from a profile`() {
        val state = TutorBridgeUiState()
        assertFalse(state.repeatWeekly)
        assertEquals(SERIES_DEFAULT, state.repeatCount)
        assertFalse(state.bookingFromApplication)
        assertEquals(0, state.lastSeriesCount)
    }

    @Test
    fun `the default state sends no repeat`() {
        val state = TutorBridgeUiState()
        assertNull(repeatFor(state.repeatWeekly, state.repeatCount))
    }

    @Test
    fun `switching weekly on sends the chosen count`() {
        val state = TutorBridgeUiState().copy(repeatWeekly = true, repeatCount = 6)
        assertEquals(6, repeatFor(state.repeatWeekly, state.repeatCount)?.count)
    }
}
