package io.github.leobenzol.extension.queueapi

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueueFormatTest {
    private val playing = QueueItem(3, "aaaaaaaaaaa", "One More Time", "Daft Punk", null)
    private val request = QueueItem(4, "bbbbbbbbbbb", "Africa", "Toto", "Alice")
    private val noMetadata = QueueItem(5, "ccccccccccc", null, null, null)

    @Test
    fun `text lists the songs after the playing one`() = assertEquals(
        "1. Africa – Toto (Alice)\n2. ccccccccccc",
        QueueFormat.text(3, listOf(playing, request, noMetadata)),
    )

    @Test
    fun `text without queued songs`() {
        assertEquals("Nothing queued", QueueFormat.text(3, listOf(playing)))
        assertEquals("Nothing queued", QueueFormat.text(-1, emptyList()))
    }

    @Test
    fun `json has the queue and marks the playing item`() {
        val json = JSONObject(QueueFormat.json(1, 3, 6, listOf(playing, request, noMetadata), listOf(noMetadata)))
        assertEquals(1, json.getInt("api_version"))
        assertEquals(3, json.getInt("current_index"))
        assertEquals(6, json.getInt("size"))

        val items = json.getJSONArray("items")
        assertEquals(3, items.length())
        assertTrue(items.getJSONObject(0).getBoolean("current"))
        assertEquals("Alice", items.getJSONObject(1).getString("requester"))
        assertFalse(items.getJSONObject(1).has("current"))
        assertFalse(items.getJSONObject(2).has("title"))
        assertEquals("ccccccccccc", items.getJSONObject(2).getString("video_id"))

        assertEquals(1, json.getJSONArray("autoplay").length())
        assertFalse(json.getJSONArray("autoplay").getJSONObject(0).has("current"))
    }
}
