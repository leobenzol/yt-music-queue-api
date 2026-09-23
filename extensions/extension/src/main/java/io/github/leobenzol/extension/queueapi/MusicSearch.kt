package io.github.leobenzol.extension.queueapi

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Turns a song request into a video id: from a YouTube or YouTube Music link in the text, or by
 * searching the "Songs" tab of YouTube Music (InnerTube WEB_REMIX client).
 *
 * Does not use the app's obfuscated code. The response is only walked loosely, so small InnerTube
 * layout changes do not break it.
 */
object MusicSearch {

    data class Result(
        val videoId: String,
        val title: String,
        val artist: String,
        /** -1 if unknown. */
        val durationSeconds: Int,
    )

    private const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"

    /** "Songs" filter, the same value ytmusicapi uses. */
    private const val SONGS_FILTER_PARAMS = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
    private const val CLIENT_VERSION = "1.20250310.01.00"
    private const val TIMEOUT_MILLISECONDS = 8_000
    private const val SEARCH_ATTEMPTS = 2

    private val LINK_VIDEO_ID =
        Regex("(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})")
    private val LINK_PLAYLIST_ID = Regex("[?&]list=([A-Za-z0-9_-]{10,})")
    private val DURATION = Regex("(?:(\\d+):)?(\\d{1,2}):(\\d{2})")

    /** The video id of a YouTube link in [text], or null. */
    fun videoIdFromLink(text: String): String? = LINK_VIDEO_ID.find(text)?.groupValues?.get(1)

    /** The playlist id of a link with a list parameter in [text], or null. */
    fun playlistIdFromLink(text: String): String? = LINK_PLAYLIST_ID.find(text)?.groupValues?.get(1)

    /** The best "Songs" match for [query], or null. Blocks, so call it off the main thread. */
    fun search(query: String): Result? {
        // InnerTube sometimes answers a valid query with no results. One retry fixes it.
        repeat(SEARCH_ATTEMPTS) {
            searchOnce(query)?.let { return it }
        }
        return null
    }

    private fun searchOnce(query: String): Result? {
        val client = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", CLIENT_VERSION)
            .put("hl", "en")
        val body = JSONObject()
            .put("context", JSONObject().put("client", client))
            .put("query", query)
            .put("params", SONGS_FILTER_PARAMS)

        val connection = URL(SEARCH_URL).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = TIMEOUT_MILLISECONDS
            connection.readTimeout = TIMEOUT_MILLISECONDS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Origin", "https://music.youtube.com")
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36",
            )

            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Search failed with HTTP ${connection.responseCode}")
            }
            return parseResponse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    /** The first song in an InnerTube search response, or null. */
    internal fun parseResponse(json: String): Result? = findFirstSong(JSONObject(json))?.let(::parseItem)

    /** Depth first search for the first musicResponsiveListItemRenderer that has a video id. */
    private fun findFirstSong(node: Any?): JSONObject? {
        when (node) {
            is JSONObject -> {
                val renderer = node.optJSONObject("musicResponsiveListItemRenderer")
                if (renderer != null && videoIdOf(renderer) != null) return renderer
                for (key in node.keys()) findFirstSong(node.opt(key))?.let { return it }
            }

            is JSONArray -> for (i in 0 until node.length()) findFirstSong(node.opt(i))?.let { return it }
        }
        return null
    }

    private fun videoIdOf(renderer: JSONObject): String? =
        renderer.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotEmpty() }

    private fun parseItem(renderer: JSONObject): Result? {
        val videoId = videoIdOf(renderer) ?: return null
        val columns = renderer.optJSONArray("flexColumns")
        val title = runsOf(columns, 0).firstOrNull().orEmpty()

        // Detail runs look like [artist, " • ", album, " • ", "3:45"]. Older layouts start with "Song".
        var artist = ""
        var duration = -1
        for (text in runsOf(columns, 1)) {
            val match = DURATION.matchEntire(text)
            if (match != null) {
                val (hours, minutes, seconds) = match.destructured
                duration = (hours.toIntOrNull() ?: 0) * 3600 + minutes.toInt() * 60 + seconds.toInt()
            } else if (artist.isEmpty() && text != "•" && text != "Song") {
                artist = text
            }
        }
        return Result(videoId, title, artist, duration)
    }

    /** The trimmed texts of the runs in flex column [index]. */
    private fun runsOf(columns: JSONArray?, index: Int): List<String> {
        val runs = columns?.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?: return emptyList()
        return (0 until runs.length()).map { runs.optJSONObject(it)?.optString("text").orEmpty().trim() }
    }
}
