package com.freedomfighter.readerslauncher.widgets

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.freedomfighter.readerslauncher.data.Place
import com.freedomfighter.readerslauncher.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

@Serializable
data class DayForecast(val date: String, val code: Int, val min: Double, val max: Double)

@Serializable
data class Forecast(
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    val currentTemp: Double,
    val currentCode: Int,
    val days: List<DayForecast>,
    val fetchedAt: Long,
    /** "open-meteo" or "meteosuisse". */
    val source: String = "open-meteo"
)

sealed class WeatherState {
    data object Loading : WeatherState()
    data object NoLocation : WeatherState()
    data object Offline : WeatherState()
    data class Ready(val forecast: Forecast) : WeatherState()
}

/**
 * Weather from Open-Meteo (free, no API key). Location from the platform LocationManager
 * (coarse), or a city chosen through Open-Meteo's geocoder. One shared cache for all
 * weather tiles that use the automatic location; manual places are keyed by coordinates.
 */
class WeatherRepo private constructor(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val states = HashMap<String, MutableStateFlow<WeatherState>>()

    fun state(place: Place?): StateFlow<WeatherState> = flowFor(place)

    private fun keyOf(place: Place?) = place?.let { "%.3f,%.3f".format(Locale.US, it.latitude, it.longitude) } ?: "auto"

    @Synchronized
    private fun flowFor(place: Place?): MutableStateFlow<WeatherState> =
        states.getOrPut(keyOf(place)) {
            val cached = context.getSharedPreferences("weather", Context.MODE_PRIVATE).getString(keyOf(place), null)
                ?.let { runCatching { json.decodeFromString(Forecast.serializer(), it) }.getOrNull() }
            MutableStateFlow(if (cached != null) WeatherState.Ready(cached) else WeatherState.Loading)
        }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun requestLocationPermission(activity: Activity?) {
        if (activity != null && !hasLocationPermission())
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 41)
    }

    /** Refresh if the cache is older than [maxAgeMin] minutes (or forced). */
    fun refresh(place: Place?, force: Boolean = false, maxAgeMin: Int = 30) {
        val flow = flowFor(place)
        val current = flow.value
        if (!force && current is WeatherState.Ready &&
            System.currentTimeMillis() - current.forecast.fetchedAt < maxAgeMin * 60_000L
        ) return
        scope.launch {
            try {
                val (lat, lon, name) = if (place != null) Triple(place.latitude, place.longitude, place.name) else {
                    if (!hasLocationPermission()) { flow.value = WeatherState.NoLocation; return@launch }
                    val loc = currentLocation() ?: run {
                        if (current !is WeatherState.Ready) flow.value = WeatherState.NoLocation
                        return@launch
                    }
                    Triple(loc.latitude, loc.longitude, placeName(loc.latitude, loc.longitude))
                }
                // Swiss places: MeteoSwiss first (the app's own forecast service), Open-Meteo otherwise.
                val f = fetchSwiss(lat, lon, name) ?: fetch(lat, lon, name)
                flow.value = WeatherState.Ready(f)
                context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
                    .putString(keyOf(place), json.encodeToString(Forecast.serializer(), f)).apply()
            } catch (e: Exception) {
                Log.w(TAG, "weather refresh failed", e)
                if (current !is WeatherState.Ready) flow.value = WeatherState.Offline
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        var best: Location? = null
        for (p in providers) {
            val l = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || l.time > best.time) best = l
        }
        val fresh = best != null && System.currentTimeMillis() - best.time < 2 * 60 * 60 * 1000L
        if (fresh) return best
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = when {
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                else -> return best
            }
            val got = withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    runCatching {
                        lm.getCurrentLocation(provider, null, Executors.newSingleThreadExecutor()) { l -> if (cont.isActive) cont.resume(l) }
                    }.onFailure { if (cont.isActive) cont.resume(null) }
                }
            }
            return got ?: best
        }
        return best
    }

    private suspend fun placeName(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val list = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
            list?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull() ?: ""
    }

    @Serializable private data class OmCurrent(val temperature_2m: Double, val weather_code: Int)
    @Serializable private data class OmDaily(val time: List<String>, val weather_code: List<Int>, val temperature_2m_max: List<Double>, val temperature_2m_min: List<Double>)
    @Serializable private data class OmResponse(val current: OmCurrent, val daily: OmDaily)

    private fun fetch(lat: Double, lon: Double, name: String): Forecast {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(Locale.US, lat, lon) +
            "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=5"
        val r = json.decodeFromString(OmResponse.serializer(), Http.get(url))
        val days = r.daily.time.indices.map { i ->
            DayForecast(r.daily.time[i], r.daily.weather_code[i], r.daily.temperature_2m_min[i], r.daily.temperature_2m_max[i])
        }
        return Forecast(name, lat, lon, r.current.temperature_2m, r.current.weather_code, days, System.currentTimeMillis())
    }

    // ---- MeteoSwiss ----------------------------------------------------------------------

    @Serializable private data class MsCurrent(val icon: Int = 0, val temperature: Double = 0.0)
    @Serializable private data class MsDay(val dayDate: String, val iconDay: Int = 0, val temperatureMax: Double = 0.0, val temperatureMin: Double = 0.0)
    @Serializable private data class MsResponse(val currentWeather: MsCurrent? = null, val forecast: List<MsDay> = emptyList())
    @Serializable private data class GaAttributes(val plz: Int? = null, val langtext: String? = null)
    @Serializable private data class GaResult(val attributes: GaAttributes = GaAttributes())
    @Serializable private data class GaResponse(val results: List<GaResult> = emptyList())

    private fun inSwitzerland(lat: Double, lon: Double) = lat in 45.75..47.9 && lon in 5.9..10.6

    /**
     * Forecast from MeteoSwiss for a Swiss place: the postal code comes from swisstopo's
     * geo.admin.ch identify service, the forecast from the MeteoSwiss app's own JSON service
     * (the one the community integrations use). Any failure → null → Open-Meteo.
     */
    private fun fetchSwiss(lat: Double, lon: Double, name: String): Forecast? {
        if (!inSwitzerland(lat, lon)) return null
        return try {
            val ga = "https://api3.geo.admin.ch/rest/services/api/MapServer/identify?geometry=%.5f,%.5f".format(Locale.US, lon, lat) +
                "&geometryType=esriGeometryPoint&layers=all:ch.swisstopo-vd.ortschaftenverzeichnis_plz&sr=4326&tolerance=0&returnGeometry=false"
            val hit = json.decodeFromString(GaResponse.serializer(), Http.get(ga)).results.firstOrNull()?.attributes
            val plz = hit?.plz ?: return null
            val r = json.decodeFromString(MsResponse.serializer(), Http.get("https://app-prod-ws.meteoswiss-app.ch/v1/plzDetail?plz=${plz}00"))
            val current = r.currentWeather ?: return null
            if (r.forecast.isEmpty()) return null
            val days = r.forecast.take(5).map { DayForecast(it.dayDate, swissToWmo(it.iconDay), it.temperatureMin, it.temperatureMax) }
            Forecast(name.ifBlank { hit.langtext ?: "" }, lat, lon, current.temperature, swissToWmo(current.icon), days, System.currentTimeMillis(), "meteosuisse")
        } catch (e: Exception) {
            Log.w(TAG, "MeteoSwiss unavailable, falling back to Open-Meteo", e)
            null
        }
    }

    /**
     * MeteoSwiss symbol codes (1–42 day, 101–142 night, from the official icon spreadsheet)
     * → the WMO-style code the glyphs are chosen from.
     */
    private fun swissToWmo(code: Int): Int = when (code) {
        1, 26, 101 -> 0
        2, 3, 4, 102, 103, 104 -> 2
        5, 35, 105, 126 -> 3
        27, 28, 127, 128 -> 45
        6, 9, 14, 17, 29, 33, 106, 109, 114, 117, 129, 133 -> 61
        20, 120 -> 65
        7, 10, 15, 18, 21, 31, 39, 107, 110, 115, 118, 121, 131, 139 -> 68
        8, 11, 16, 19, 22, 30, 34, 37, 42, 108, 111, 116, 119, 122, 130, 134, 137, 142 -> 71
        12, 36, 40, 41, 112, 136, 140, 141 -> 95
        13, 23, 24, 25, 32, 38, 113, 123, 124, 125, 132, 138 -> 96
        else -> 3
    }

    @Serializable private data class GeoResult(val name: String, val latitude: Double, val longitude: Double, val country: String? = null, val admin1: String? = null)
    @Serializable private data class GeoResponse(val results: List<GeoResult> = emptyList())

    /** Geocode a city name through Open-Meteo; picks the first match. */
    fun chooseCity(uiScope: CoroutineScope, query: String, onResult: (Place?) -> Unit) {
        uiScope.launch {
            val place = withContext(Dispatchers.IO) {
                runCatching {
                    val url = "https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(query, "UTF-8") +
                        "&count=1&language=" + Locale.getDefault().language
                    val r = json.decodeFromString(GeoResponse.serializer(), Http.get(url))
                    r.results.firstOrNull()?.let { Place(it.name, it.latitude, it.longitude) }
                }.getOrNull()
            }
            onResult(place)
        }
    }

    companion object {
        private const val TAG = "Weather"
        @Volatile private var instance: WeatherRepo? = null
        fun get(context: Context): WeatherRepo =
            instance ?: synchronized(this) { instance ?: WeatherRepo(context.applicationContext).also { instance = it } }
    }
}

/** WMO weather code → (icon resource name, short word). */
fun wmoIcon(code: Int): String = when (code) {
    0 -> "wx_sun"
    1, 2 -> "wx_partly"
    3 -> "wx_cloud"
    45, 48 -> "wx_fog"
    in 51..69, in 80..82 -> "wx_rain"
    in 71..77, 85, 86 -> "wx_snow"
    in 95..99 -> "wx_thunder"
    else -> "wx_cloud"
}
