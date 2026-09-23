package io.github.leobenzol.extension.queueapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SongRequestsTest {
    private val limits = RequestLimits(maxPerRequester = 2, maxTotal = 3, maxDurationSeconds = 600)
    private val song = MusicSearch.Result("africa00000", "Africa", "Toto", 296)

    private fun pending(vararg requesters: String) =
        requesters.mapIndexed { i, requester -> RequestOrdering.Request("r$i", requester, "video$i", "Song $i") }

    private fun check(requester: String, pending: List<RequestOrdering.Request>, limits: RequestLimits = this.limits) =
        SongRequests.check(song, requester, pending, limits)?.status

    @Test
    fun `a request within the limits is allowed`() = assertNull(check("Alice", pending("Alice", "Bob")))

    @Test
    fun `a song already waiting is a duplicate`() {
        val waiting = listOf(RequestOrdering.Request("r", "Bob", song.videoId, "Africa"))
        assertEquals(QueueApiPatch.STATUS_DUPLICATE, check("Alice", waiting))
    }

    @Test
    fun `a requester with too many songs waiting is rejected`() =
        assertEquals(QueueApiPatch.STATUS_LIMIT_REQUESTER, check("Alice", pending("Alice", "Alice")))

    @Test
    fun `a full request queue rejects everyone`() =
        assertEquals(QueueApiPatch.STATUS_LIMIT_TOTAL, check("Dave", pending("Alice", "Bob", "Carol")))

    @Test
    fun `a song over the maximum length is rejected`() {
        assertEquals(QueueApiPatch.STATUS_TOO_LONG, check("Alice", emptyList(), limits.copy(maxDurationSeconds = 200)))
        // Unknown length is allowed.
        assertNull(SongRequests.check(song.copy(durationSeconds = -1), "Alice", emptyList(), limits.copy(maxDurationSeconds = 200)))
    }

    @Test
    fun `0 turns a limit off`() =
        assertNull(check("Alice", pending("Alice", "Alice", "Alice", "Bob"), RequestLimits(0, 0, 0)))

    @Test
    fun `queued messages`() {
        assertEquals("Playing \"Africa\" by Toto now", SongRequests.queuedMessage(0, "Africa", "Toto"))
        assertEquals("\"Africa\" by Toto plays next", SongRequests.queuedMessage(1, "Africa", "Toto"))
        assertEquals("\"Africa\" is queued, #3 in line", SongRequests.queuedMessage(3, "Africa", ""))
    }

    @Test
    fun `the same song from the same requester counts once within the window`() {
        var now = 0L
        val filter = RedeliveryFilter(windowMillis = 1000, clock = { now })
        assertFalse(filter.isRedelivery(null, "Alice", "africa"))
        now = 500
        assertTrue(filter.isRedelivery(null, "Alice", "africa"))
        assertFalse(filter.isRedelivery(null, "Bob", "africa"))
        now = 2000
        assertFalse(filter.isRedelivery(null, "Alice", "africa"))
    }

    @Test
    fun `an explicit dedupe key is exact`() {
        var now = 0L
        val filter = RedeliveryFilter(windowMillis = 1000, clock = { now })
        assertFalse(filter.isRedelivery("message-1", "Alice", "africa"))
        now = 1_000_000
        assertTrue(filter.isRedelivery("message-1", "Alice", "africa"))
        assertFalse(filter.isRedelivery("message-2", "Alice", "africa"))
    }
}
