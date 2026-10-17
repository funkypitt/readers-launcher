package com.freedomfighter.readerslauncher.widgets

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import com.freedomfighter.readerslauncher.App
import com.freedomfighter.readerslauncher.R
import com.freedomfighter.readerslauncher.data.WeatherTile
import com.freedomfighter.readerslauncher.ui.LocalColors
import com.freedomfighter.readerslauncher.ui.findActivity
import com.freedomfighter.readerslauncher.ui.LocalTypo
import com.freedomfighter.readerslauncher.ui.MenuItem
import com.freedomfighter.readerslauncher.ui.Small
import com.freedomfighter.readerslauncher.ui.T
import com.freedomfighter.readerslauncher.ui.rowPadH
import com.freedomfighter.readerslauncher.ui.rowPadV
import com.freedomfighter.readerslauncher.ui.widgetTallHeight
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
private fun WxIcon(code: Int, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val name = wmoIcon(code)
    val id = remember(name) { context.resources.getIdentifier(name, "drawable", context.packageName) }
    if (id != 0) {
        Image(
            painter = painterResource(id),
            contentDescription = null,
            modifier = Modifier.size(size),
            colorFilter = ColorFilter.tint(LocalColors.current.fg)
        )
    }
}

private fun temp(c: Double, f: Boolean): String =
    if (f) "${(c * 9 / 5 + 32).roundToInt()}°" else "${c.roundToInt()}°"

/**
 * Weather tile: time on the left, today's weather on the right; tap to switch to five days.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeatherTileView(tile: WeatherTile, app: App, onLongPress: () -> Unit, onNeedCity: (WeatherTile) -> Unit) {
    val context = LocalContext.current
    val repo = remember { WeatherRepo.get(context) }
    val state by repo.state(tile.place).collectAsState()
    val now = rememberNow()
    val typo = LocalTypo.current
    val colors = LocalColors.current

    // Also on the home's ON_RESUME: the half-hour tick alone left a forecast hours old on screen
    // when the phone had slept or the network was down at that moment.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumed++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(tile.place, now / (30 * 60_000), resumed) { repo.refresh(tile.place) }

    Column(
        Modifier
            .fillMaxWidth()
            .height(widgetTallHeight())
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    when (val s = state) {
                        // Today → five days → the day's chart (when MeteoSwiss gives one) → today.
                        is WeatherState.Ready -> app.store.replaceTile(
                            when {
                                tile.fiveDays -> tile.copy(fiveDays = false, chart = chartDay(s.forecast, now) != null)
                                tile.chart && chartDay(s.forecast, now) != null -> tile.copy(chart = false)
                                else -> tile.copy(fiveDays = true, chart = false)
                            }
                        )
                        WeatherState.NoLocation -> if (repo.hasLocationPermission()) onNeedCity(tile) else repo.requestLocationPermission(context.findActivityCompat())
                        else -> repo.refresh(tile.place, force = true)
                    }
                },
                onLongClick = onLongPress
            )
            .padding(horizontal = rowPadH, vertical = rowPadV),
        verticalArrangement = Arrangement.Center
    ) {
        when (val s = state) {
            WeatherState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                T(timeString(now), size = typo.big, lineHeightMul = 1.05f, align = TextAlign.Start)
                Spacer(Modifier.width(20.dp))
                Small(stringResource(R.string.weather_loading), align = TextAlign.Start)
            }
            WeatherState.NoLocation -> Row(verticalAlignment = Alignment.CenterVertically) {
                T(timeString(now), size = typo.big, lineHeightMul = 1.05f, align = TextAlign.Start)
                Spacer(Modifier.width(20.dp))
                Small(stringResource(R.string.weather_no_location), align = TextAlign.Start)
            }
            WeatherState.Offline -> Row(verticalAlignment = Alignment.CenterVertically) {
                T(timeString(now), size = typo.big, lineHeightMul = 1.05f, align = TextAlign.Start)
                Spacer(Modifier.width(20.dp))
                Small(stringResource(R.string.weather_offline), align = TextAlign.Start)
            }
            is WeatherState.Ready -> {
                val f = s.forecast
                val placeLabel = f.placeName.ifBlank { stringResource(R.string.weather_here) }
                val chartDay = if (tile.chart && !tile.fiveDays) remember(f, now / 60_000) { chartDay(f, now) } else null
                if (chartDay != null) {
                    // The day's chart: place and day on one line, then MeteoSwiss's graph in two colours.
                    val dayFmt = remember { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()) }
                    Small("${placeLabel.lowercase()} · ${dayFmt.format(java.util.Date(now)).lowercase()}", maxLines = 1, align = TextAlign.Start, color = colors.fg)
                    DayChart(chartDay, tile.fahrenheit, Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp))
                } else if (!tile.fiveDays) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T(timeString(now), size = typo.big, lineHeightMul = 1.05f, align = TextAlign.Start)
                            Small(placeLabel.lowercase(), maxLines = 1, align = TextAlign.Start)
                        }
                        WxIcon(f.currentCode, 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            T(temp(f.currentTemp, tile.fahrenheit), size = typo.tile * 1.4f, align = TextAlign.End, lineHeightMul = 1.05f)
                            f.days.firstOrNull()?.let { d ->
                                Small(stringResource(R.string.weather_min_max, temp(d.min, tile.fahrenheit), temp(d.max, tile.fahrenheit)), align = TextAlign.End, maxLines = 1)
                            }
                        }
                    }
                } else {
                    // Five days: day, glyph, max/min — nothing else, so the tile keeps its height.
                    val dayFmt = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
                    val parse = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        f.days.take(5).forEach { d ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                val label = runCatching { dayFmt.format(parse.parse(d.date)!!) }.getOrDefault("").lowercase().trimEnd('.')
                                Small(label, align = TextAlign.Center, maxLines = 1, color = colors.fg)
                                WxIcon(d.code, 32.dp)
                                Small(temp(d.max, tile.fahrenheit) + "/" + temp(d.min, tile.fahrenheit), align = TextAlign.Center, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Context.findActivityCompat(): android.app.Activity? = findActivity()

/**
 * Today's hours of a MeteoSwiss forecast, in Swiss time (the place is Swiss); null elsewhere, when
 * the data has run out, or when it is more than six hours old (an hour-by-hour chart that old misleads).
 */
