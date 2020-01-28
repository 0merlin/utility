@file:Suppress("unused")

package zz.utility.helpers

import android.location.Location
import com.google.gson.JsonObject
import zz.utility.lib.OpenLocationCode
import zz.utility.lib.SunriseSunset
import java.util.*

fun Location.toJson(): JsonObject {
    return JsonObject().apply {
        addProperty("latitude", latitude)
        addProperty("longitude", longitude)
        addProperty("accuracy", accuracy)
        addProperty("updated", now())
        addProperty("speed", speed)
        addProperty("bearing", bearing)
        addProperty("gps_time", time)
        addProperty("provider", provider)
    }
}

fun nullIsland(): Location {
    val location = Location("none")
    location.latitude = 0.0
    location.longitude = 0.0
    location.accuracy = 0F
    location.speed = 0F
    location.bearing = 0F
    location.accuracy = 0F
    return location
}

fun Location.within(location: Location, distance: Double) = this.distanceTo(location) < distance
fun Location.outsideOf(location: Location, distance: Double) = this.distanceTo(location) > distance

fun Location.viz() = "%.7f, %.7f".format(latitude, longitude)

fun Location.basicDistance(latitude: Double, longitude: Double) = ((latitude - this.latitude + longitude - this.longitude) * 10000000).toInt()
fun Location.isNullIsland() = provider == "none" && latitude == 0.0 && longitude == 0.0


fun Float.bearingToCompass(): String = when {
    this < 28 -> "N"
    this < 73 -> "NE"
    this < 118 -> "E"
    this < 163 -> "SE"
    this < 208 -> "S"
    this < 253 -> "SW"
    this < 298 -> "W"
    this < 343 -> "NW"
    else -> "N"
}

fun Location.saveToFile() {
    val ss = SunriseSunset(latitude, longitude, Date(time), 0.0)
    JsonObject().apply {
        addProperty("event_time", Date().fullDate())
        addProperty("latitude", latitude)
        addProperty("longitude", longitude)
        addProperty("accuracy", accuracy)
        addProperty("speed", speed)

        addProperty("altitude", altitude)

        addProperty("bearing", bearing)
        addProperty("provider", provider)
        addProperty("bearingAccuracyDegrees", bearingAccuracyDegrees)
        addProperty("speedAccuracyMetersPerSecond", speedAccuracyMetersPerSecond)
        addProperty("verticalAccuracyMeters", verticalAccuracyMeters)
        addProperty("openLocationCode", OpenLocationCode.encode(latitude, longitude))
        addProperty("time", Date(time).fullDateDay())
        addProperty("sunrise", ss.sunrise?.fullDateDay())
        addProperty("sunset", ss.sunset?.fullDateDay())
    }.appendToFile("utility/location.json".externalFile())
}