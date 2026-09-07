package com.freedomfighter.readerslauncher.widgets

import android.content.Context
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    LaunchedEffect(tile.place, now / (30 * 60_000)) { repo.refresh(tile.place) }

    Column(
        Modifier
            .fillMaxWidth()
            .height(widgetTallHeight())
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    when (state) {
                        is WeatherState.Ready -> app.store.replaceTile(tile.copy(fiveDays = !tile.fiveDays))
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
                if (!tile.fiveDays) {
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