private fun chartDay(f: Forecast, now: Long): WeatherChart.Day? =
    f.hourly?.takeIf { now - f.fetchedAt < 6 * 3_600_000L }?.let { WeatherChart.today(it, now, ZoneId.of("Europe/Zurich")) }

/**
 * The chart itself: a thick line for the temperature, solid bars for the rain, a vertical mark at
 * the present hour, figures in the tile's text. Two colours and no transparency, for e-ink.
 */
@Composable
private fun DayChart(day: WeatherChart.Day, fahrenheit: Boolean, modifier: Modifier) {
    val colors = LocalColors.current
    val typo = LocalTypo.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.fg, fontFamily = typo.family, fontWeight = typo.weight, fontSize = typo.tile * 0.5f)
    Canvas(modifier) {
        val labelH = measurer.measure("0°", labelStyle).size.height.toFloat()
        val gap = 2.dp.toPx()
        val l = WeatherChart.layout(day, size.width, size.height, labelH, gap, fahrenheit) { measurer.measure(it, labelStyle).size.width.toFloat() }
        val stroke = 2.5.dp.toPx()
        // Bars first, the line over them with a halo in the background colour so it stays readable where they meet.
        l.bars.forEach { b -> drawRect(colors.fg, Offset(b.left, b.top), Size(b.right - b.left, b.bottom - b.top)) }
        drawLine(colors.fg, Offset(l.plotLeft, l.baselineY), Offset(l.plotRight, l.baselineY), strokeWidth = 1.dp.toPx())
        l.ticks.forEach { x -> drawLine(colors.fg, Offset(x, l.baselineY), Offset(x, l.baselineY + gap * 1.5f), strokeWidth = 1.dp.toPx()) }
        if (l.line.size >= 2) {
            val path = Path().apply {
                moveTo(l.line[0].x, l.line[0].y)
                l.line.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, colors.bg, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, colors.fg, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (l.nowX != null && l.nowY != null) {
            drawLine(colors.fg, Offset(l.nowX, l.plotTop), Offset(l.nowX, l.baselineY), strokeWidth = 1.dp.toPx())
            drawCircle(colors.bg, stroke * 1.8f, Offset(l.nowX, l.nowY))
            drawCircle(colors.fg, stroke * 1.2f, Offset(l.nowX, l.nowY))
        }
        l.labels.forEach { lb ->
            val m = measurer.measure(lb.text, labelStyle)
            val x = when (lb.anchor) {
                WeatherChart.Anchor.START -> lb.x
                WeatherChart.Anchor.CENTER -> lb.x - m.size.width / 2f
                WeatherChart.Anchor.END -> lb.x - m.size.width
            }
            drawText(m, topLeft = Offset(x, lb.y))
        }
    }
}

@Composable
fun weatherMenuItems(tile: WeatherTile, app: App, context: Context, onChooseCity: () -> Unit): List<MenuItem> = buildList {
    val current = WeatherRepo.get(context).state(tile.place).value
    if (current is WeatherState.Ready) {
        add(MenuItem(stringResource(R.string.menu_source, if (current.forecast.source == "meteosuisse") "MétéoSuisse" else "Open-Meteo")) { })
    }
    add(MenuItem(stringResource(R.string.menu_refresh)) { WeatherRepo.get(context).refresh(tile.place, force = true) })
    add(MenuItem(stringResource(R.string.menu_units, if (tile.fahrenheit) "°F" else "°C")) {
        app.store.replaceTile(tile.copy(fahrenheit = !tile.fahrenheit))
    })
    if (tile.place == null) {
        add(MenuItem(stringResource(R.string.menu_location_auto), stringResource(R.string.menu_choose_city)) { onChooseCity() })
    } else {
        add(MenuItem(stringResource(R.string.menu_location_city, tile.place.name), stringResource(R.string.menu_choose_city)) { onChooseCity() })
        add(MenuItem(stringResource(R.string.menu_use_auto_location)) {
            app.store.replaceTile(tile.copy(place = null))
            WeatherRepo.get(context).requestLocationPermission(context.findActivityCompat())
        })
    }
}
