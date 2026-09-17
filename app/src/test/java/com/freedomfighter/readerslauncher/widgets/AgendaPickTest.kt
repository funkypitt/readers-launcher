package com.freedomfighter.readerslauncher.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class AgendaPickTest {
    private val zone = ZoneId.of("Europe/Zurich")
    private val day = LocalDate.of(2026, 9, 17)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = LocalDateTime.of(d, java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
    private fun midnight(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
    private var n = 0L
    private fun timed(title: String, b: Long, e: Long) = EventInfo(++n, title, b, e, false, null)
    private fun allDay(title: String, from: LocalDate, days: Int = 1) = EventInfo(++n, title, midnight(from), midnight(from.plusDays(days.toLong())), true, null)
    private fun pick(items: List<EventInfo>, now: Long) = AgendaPick.line(items, now)?.title

    private val holiday = allDay("holiday", day)
    private val ten = timed("10:00", at(10), at(11))
    private val two = timed("14:00", at(14), at(15))
    private val tomorrow = timed("tomorrow 9:00", at(9, d = day.plusDays(1)), at(10, d = day.plusDays(1)))
    private val items = listOf(holiday, ten, two, tomorrow)

    @Test fun allDayOnly() = assertEquals("holiday", pick(listOf(holiday, tomorrow), at(15)))
    @Test fun allDayWhileFirstEventIsFar() = assertEquals("holiday", pick(items, at(8)))
    @Test fun exactlyOneHourBefore() = assertEquals("10:00", pick(items, at(9)))
    @Test fun lessThanAnHourBefore() = assertEquals("10:00", pick(items, at(9, 10)))
    @Test fun duringTheEvent() = assertEquals("10:00", pick(items, at(10, 30)))
    @Test fun afterItTheAllDayUntilAnHourBeforeTheNext() = assertEquals("holiday", pick(items, at(11, 30)))
    @Test fun thenTheNext() = assertEquals("14:00", pick(items, at(13, 15)))
    @Test fun afterTheLastTheAllDayAgain() = assertEquals("holiday", pick(items, at(16)))
    @Test fun afterTheAllDayTomorrowsFirst() = assertEquals("tomorrow 9:00", pick(items, at(0, 30, day.plusDays(1))))
    @Test fun withoutAllDayTheNextEventEvenIfFar() = assertEquals("14:00", pick(listOf(ten, two), at(11, 30)))
    @Test fun nothingLeft() = assertNull(pick(listOf(ten), at(12)))

    @Test fun overlappingKeepsTheFirstUntilItEnds() {
        val a = timed("a 10-12", at(10), at(12)); val b = timed("b 11-11:30", at(11), at(11, 30)); val c = timed("c 11:15-13", at(11, 15), at(13))
        assertEquals("a 10-12", pick(listOf(holiday, a, b, c), at(11, 20)))
        assertEquals("c 11:15-13", pick(listOf(holiday, a, b, c), at(12, 5)))
    }

    @Test fun multiDayAllDay() {
        val retreat = allDay("retreat", day.minusDays(1), 4)
        assertEquals("retreat", pick(listOf(retreat, two), at(8)))
        assertEquals("14:00", pick(listOf(retreat, two), at(13, 30)))
        assertEquals("retreat", pick(listOf(retreat, two), at(20)))
    }

    @Test fun eventEndingAtMidnight() {
        val late = timed("late", at(22), midnight(day.plusDays(1)))
        assertEquals("late", pick(listOf(holiday, late), at(23, 59)))
        assertNull(pick(listOf(holiday, late), midnight(day.plusDays(1))))
    }

    @Test fun nextChangeBoundaries() {
        assertEquals(at(9), AgendaPick.nextChange(items, at(8)))
        assertEquals(at(10), AgendaPick.nextChange(items, at(9, 10)))
        assertEquals(at(11), AgendaPick.nextChange(items, at(10, 30)))
        assertEquals(at(13), AgendaPick.nextChange(items, at(11, 30)))
        assertEquals(midnight(day.plusDays(1)), AgendaPick.nextChange(listOf(holiday), at(16)))
    }
}
