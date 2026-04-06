package rs.wllrg.search

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import rs.wllrg.util.ApiKeys

data class SearchResult(
    val name: String,
    val type: String,
    val lat: Double,
    val lon: Double,
)

/**
 * Geocoder that uses the OS Names API when an OS key is available,
 * falling back to Nominatim (OpenStreetMap) otherwise.
 *
 * OS Names API: https://developer.ordnancesurvey.co.uk/os-names-api
 * Nominatim:    https://nominatim.openstreetmap.org (no key required)
 */
class OSNamesService {
    private val client = OkHttpClient()
    private val gson = Gson()

    private fun osKeyIsSet() = ApiKeys.OS_MAPS_KEY != "YOUR_OS_MAPS_API_KEY"

    suspend fun search(query: String): List<SearchResult> = if (osKeyIsSet()) searchOS(query) else searchNominatim(query)

    // -------------------------------------------------------------------------
    // OS Names API
    // -------------------------------------------------------------------------

    private suspend fun searchOS(query: String): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val url =
                "https://api.os.uk/search/names/v1/find" +
                    "?query=${query.trim().encodeUrl()}" +
                    "&maxresults=10" +
                    "&key=${ApiKeys.OS_MAPS_KEY}"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val parsed = gson.fromJson(body, OsNamesResponse::class.java)

            parsed.results
                ?.mapNotNull { it.gazetteerEntry }
                ?.map { entry ->
                    SearchResult(
                        name = entry.name1 ?: entry.id ?: "Unknown",
                        type = entry.type ?: "",
                        lat = entry.geometryY ?: 0.0,
                        lon = entry.geometryX ?: 0.0,
                    )
                }
                ?: emptyList()
        }

    // -------------------------------------------------------------------------
    // Nominatim fallback (no API key required)
    // -------------------------------------------------------------------------

    private suspend fun searchNominatim(query: String): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val url =
                "https://nominatim.openstreetmap.org/search" +
                    "?q=${query.trim().encodeUrl()}" +
                    "&countrycodes=gb" +
                    "&limit=10" +
                    "&format=json"

            val request =
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "OSLeisureMaps/1.0 (Android)")
                    .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val results = gson.fromJson(body, Array<NominatimResult>::class.java)

            results.map { r ->
                SearchResult(
                    name = r.displayName?.split(",")?.firstOrNull()?.trim() ?: r.displayName ?: "Unknown",
                    type = r.type?.replaceFirstChar { it.uppercase() } ?: "",
                    lat = r.lat?.toDoubleOrNull() ?: 0.0,
                    lon = r.lon?.toDoubleOrNull() ?: 0.0,
                )
            }
        }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, "UTF-8")

    // OS Names API JSON model
    private data class OsNamesResponse(
        @SerializedName("results") val results: List<OsResultItem>?,
    )

    private data class OsResultItem(
        @SerializedName("GAZETTEER_ENTRY") val gazetteerEntry: OsGazetteerEntry?,
    )

    private data class OsGazetteerEntry(
        @SerializedName("ID") val id: String?,
        @SerializedName("NAME1") val name1: String?,
        @SerializedName("TYPE") val type: String?,
        @SerializedName("GEOMETRY_X") val geometryX: Double?,
        @SerializedName("GEOMETRY_Y") val geometryY: Double?,
    )

    // Nominatim JSON model
    private data class NominatimResult(
        @SerializedName("display_name") val displayName: String?,
        @SerializedName("type") val type: String?,
        @SerializedName("lat") val lat: String?,
        @SerializedName("lon") val lon: String?,
    )
}
