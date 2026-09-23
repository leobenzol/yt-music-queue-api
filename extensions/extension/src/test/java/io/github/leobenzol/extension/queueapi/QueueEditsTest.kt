package io.github.leobenzol.extension.queueapi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QueueEditsTest {
    private class Item(val id: String) {
        override fun toString() = id
    }

    /** The app's queue, shown as "C [R1] M1" with the playing item in brackets. */
    private class FakeQueue(vararg ids: String) : EditableQueue {
        val items = ids.mapTo(mutableListOf()) { Item(it) }
        override var currentIndex = 0
        override val size get() = items.size

        override fun itemAt(index: Int): Any = items[index]

        override fun videoIdAt(index: Int) = items[index].id

        override fun move(from: Int, to: Int) = items.add(to, items.removeAt(from))

        override fun remove(start: Int, count: Int) = repeat(count) { items.removeAt(start) }

        override fun jump(index: Int) {
            currentIndex = index
        }

        override fun toString() = items.withIndex().joinToString(" ") { (index, item) ->
            if (index == currentIndex) "[$item]" else "$item"
        }
    }

    @Test
    fun `upcomingIndexOf finds the first match after the playing item`() {
        val queue = FakeQueue("A", "B", "A", "C", "A")
        queue.currentIndex = 2
        assertEquals(4, QueueEdits.upcomingIndexOf(queue, "A"))
        assertEquals(-1, QueueEdits.upcomingIndexOf(queue, "B"))
    }

    @Test
    fun `remove keeps the count between 1 and the end of the queue`() {
        val queue = FakeQueue("C", "M1", "M2", "M3")
        assertEquals(1, QueueEdits.remove(queue, 1, 0))
        assertEquals("[C] M2 M3", queue.toString())
        assertEquals(2, QueueEdits.remove(queue, 1, 10))
        assertEquals("[C]", queue.toString())
    }

    @Test
    fun `clearUpcoming removes everything after the playing item`() {
        val queue = FakeQueue("P", "C", "M1", "M2")
        queue.currentIndex = 1
        assertEquals(2, QueueEdits.clearUpcoming(queue))
        assertEquals("P [C]", queue.toString())
        assertEquals(0, QueueEdits.clearUpcoming(queue))
    }

    @Test
    fun `clearRequests removes only the requests that have not played`() {
        val ordering = RequestOrdering()
        val queue = FakeQueue("C", "M1")
        for (id in listOf("R1", "R2", "R3")) {
            val item = Item(id)
            queue.items.add(queue.currentIndex + 1, item)
            ordering.place(queue, item, queue.items.indexOf(item), RequestOrdering.Request(id, "user", id, id))
        }
        queue.currentIndex = 1
        assertEquals("C [R1] R2 R3 M1", queue.toString())

        assertEquals(2, QueueEdits.clearRequests(queue, ordering))
        assertEquals("C [R1] M1", queue.toString())
    }

    @Test
    fun `isValidIndex`() {
        val queue = FakeQueue("C", "M1")
        assertEquals(listOf(false, true, true, false), listOf(-1, 0, 1, 2).map { QueueEdits.isValidIndex(queue, it) })
    }
}
