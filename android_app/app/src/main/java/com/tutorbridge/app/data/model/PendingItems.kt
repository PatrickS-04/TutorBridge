package com.tutorbridge.app.data.model

/** One card on the tutor's "waiting for you" list: a single session, or a whole weekly series. */
sealed interface PendingItem {

    data class Single(val booking: PendingBookingUi) : PendingItem

    /**
     * Several waiting sessions the same student booked in one go.
     * [first] is the earliest of them, [waiting] how many still wait on the tutor, [total] how many the series had,
     * and [days] the weekdays the waiting sessions fall on, in week order.
     */
    data class Series(
        val seriesId: String,
        val first: PendingBookingUi,
        val waiting: Int,
        val total: Int,
        val days: List<java.time.DayOfWeek> = emptyList()
    ) : PendingItem
}

/**
 * Folds the waiting sessions of a series into one card, so a month of lessons is one decision rather than four.
 * The card sits where the series' first waiting session was, and the order of everything else is kept.
 *
 * A series with only one session still waiting (the others were confirmed or declined one by one) stays an
 * ordinary card, since confirming "the series" would then mean exactly that one session.
 */
fun groupPending(bookings: List<PendingBookingUi>): List<PendingItem> {
    val waitingBySeries = bookings.filter { it.seriesId != null }.groupBy { it.seriesId!! }
    val shown = mutableSetOf<String>()
    return bookings.mapNotNull { booking ->
        val seriesId = booking.seriesId
        val members = if (seriesId != null) waitingBySeries[seriesId].orEmpty() else emptyList()
        when {
            seriesId == null || members.size < 2 -> PendingItem.Single(booking)
            shown.add(seriesId) -> PendingItem.Series(
                seriesId = seriesId,
                first = members.minBy { it.seriesIndex },
                waiting = members.size,
                total = members.maxOf { it.seriesCount },
                days = members.mapNotNull { it.dayOfWeek }.distinct().sorted()
            )
            else -> null
        }
    }
}
