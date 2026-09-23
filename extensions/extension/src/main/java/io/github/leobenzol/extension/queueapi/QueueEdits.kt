package io.github.leobenzol.extension.queueapi

/** A queue that can be edited: the app's queue, or a fake one in tests. */
interface EditableQueue : RequestOrdering.QueueView {
    fun videoIdAt(index: Int): String?

    fun remove(start: Int, count: Int)

    /** Makes the item at [index] the current item, which starts playing it. */
    fun jump(index: Int)
}

/** The edits of MOVE, REMOVE, JUMP and CLEAR. */
object QueueEdits {
    fun isValidIndex(queue: EditableQueue, index: Int) = index in 0 until queue.size

    /** Index of the first item after the playing one with [videoId], or -1. */
    fun upcomingIndexOf(queue: EditableQueue, videoId: String): Int =
        (queue.currentIndex + 1 until queue.size).firstOrNull { queue.videoIdAt(it) == videoId } ?: -1

    /**
     * Removes [count] items from [index] on, at least one and no more than the queue has.
     *
     * @return The number of removed items.
     */
    fun remove(queue: EditableQueue, index: Int, count: Int): Int {
        val removed = count.coerceIn(1, queue.size - index)
        queue.remove(index, removed)
        return removed
    }

    /** Removes everything after the playing item. @return The number of removed items. */
    fun clearUpcoming(queue: EditableQueue): Int {
        val start = queue.currentIndex + 1
        val removed = maxOf(0, queue.size - start)
        if (removed > 0) queue.remove(start, removed)
        return removed
    }

    /** Removes the requests that have not played yet. @return The number of removed items. */
    fun clearRequests(queue: EditableQueue, ordering: RequestOrdering): Int {
        var removed = 0
        for (index in queue.size - 1 downTo queue.currentIndex + 1) {
            if (ordering.isRequest(queue.itemAt(index))) {
                queue.remove(index, 1)
                removed++
            }
        }
        ordering.prune(queue)
        return removed
    }
}
