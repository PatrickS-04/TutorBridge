package com.tutorbridge.app

import com.tutorbridge.app.data.model.PendingBookingUi
import com.tutorbridge.app.data.model.PendingItem
import com.tutorbridge.app.data.model.groupPending
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the tutor's waiting sessions are folded into cards. */
class PendingGroupingTest {

    private fun waiting(id: String, seriesId: String? = null, index: Int = 0, count: Int = 0, student: String = "Zola") =
        PendingBookingUi(
            id = id, studentName = student, initials = "Z", subjectName = "Maths",
            whenLabel = "2026-11-0$id", location = "Library",
            seriesId = seriesId, seriesIndex = index, seriesCount = count
        )

    @Test
    fun `sessions that are not in a series stay as they are`() {
        val items = groupPending(listOf(waiting("1"), waiting("2")))
        assertEquals(2, items.size)
        assertTrue(items.all { it is PendingItem.Single })
    }

    @Test
    fun `an empty list gives an empty list`() {
        assertTrue(groupPending(emptyList()).isEmpty())
    }

    @Test
    fun `the waiting sessions of a series become one card`() {
        val items = groupPending((1..4).map { waiting("$it", "s1", it, 4) })

        assertEquals(1, items.size)
        val series = items.single() as PendingItem.Series
        assertEquals("s1", series.seriesId)
        assertEquals(4, series.waiting)
        assertEquals(4, series.total)
        assertEquals("1", series.first.id)
    }

    @Test
    fun `the card shows the earliest waiting session, whatever order they arrive in`() {
        val items = groupPending(listOf(waiting("3", "s1", 3, 4), waiting("2", "s1", 2, 4), waiting("4", "s1", 4, 4)))
        val series = items.single() as PendingItem.Series
        assertEquals("2", series.first.id)
        assertEquals(3, series.waiting)
        assertEquals("the series was booked with four even though three still wait", 4, series.total)
    }

    @Test
    fun `a series with one session left waiting is an ordinary card`() {
        val items = groupPending(listOf(waiting("3", "s1", 3, 4)))
        assertTrue(items.single() is PendingItem.Single)
    }

    @Test
    fun `the series card sits where its first session was and the rest keep their order`() {
        val items = groupPending(
            listOf(
                waiting("a"),
                waiting("1", "s1", 1, 3),
                waiting("b"),
                waiting("2", "s1", 2, 3),
                waiting("3", "s1", 3, 3),
                waiting("c")
            )
        )

        assertEquals(4, items.size)
        assertEquals("a", (items[0] as PendingItem.Single).booking.id)
        assertEquals("s1", (items[1] as PendingItem.Series).seriesId)
        assertEquals("b", (items[2] as PendingItem.Single).booking.id)
        assertEquals("c", (items[3] as PendingItem.Single).booking.id)
    }

    @Test
    fun `two series from different students are two cards`() {
        val items = groupPending(
            listOf(
                waiting("1", "s1", 1, 2, "Zola"), waiting("2", "s1", 2, 2, "Zola"),
                waiting("3", "s2", 1, 2, "Sipho"), waiting("4", "s2", 2, 2, "Sipho")
            )
        )
        assertEquals(listOf("s1", "s2"), items.map { (it as PendingItem.Series).seriesId })
    }
}
