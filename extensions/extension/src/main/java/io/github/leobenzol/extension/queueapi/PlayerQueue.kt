package io.github.leobenzol.extension.queueapi

/**
 * The app's live queue. Main thread only.
 *
 * Take a new one for every operation, since the app replaces the queue object when a new
 * playlist or radio starts.
 */
class PlayerQueue private constructor(private val queue: Any) : EditableQueue {
    /** Size of the queue shown in "Up next". */
    override val size get() = AppBridge.queueSize(queue, SECTION_QUEUE)

    override val currentIndex get() = AppBridge.queueCurrentIndex(queue)

    val autoplaySize get() = AppBridge.queueSize(queue, SECTION_AUTOPLAY)

    override fun itemAt(index: Int): Any = AppBridge.queueItem(queue, SECTION_QUEUE, index)!!

    override fun videoIdAt(index: Int) = AppBridge.itemVideoId(itemAt(index))

    override fun move(from: Int, to: Int) = AppBridge.queueMove(queue, SECTION_QUEUE, from, SECTION_QUEUE, to)

    override fun remove(start: Int, count: Int) = AppBridge.queueRemove(queue, SECTION_QUEUE, start, count)

    override fun jump(index: Int) = AppBridge.queueSetCurrentIndex(queue, index)

    fun autoplayItemAt(index: Int): Any = AppBridge.queueItem(queue, SECTION_AUTOPLAY, index)!!

    companion object {
        private const val SECTION_QUEUE = 0
        private const val SECTION_AUTOPLAY = 1

        fun of(queueManager: Any) = AppBridge.getQueue(queueManager)?.let(::PlayerQueue)
    }
}
