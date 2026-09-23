package io.github.leobenzol.extension.queueapi

import java.util.Collections
import java.util.IdentityHashMap

/**
 * Where song requests go in the queue.
 *
 * A request is placed after the playing item and after every request that is still waiting to
 * play, before the rest of the host's queue. Requests therefore play first in, first out, and the
 * host's next song is pushed back by exactly the number of waiting requests.
 *
 * Requests are tracked by the identity of the app's queue items, so a request and a host song
 * with the same video id are never confused.
 *
 * Not thread safe. Call it only from the thread that owns the queue (the main thread).
 */
class RequestOrdering {

    /** Read and write access to the app's queue. */
    interface QueueView {
        val size: Int

        /** Index of the playing item, or -1 if nothing is playing. */
        val currentIndex: Int

        fun itemAt(index: Int): Any

        /** Moves an item. [to] is the item's index after the move, like RecyclerView. */
        fun move(from: Int, to: Int)
    }

    data class Request(
        val requestId: String,
        val requester: String,
        val videoId: String,
        val title: String,
    )

    private val requests = IdentityHashMap<Any, Request>()

    fun isRequest(item: Any) = requests.containsKey(item)

    /** The request that added [item], or null for the host's own items. */
    fun requestFor(item: Any): Request? = requests[item]

    /**
     * Index before which a new request is inserted.
     *
     * @param exclude Item to skip while scanning, such as the item being placed.
     */
    fun insertionIndex(queue: QueueView, exclude: Any? = null): Int {
        val start = queue.currentIndex + 1
        var index = start
        for (i in start until queue.size) {
            val item = queue.itemAt(i)
            if (item !== exclude && isRequest(item)) index = i + 1
        }
        return index
    }

    /**
     * Moves a newly added item to the end of the waiting requests and starts tracking it.
     *
     * @return The item's final index.
     */
    fun place(queue: QueueView, item: Any, index: Int, request: Request): Int {
        val target = insertionIndex(queue, item)
        // Removing the item first shifts every later index down by one.
        val to = if (index < target) target - 1 else target
        if (to != index) queue.move(index, to)
        requests[item] = request
        return to
    }

    /** Requests that have not played yet, in play order. */
    fun pending(queue: QueueView): List<Request> =
        (queue.currentIndex + 1 until queue.size).mapNotNull { requests[queue.itemAt(it)] }

    /** Stops tracking items that are no longer in the queue. */
    fun prune(queue: QueueView) {
        if (requests.isEmpty()) return
        val present = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        (0 until queue.size).mapTo(present) { queue.itemAt(it) }
        requests.keys.retainAll(present)
    }

    fun clear() = requests.clear()
}
