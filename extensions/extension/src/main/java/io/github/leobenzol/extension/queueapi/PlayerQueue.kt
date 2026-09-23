package io.github.leobenzol.extension.queueapi

/**
 * The app's live queue. Main thread only.
 *
 * Take a new one for every operation, since the app replaces the queue object when a new
 * playlist or radio starts.
 */
class PlayerQueue private constructor(private val queue: Any) {
    /** Size of the queue shown in "Up next". */
    val size get() = AppBridge.queueSize(queue, SECTION_QUEUE)

    /** Index of the playing item, or -1. */
    val currentIndex get() = AppBridge.queueCurrentIndex(queue)

    val autoplaySize get() = AppBridge.queueSize(queue, SECTION_AUTOPLAY)

    fun itemAt(index: Int): Any = AppBridge.queueItem(queue, SECTION_QUEUE, index)!!

    fun autoplayItemAt(index: Int): Any = AppBridge.queueItem(queue, SECTION_AUTOPLAY, index)!!

    companion object {
        private const val SECTION_QUEUE = 0
        private const val SECTION_AUTOPLAY = 1

        fun of(queueManager: Any) = AppBridge.getQueue(queueManager)?.let(::PlayerQueue)
    }
}
