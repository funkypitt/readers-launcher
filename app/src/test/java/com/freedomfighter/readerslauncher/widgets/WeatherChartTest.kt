package com.freedomfighter.readerslauncher.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WeatherChartTest {
    private val zone = ZoneId.of("Europe/Zurich")
    private val day = LocalDate.of(2026, 10, 11)
    private val midnight = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    /** Six days of hours as MeteoSwiss sends them: 144 temperatures, 126 precipitations from midnight. */
    private val hourly = Hourly(
        start = midnight,
        temperature = List(144) { i -> 10.0 + (i % 24) / 2.0 },
        precipitation = List(126) { i -> if (i == 15) 1.2 else if (i == 16) 0.4 else 0.0 }
    )

    private fun measure(s: String) = s.length * 8f
    private fun layout(d: WeatherChart.Day, w: Float = 400f, h: Float = 120f) = WeatherChart.layout(d, w, h, 16f, 2f, false, ::measure)

    @Test fun todayIsCutAtMidnightWithTwentyFivePoints() {
        val d = WeatherChart.today(hourly, at(14, 30), zone)!!
        assertEquals(25, d.temperature.size)
        assertEquals(24, d.precipitation.size)
        assertEquals(10.0, d.temperature[0], 0.0)
        assertEquals(14.5, d.nowHour!!, 0.001)
    }

    @Test fun tomorrowStartsTwentyFourHoursLater() {
        val d = WeatherChart.today(hourly, at(9, d = day.plusDays(1)), zone)!!
        assertEquals(10.0, d.temperature[0], 0.0)
        assertEquals(0.0, d.precipitation[15], 0.0)
    }

    @Test fun yesterdayIsNotInTheData() = assertNull(WeatherChart.today(hourly, at(9, d = day.minusDays(1)), zone))

    @Test fun beyondTheSeriesNothing() = assertNull(WeatherChart.today(hourly, at(9, d = day.plusDays(7)), zone))

    @Test fun lastDayOfTheSeriesIsShorterButShown() {
        val d = WeatherChart.today(hourly, at(9, d = day.plusDays(5)), zone)!!
        assertEquals(24, d.temperature.size)
        assertEquals(6, d.precipitation.size)
    }

    @Test fun scaleHoldsTheHeaviestHour() {
        assertEquals(1.0, WeatherChart.precipitationScale(0.0), 0.0)
        assertEquals(1.0, WeatherChart.precipitationScale(0.7), 0.0)
        assertEquals(2.0, WeatherChart.precipitationScale(1.2), 0.0)
        assertEquals(3.0, WeatherChart.precipitationScale(2.4), 0.0)
        assertEquals(5.0, WeatherChart.precipitationScale(4.9), 0.0)
        assertEquals(10.0, WeatherChart.precipitationScale(7.0), 0.0)
        assertEquals(20.0, WeatherChart.precipitationScale(11.0), 0.0)
        assertEquals(50.0, WeatherChart.precipitationScale(37.0), 0.0)
    }

    @Test fun hoursRunLeftToRightAcrossThePlot() {
        val l = layout(WeatherChart.today(hourly, at(12), zone)!!)
        assertEquals(0f, l.line.first().x, 0.001f)
        assertEquals(l.plotRight, l.line.last().x, 0.001f)
        assertTrue(l.plotRight < 400f - measure("22°"))
        assertEquals(listOf("0", "6", "12", "18", "24"), l.labels.take(5).map { it.text })
        assertEquals(WeatherChart.Anchor.START, l.labels[0].anchor)
        assertEquals(WeatherChart.Anchor.END, l.labels[4].anchor)
        assertEquals(l.plotRight / 2, l.labels[2].x, 0.001f)
        assertEquals(5, l.ticks.size)
    }

    @Test fun warmerIsHigher() {
        val l = layout(WeatherChart.today(hourly, at(12), zone)!!)
        assertTrue(l.line[0].y > l.line[23].y)
        assertEquals(l.plotTop, l.line[23].y, 0.001f)
        assertTrue(l.line.all { it.y >= l.plotTop && it.y < l.baselineY })
    }

    @Test fun maxAndMinWrittenInTheRightMargin() {
        val l = layout(WeatherChart.today(hourly, at(12), zone)!!)
        val temps = l.labels.filter { it.text.endsWith("°") }
        assertEquals(listOf("22°", "10°"), temps.map { it.text })
        temps.forEach { assertEquals(l.plotRight + 6f, it.x, 0.001f) }
        assertTrue(temps[0].y + 16f <= temps[1].y + 0.001f)
    }

    @Test fun fahrenheitWhenAsked() {
        val l = WeatherChart.layout(WeatherChart.today(hourly, at(12), zone)!!, 400f, 120f, 16f, 2f, true, ::measure)
        assertTrue(l.labels.any { it.text == "71°" })
        assertTrue(l.labels.any { it.text == "50°" })
    }

    @Test fun flatDayIsNotBlownUp() {
        val flat = WeatherChart.Day(List(25) { 12.0 + (it % 2) * 0.2 }, emptyList(), null)
        val l = layout(flat)
        val ys = l.line.map { it.y }
        assertTrue((ys.max() - ys.min()) < (l.baselineY - l.plotTop) * 0.1f)
        assertEquals(1, l.labels.count { it.text.endsWith("°") })
    }

    @Test fun rainBarsStandOnTheBaselineAndTheHeaviestIsLabelled() {
        val l = layout(WeatherChart.today(hourly, at(12), zone)!!)
        assertEquals(2, l.bars.size)
        assertEquals(2.0, l.precipitationScale, 0.0)
        val big = l.bars[0]
        assertEquals(l.baselineY, big.bottom, 0.001f)
        val plotH = l.baselineY - l.plotTop
        assertEquals(plotH * 0.45f * 0.6f, big.bottom - big.top, 0.01f)
        assertTrue(big.left > l.plotRight * 15 / 24 && big.right < l.plotRight * 16 / 24)
        val lb = l.labels.single { it.text.endsWith("mm") }
        assertEquals("1.2 mm", lb.text)
        assertEquals((big.left + big.right) / 2, lb.x, 0.001f)
        assertTrue(lb.y + 16f <= big.top)
    }

    @Test fun aDryDayHasNoBarsAndNoRainFigure() {
        val l = layout(WeatherChart.Day(List(25) { 10.0 + it / 2.0 }, List(24) { 0.0 }, null))
        assertTrue(l.bars.isEmpty())
        assertTrue(l.labels.none { it.text.endsWith("mm") })
    }

    @Test fun aDrizzleStillShowsATwoPixelBar() {
        val l = layout(WeatherChart.Day(List(25) { 10.0 }, List(24) { if (it == 3) 0.01 else 0.0 }, null))
        assertEquals(2f, l.bars.single().let { it.bottom - it.top }, 0.001f)
    }

    @Test fun millimetresAreWrittenShort() {
        assertEquals("3 mm", WeatherChart.precipitationText(3.0))
        assertEquals("0.4 mm", WeatherChart.precipitationText(0.4))
        assertEquals("12 mm", WeatherChart.precipitationText(12.3))
    }

    @Test fun nowIsMarkedOnTheCurve() {
        val l = layout(WeatherChart.today(hourly, at(6), zone)!!)
        assertNotNull(l.nowX)
        assertEquals(l.plotRight / 4, l.nowX!!, 0.001f)
        assertEquals(l.line[6].y, l.nowY!!, 0.001f)
        val l2 = layout(WeatherChart.Day(List(25) { 10.0 }, emptyList(), null))
        assertNull(l2.nowX)
    }

    @Test fun halfwayThroughAnHourTheMarkIsBetweenTwoPoints() {
        val l = layout(WeatherChart.today(hourly, at(6, 30), zone)!!)
        assertEquals((l.line[6].y + l.line[7].y) / 2, l.nowY!!, 0.001f)
    }
}
