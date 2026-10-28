package com.pbrockt.fahrradcomputer.routes

import com.pbrockt.fahrradcomputer.BuildConfig
import com.pbrockt.fahrradcomputer.recording.RideTracker
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Ein gefundener Ort: Name, Adresse, Lage und Art (z. B. "cafe"). */
data class Place(
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    /** OpenStreetMap-Art, z. B. "cafe", "bakery", "restaurant". */
    val kind: String,
    /** Luftlinie vom Standort in Metern; null ohne Standort. */
    val distanceM: Double? = null,
)

/** Schnellsuche: Begriff plus OpenStreetMap-Merkmal. */
enum class PlaceCategory(val label: String, val osmTag: String) {
    CAFE("Café", "amenity:cafe"),
    BAKERY("Bäckerei", "shop:bakery"),
    ICE_CREAM("Eis", "amenity:ice_cream"),
    BEER_GARDEN("Biergarten", "amenity:biergarten"),
    RESTAURANT("Restaurant", "amenity:restaurant"),
    WATER("Trinkwasser", "amenity:drinking_water"),
    TOILETS("Toilette", "amenity:toilets"),
    BIKE_SHOP("Fahrradladen", "shop:bicycle"),
    CHARGING("E-Bike laden", "amenity:charging_station"),
}

/**
 * Ortssuche ueber Photon (komoot, OpenStreetMap-Daten, ohne Konto). Mit Standort werden
 * Orte in der Naehe bevorzugt.
 */
object PlaceSearch {

    fun search(query: String, lat: Double?, lon: Double?, category: PlaceCategory? = null): List<Place> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val url = buildString {
            append("https://photon.komoot.io/api/?q=$q&limit=15&lang=de")
            if (lat != null && lon != null) append(String.format(Locale.ROOT, "&lat=%.4f&lon=%.4f", lat, lon))
            if (category != null) append("&osm_tag=${category.osmTag}")
        }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Fahrrad-Computer/${BuildConfig.VERSION_NAME} (github.com/pbrockt/fahrrad-computer)")
        }
        try {
            check(conn.responseCode == 200) { "Suche antwortet mit ${conn.responseCode}" }
            val places = parse(conn.inputStream.bufferedReader().use { it.readText() }, lat, lon)
            // Schnellsuche nach Art: das Naechste zuerst.
            return if (category != null) places.sortedBy { it.distanceM ?: 0.0 } else places
        } finally {
            conn.disconnect()
        }
    }

    fun parse(json: String, lat: Double?, lon: Double?): List<Place> =
        Json.parseToJsonElement(json).jsonObject["features"]?.jsonArray.orEmpty().mapNotNull { f ->
            val o = f.jsonObject
            val c = o["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
            val p = o["properties"]?.jsonObject ?: return@mapNotNull null
            fun str(k: String) = p[k]?.jsonPrimitive?.content
            val pLon = c[0].jsonPrimitive.double
            val pLat = c[1].jsonPrimitive.double
            val name = str("name") ?: listOfNotNull(str("street"), str("housenumber")).joinToString(" ").ifBlank { null }
                ?: str("city") ?: return@mapNotNull null
            Place(
                name = name,
                address = address(p, name),
                lat = pLat,
                lon = pLon,
                kind = str("osm_value") ?: "",
                distanceM = if (lat != null && lon != null) RideTracker.distanceMeters(lat, lon, pLat, pLon) else null,
            )
        }

    private fun address(p: JsonObject, name: String): String {
        fun str(k: String) = p[k]?.jsonPrimitive?.content
        val street = listOfNotNull(str("street"), str("housenumber")).joinToString(" ").ifBlank { null }
        val town = listOfNotNull(str("postcode"), str("city") ?: str("town") ?: str("village")).joinToString(" ").ifBlank { null }
        return listOfNotNull(street, town, str("country").takeIf { it != "Deutschland" })
            .filter { it != name }.joinToString(", ")
    }
}
