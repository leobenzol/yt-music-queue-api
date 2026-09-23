package io.github.leobenzol.extension.queueapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RequestOrderingTest {
    private class Item(val id: String) {
        override fun toString() = id
    }

    /** The app's queue, shown as "C [R1] M1" with the playing item in brackets. */
    private class FakeQueue(vararg ids: String) : RequestOrdering.QueueView {
        val items = ids.mapTo(mutableListOf()) { Item(it) }
        override var currentIndex = 0
        override val size get() = items.size

        override fun itemAt(index: Int): Any = items[index]

        override fun move(from: Int, to: Int) = items.add(to, items.removeAt(from))

        override fun toString() = items.withIndex().joinToString(" ") { (index, item) ->
            if (index == currentIndex) "[$item]" else "$item"
        }
    }

    private val ordering = RequestOrdering()

    /**
     * Adds a request the way the app does, then places it.
     *
     * @param appendAtEnd The app adds the item at the end of the queue instead of after the
     * playing item.
     */
    private fun FakeQueue.request(id: String, appendAtEnd: Boolean = false): Item {
        val item = Item(id)
        if (appendAtEnd) items.add(item) else items.add(currentIndex + 1, item)
        ordering.place(this, item, items.indexOf(item), RequestOrdering.Request(id, "user", id, id))
        return item
    }

    @ParameterizedTest(name = "app appends at end: {0}")
    @ValueSource(booleans = [false, true])
    fun `requests play first in, first out before the host's next song`(appendAtEnd: Boolean) {
        val queue = FakeQueue("C", "M1", "M2")
        queue.request("R1", appendAtEnd)
        queue.request("R2", appendAtEnd)
        queue.request("R3", appendAtEnd)
        assertEquals("[C] R1 R2 R3 M1 M2", queue.toString())
    }

    @ParameterizedTest(name = "app appends at end: {0}")
    @ValueSource(booleans = [false, true])
    fun `a new request joins the end of the waiting requests while a request plays`(appendAtEnd: Boolean) {
        val queue = FakeQueue("C", "M1", "M2")
        queue.request("R1", appendAtEnd)
        queue.request("R2", appendAtEnd)
        queue.request("R3", appendAtEnd)

        queue.currentIndex = 1
        queue.request("R4", appendAtEnd)
        assertEquals("C [R1] R2 R3 R4 M1 M2", queue.toString())

        queue.currentIndex = 4
        queue.request("R5", appendAtEnd)
        assertEquals("C R1 R2 R3 [R4] R5 M1 M2", queue.toString())
    }

    @ParameterizedTest(name = "app appends at end: {0}")
    @ValueSource(booleans = [false, true])
    fun `a host play next song counts as the host's next song`(appendAtEnd: Boolean) {
        val queue = FakeQueue("C", "H", "M1")
        queue.request("R1", appendAtEnd)
        assertEquals("[C] R1 H M1", queue.toString())

        queue.items.add(1, Item("H2"))
        queue.request("R2", appendAtEnd)
        assertEquals("[C] H2 R1 R2 H M1", queue.toString())
    }

    @ParameterizedTest(name = "app appends at end: {0}")
    @ValueSource(booleans = [false, true])
    fun `a queue with only the playing song`(appendAtEnd: Boolean) {
        val queue = FakeQueue("C")
        queue.request("R1", appendAtEnd)
        queue.request("R2", appendAtEnd)
        assertEquals("[C] R1 R2", queue.toString())
    }

    @ParameterizedTest(name = "app appends at end: {0}")
    @ValueSource(booleans = [false, true])
    fun `a request with the same video id as a host song`(appendAtEnd: Boolean) {
        val queue = FakeQueue("C", "M1")
        queue.request("M1", appendAtEnd)
        queue.request("R2", appendAtEnd)
        assertEquals("[C] M1 R2 M1", queue.toString())
    }

    @Test
    fun `requestFor returns the request only for request items`() {
        val queue = FakeQueue("C", "M1")
        val request = queue.request("R1")
        assertEquals("R1", ordering.requestFor(request)?.videoId)
        assertNull(ordering.requestFor(queue.items.last()))
    }

    @Test
    fun `pending lists the requests after the playing item`() {
        val queue = FakeQueue("C", "M1")
        queue.request("R1")
        queue.request("R2")
        assertEquals(listOf("R1", "R2"), ordering.pending(queue).map { it.videoId })

        queue.currentIndex = 2
        assertEquals(emptyList<String>(), ordering.pending(queue).map { it.videoId })
    }

    @Test
    fun `prune forgets removed items only`() {
        val queue = FakeQueue("C", "M1")
        val removed = queue.request("R1")
        val kept = queue.request("R2")

        queue.items.remove(removed)
        ordering.prune(queue)
        assertFalse(ordering.isRequest(removed))
        assertTrue(ordering.isRequest(kept))
    }
}
