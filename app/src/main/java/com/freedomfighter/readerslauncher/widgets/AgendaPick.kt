package com.freedomfighter.readerslauncher.widgets

/**
 * Which event the agenda tile opens on. Pure, so it is tested on the JVM; the same rule as the
 * Reader's Calendar widget (WidgetPick there).
 *
 * An all-day event of today no longer hides the day: it is shown only while the next timed event
 * is more than an hour away. From an hour before, the tile shows that timed event, keeps it until
 * it is over, then the next one, and so on. Once the timed events are over, the all-day event
 * comes back while it lasts; after that, the first thing still to come.
 */
object AgendaPick {
    const val LEAD = 3600_000L   // a timed event takes over an hour before it starts

    fun line(items: List<EventInfo>, now: Long): EventInfo? {
        val live = items.filter { it.end > now }.sortedWith(compareBy({ it.begin }, { !it.allDay }))
        val timed = live.filter { !it.allDay }
        // running: the one that started first stays until it is over
        timed.firstOrNull { it.begin <= now }?.let { return it }
        val next = timed.firstOrNull { it.begin > now }
        if (next != null && next.begin - now <= LEAD) return next
        live.firstOrNull { it.allDay && it.begin <= now }?.let { return it }
        return live.firstOrNull()
    }

    /** The next moment the choice may change: an event's end, its start, or an hour before it. */
    fun nextChange(items: List<EventInfo>, now: Long): Long? =
        items.flatMap { listOf(it.begin - LEAD, it.begin, it.end) }.filter { it > now }.minOrNull()
}
