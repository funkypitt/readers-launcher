package com.freedomfighter.readerslauncher.widgets

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The day's chart of the weather tile — MeteoSwiss's graph in the tile's two colours: the
 * temperature as a line across the day, precipitation as bars from the baseline, hours on the
 * horizontal axis. Pure geometry: no Android, no colours, so a JVM test can exercise the
 * scaling and the labels. The tile draws what [layout] returns.
 */
object WeatherChart {
    /** Where a label's [x] sits on its text. */
    enum class Anchor { START, CENTER, END }

    data class Pt(val x: Float, val y: Float)
    data class Bar(val left: Float, val top: Float, val right: Float, val bottom: Float)
    /** [y] is the top of the text. */
    data class Label(val text: String, val x: Float, val y: Float, val anchor: Anchor)

    /**
     * One day of a place: [temperature] has a point per hour from 0 h to 24 h (up to 25),
     * [precipitation] a value per hour (up to 24, mm/h), [nowHour] the current time in
     * hours since midnight when the day is today.
     */
    data class Day(val temperature: List<Double>, val precipitation: List<Double>, val nowHour: Double?)

    data class Layout(
        val plotLeft: Float,
        val plotRight: Float,
        val plotTop: Float,
        val baselineY: Float,
        val line: List<Pt>,
        val bars: List<Bar>,
        val labels: List<Label>,
        /** Hour ticks on the baseline, every six hours. */
        val ticks: List<Float>,
        /** The present moment: a vertical mark and the point of the curve, when the day is today. */
        val nowX: Float?,
        val nowY: Float?,
        val precipitationScale: Double
    )

    /** The hourly series cut to the day that is [now] in [zone]; null when the data does not reach it. */
    fun today(hourly: Hourly, now: Long, zone: ZoneId): Day? {
        val dayStart = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val first = ((dayStart - hourly.start) / 3_600_000.0).roundToLong()
        if (first < 0 || first >= hourly.temperature.size - 1) return null
        val i = first.toInt()
        val temperature = hourly.temperature.subList(i, minOf(i + 25, hourly.temperature.size))
        val precipitation = if (i < hourly.precipitation.size) hourly.precipitation.subList(i, minOf(i + 24, hourly.precipitation.size)) else emptyList()
        val nowHour = (now - dayStart) / 3_600_000.0
        return Day(temperature, precipitation, nowHour.takeIf { it in 0.0..24.0 })
    }

    /** The bar scale: the smallest of 1, 2, 3, 5, 10, 20, 30, 50… mm that holds the day's heaviest hour. */
    fun precipitationScale(max: Double): Double {
        if (max <= 1.0) return 1.0
        var step = 1.0
        while (true) {
            for (m in doubleArrayOf(1.0, 2.0, 3.0, 5.0)) if (m * step >= max) return m * step
            step *= 10
        }
    }

    fun temperatureText(celsius: Double, fahrenheit: Boolean): String =
        if (fahrenheit) "${(celsius * 9 / 5 + 32).roundToInt()}°" else "${celsius.roundToInt()}°"

    fun precipitationText(mm: Double): String =
        if (mm >= 10 || mm == floor(mm)) "${mm.roundToInt()} mm" else String.format(Locale.US, "%.1f mm", mm)

