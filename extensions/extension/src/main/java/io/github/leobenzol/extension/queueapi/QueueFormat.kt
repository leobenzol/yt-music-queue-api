package io.github.leobenzol.extension.queueapi

import org.json.JSONArray
import org.json.JSONObject

/** A queue item as the API reports it. */
data class QueueItem(
    val index: Int,
    val videoId: String?,
    val title: String?,
    val artist: String?,
    /** Null for the host's own songs. */
    val requester: String?,
)

/** The queue_json and queue_text of GET_QUEUE results. */
object QueueFormat {

    /**
     * @param items Queue items from the playing one on.
     * @param autoplay Autoplay items.
     */
    fun json(apiVersion: Int, currentIndex: Int, size: Int, items: List<QueueItem>, autoplay: List<QueueItem>): String =
        JSONObject()
            .put("api_version", apiVersion)
            .put("current_index", currentIndex)
            .put("size", size)
            .put("items", JSONArray(items.map { it.toJson(isCurrent = it.index == currentIndex) }))
            .put("autoplay", JSONArray(autoplay.map { it.toJson(isCurrent = false) }))
            .toString()

    /** The songs after the playing one, one "1. Title – Artist (requester)" per line. */
    fun text(currentIndex: Int, items: List<QueueItem>): String =
        items.filter { it.index > currentIndex }.joinToString("\n") { item ->
            buildString {
                append(item.index - currentIndex).append(". ").append(item.title ?: item.videoId)
                if (!item.artist.isNullOrEmpty()) append(" – ").append(item.artist)
                if (item.requester != null) append(" (").append(item.requester).append(')')
            }
        }.ifEmpty { "Nothing queued" }

    private fun QueueItem.toJson(isCurrent: Boolean) = JSONObject().apply {
        put("index", index)
        put("video_id", videoId)
        title?.let { put("title", it) }
        artist?.let { put("artist", it) }
        requester?.let { put("requester", it) }
        if (isCurrent) put("current", true)
    }
}
