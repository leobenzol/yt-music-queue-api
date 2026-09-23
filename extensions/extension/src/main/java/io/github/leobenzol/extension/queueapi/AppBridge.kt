package io.github.leobenzol.extension.queueapi

/**
 * Calls into YouTube Music's obfuscated code.
 *
 * Every method here is a placeholder: the patch replaces each body with a call to the app method it
 * found. The methods must stay public, since internal names are changed by the Kotlin compiler.
 *
 * Queue methods must be called on the main thread. Queue sections: 0 is the queue shown in
 * "Up next", 1 is autoplay.
 */
@Suppress("unused", "UNUSED_PARAMETER")
object AppBridge {
    /** The PlaybackQueue the PlaybackQueueManager currently uses. */
    @JvmStatic
    fun getQueue(queueManager: Any): Any? = throw notPatched()

    @JvmStatic
    fun queueSize(queue: Any, section: Int): Int = throw notPatched()

    /** Index of the playing item in section 0, or -1. */
    @JvmStatic
    fun queueCurrentIndex(queue: Any): Int = throw notPatched()

    @JvmStatic
    fun queueItem(queue: Any, section: Int, index: Int): Any? = throw notPatched()

    /** Removes the item and inserts it again, so that it ends up at [toIndex]. */
    @JvmStatic
    fun queueMove(queue: Any, fromSection: Int, fromIndex: Int, toSection: Int, toIndex: Int): Unit = throw notPatched()

    @JvmStatic
    fun queueRemove(queue: Any, section: Int, start: Int, count: Int): Unit = throw notPatched()

    /** Makes the item at [index] of section 0 the current item, which starts playing it. */
    @JvmStatic
    fun queueSetCurrentIndex(queue: Any, index: Int): Unit = throw notPatched()

    @JvmStatic
    fun itemVideoId(item: Any): String? = throw notPatched()

    /** Null if unavailable for this item or app version. */
    @JvmStatic
    fun itemTitle(item: Any): String? = null

    /** The subtitle, usually the artist. Null if unavailable for this item or app version. */
    @JvmStatic
    fun itemArtist(item: Any): String? = null

    /** Parses InnerTube NavigationEndpoint bytes with the app's generated extension registry. */
    @JvmStatic
    fun parseCommand(bytes: ByteArray): Any? = throw notPatched()

    /**
     * Runs a parsed NavigationEndpoint with the resolver the app registered for it in
     * [commandMapping], exactly like a tap in the app.
     *
     * @return False if this mapping has no resolver for the command.
     */
    @JvmStatic
    fun executeCommand(commandMapping: Any, command: Any): Boolean = throw notPatched()

    private fun notPatched() = IllegalStateException("Queue API bridge method was not patched")
}