    /**
     * @param width, height the chart's box in pixels
     * @param labelHeight the height of a line of label text
     * @param gap the breathing space between things (a couple of pixels)
     * @param measure the width of a label's text
     */
    fun layout(
        day: Day,
        width: Float,
        height: Float,
        labelHeight: Float,
        gap: Float,
        fahrenheit: Boolean,
        measure: (String) -> Float
    ): Layout {
        val temps = day.temperature
        val tMin = temps.min()
        val tMax = temps.max()
        val maxText = temperatureText(tMax, fahrenheit)
        val minText = temperatureText(tMin, fahrenheit)

        // The box: hour labels under the baseline, the temperatures in a right margin, half a
        // line above for a label at the very top of the curve.
        val plotLeft = 0f
        val margin = gap * 3
        val plotRight = (width - maxOf(measure(maxText), measure(minText)) - margin).coerceAtLeast(width / 2)
        val plotTop = labelHeight / 2
        val baselineY = (height - labelHeight - gap).coerceAtLeast(plotTop + 1f)
        val plotHeight = baselineY - plotTop
        fun x(hour: Double) = plotLeft + ((plotRight - plotLeft) * hour / 24.0).toFloat()

        // Temperature in the upper part of the box, a span of at least four degrees so a flat
        // day does not turn into mountains.
        val span = maxOf(tMax - tMin, 4.0)
        val mid = (tMax + tMin) / 2
        val tTop = plotTop
        val tBottom = plotTop + plotHeight * 0.62f
        fun y(t: Double) = (tBottom - (tBottom - tTop) * ((t - (mid - span / 2)) / span)).toFloat()
        val line = temps.mapIndexed { h, t -> Pt(x(h.toDouble()), y(t)) }

        // Bars from the baseline, the heaviest hour reaching 45 % of the box.
        val pMax = day.precipitation.maxOrNull() ?: 0.0
        val scale = precipitationScale(pMax)
        val barMaxH = plotHeight * 0.45f
        val bars = day.precipitation.mapIndexedNotNull { h, p ->
            if (p <= 0.0) null else {
                val hgt = maxOf(2f, (barMaxH * (p / scale)).toFloat())
                Bar(x(h.toDouble()) + gap / 2, baselineY - hgt, x(h + 1.0) - gap / 2, baselineY)
            }
        }

        val labels = ArrayList<Label>()
        val labelTop = baselineY + gap
        for (h in 0..24 step 6) {
            val anchor = when (h) { 0 -> Anchor.START; 24 -> Anchor.END; else -> Anchor.CENTER }
            labels += Label(h.toString(), x(h.toDouble()), labelTop, anchor)
        }
        // Max and min at the curve's height in the right margin, pushed apart when they would touch.
        var yMaxLabel = y(tMax) - labelHeight / 2
        var yMinLabel = y(tMin) - labelHeight / 2
        if (yMinLabel - yMaxLabel < labelHeight) {
            val c = (yMaxLabel + yMinLabel) / 2
            yMaxLabel = c - labelHeight / 2
            yMinLabel = c + labelHeight / 2
        }
        yMaxLabel = yMaxLabel.coerceIn(0f, baselineY - labelHeight)
        yMinLabel = yMinLabel.coerceIn(0f, baselineY - labelHeight)
        val labelX = plotRight + margin
        labels += Label(maxText, labelX, yMaxLabel, Anchor.START)
        if (minText != maxText) labels += Label(minText, labelX, yMinLabel, Anchor.START)
        // The heaviest hour's amount above its bar — the only figure the bars need.
        if (pMax > 0.0) {
            val h = day.precipitation.indexOf(pMax)
            val bar = bars.first { abs(it.left - (x(h.toDouble()) + gap / 2)) < 0.01f }
            val text = precipitationText(pMax)
            val w = measure(text)
            val cx = ((bar.left + bar.right) / 2).coerceIn(plotLeft + w / 2, plotRight - w / 2)
            labels += Label(text, cx, (bar.top - gap - labelHeight).coerceAtLeast(0f), Anchor.CENTER)
        }

        val ticks = (0..24 step 6).map { x(it.toDouble()) }
        val nowX = day.nowHour?.let { x(it) }
        val nowY = day.nowHour?.let { h ->
            val i = floor(h).toInt().coerceIn(0, temps.size - 1)
            val j = minOf(i + 1, temps.size - 1)
            val f = (h - i).coerceIn(0.0, 1.0)
            y(temps[i] + (temps[j] - temps[i]) * f)
        }
        return Layout(plotLeft, plotRight, plotTop, baselineY, line, bars, labels, ticks, nowX, nowY, scale)
    }
}
