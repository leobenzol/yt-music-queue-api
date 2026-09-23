package io.github.leobenzol.extension.queueapi

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MusicSearchTest {
    private fun savedResponse(name: String) = javaClass.getResource("/search/$name.json")!!.readText()

    /** A search result item with the given flex column runs. */
    private fun item(videoId: String?, title: String, details: List<String>) = JSONObject().put(
        "musicResponsiveListItemRenderer",
        JSONObject()
            .put("flexColumns", JSONArray().put(column(listOf(title))).put(column(details)))
            .apply { if (videoId != null) put("playlistItemData", JSONObject().put("videoId", videoId)) },
    )

    private fun column(texts: List<String>) = JSONObject().put(
        "musicResponsiveListItemFlexColumnRenderer",
        JSONObject().put("text", JSONObject().put("runs", JSONArray(texts.map { JSONObject().put("text", it) }))),
    )

    private fun response(vararg items: JSONObject) =
        JSONObject().put("contents", JSONObject().put("items", JSONArray(items.toList()))).toString()

    @Test
    fun `saved response gives the first song`() = assertEquals(
        MusicSearch.Result("wU26xVT_vBU", "One More Time (Radio Edit)", "Daft Punk", 321),
        MusicSearch.parseResponse(savedResponse("daft_punk_one_more_time")),
    )

    @Test
    fun `items without a video id are skipped`() = assertEquals(
        "song",
        MusicSearch.parseResponse(
            response(
                item(null, "Artist", listOf("Artist")),
                item("song", "Title", listOf("Artist")),
            ),
        )?.videoId,
    )

    @Test
    fun `older layout starting with Song and a duration over an hour`() = assertEquals(
        MusicSearch.Result("song", "Title", "Artist", 3723),
        MusicSearch.parseResponse(
            response(item("song", "Title", listOf("Song", " • ", "Artist", " • ", "Album", " • ", "1:02:03"))),
        ),
    )

    @Test
    fun `missing details`() = assertEquals(
        MusicSearch.Result("song", "Title", "", -1),
        MusicSearch.parseResponse(response(item("song", "Title", emptyList()))),
    )

    @Test
    fun `no songs`() = assertNull(MusicSearch.parseResponse(response()))

    @Test
    fun `video id from links`() {
        assertEquals("BSTsnWoslP4", MusicSearch.videoIdFromLink("https://music.youtube.com/watch?v=BSTsnWoslP4&list=RDAMVM"))
        assertEquals("BSTsnWoslP4", MusicSearch.videoIdFromLink("https://www.youtube.com/watch?feature=share&v=BSTsnWoslP4"))
        assertEquals("BSTsnWoslP4", MusicSearch.videoIdFromLink("https://youtu.be/BSTsnWoslP4?si=abc"))
        assertEquals("BSTsnWoslP4", MusicSearch.videoIdFromLink("https://youtube.com/shorts/BSTsnWoslP4"))
        assertEquals("BSTsnWoslP4", MusicSearch.videoIdFromLink("play this https://youtu.be/BSTsnWoslP4 please"))
    }

    @Test
    fun `no video id without a link`() {
        assertNull(MusicSearch.videoIdFromLink("daft punk one more time"))
        assertNull(MusicSearch.videoIdFromLink("https://music.youtube.com/playlist?list=PLxyz1234567890"))
    }

    @Test
    fun `playlist id from links`() {
        assertEquals("PLxyz1234567890", MusicSearch.playlistIdFromLink("https://music.youtube.com/playlist?list=PLxyz1234567890"))
        assertEquals("RDAMVMBSTsnWoslP4", MusicSearch.playlistIdFromLink("https://music.youtube.com/watch?v=BSTsnWoslP4&list=RDAMVMBSTsnWoslP4"))
        assertNull(MusicSearch.playlistIdFromLink("https://youtu.be/BSTsnWoslP4"))
    }
}
