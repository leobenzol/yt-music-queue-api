package io.github.leobenzol.extension.queueapi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.Utils
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Lets other apps control the queue with broadcast intents.
 *
 * Every intent must include the token set when patching, and is answered with one [ACTION_RESULT]
 * broadcast. Intents are handled one at a time on a worker thread, in arrival order, and queue
 * reads and writes happen on the main thread.
 */
@Suppress("unused")
object QueueApiPatch {
    const val API_VERSION = 1

    private const val PREFIX = "io.github.leobenzol.queueapi."

    const val ACTION_MOVE = PREFIX + "MOVE"
    const val ACTION_REMOVE = PREFIX + "REMOVE"
    const val ACTION_JUMP = PREFIX + "JUMP"
    const val ACTION_CLEAR = PREFIX + "CLEAR"
    const val ACTION_GET_QUEUE = PREFIX + "GET_QUEUE"

    /** The actions of the API. Each one is handled from the step that adds it. */
    private val ACTIONS = listOf(
        "REQUEST", "ADD", "PLAY", "MOVE", "REMOVE", "JUMP", "CLEAR", "GET_QUEUE", "EXECUTE",
    ).map { PREFIX + it }

    const val ACTION_RESULT = PREFIX + "RESULT"
    const val ACTION_EVENT = PREFIX + "EVENT"

    // Request extras.
    const val EXTRA_TOKEN = "token"
    const val EXTRA_REQUEST_ID = "request_id"
    const val EXTRA_REPLY_PACKAGE = "reply_package"
    const val EXTRA_LIMIT = "limit"
    const val EXTRA_FROM = "from"
    const val EXTRA_TO = "to"
    const val EXTRA_COUNT = "count"
    const val EXTRA_SCOPE = "scope"

    // Result and event extras.
    const val EXTRA_API_VERSION = "api_version"
    const val EXTRA_ACTION = "action"
    const val EXTRA_STATUS = "status"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_EVENT = "event"
    // Also request extras.
    const val EXTRA_INDEX = "index"
    const val EXTRA_VIDEO_ID = "video_id"

    const val EXTRA_POSITION = "position"
    const val EXTRA_CURRENT_INDEX = "current_index"
    const val EXTRA_SIZE = "size"
    const val EXTRA_TITLE = "title"
    const val EXTRA_ARTIST = "artist"
    const val EXTRA_REQUESTER = "requester"
    const val EXTRA_QUEUE_JSON = "queue_json"
    const val EXTRA_QUEUE_TEXT = "queue_text"

    const val EVENT_NOW_PLAYING = "NOW_PLAYING"

    const val STATUS_OK = "OK"
    const val STATUS_NOT_FOUND = "NOT_FOUND"
    const val STATUS_BAD_REQUEST = "BAD_REQUEST"
    const val STATUS_UNAVAILABLE = "UNAVAILABLE"
    const val STATUS_ERROR = "ERROR"

    /** Receives results when a request does not name a reply package. */
    private const val DEFAULT_REPLY_PACKAGE = "net.dinglisch.android.taskerm"
    private const val DEFAULT_QUEUE_LIMIT = 50
    private const val MAIN_THREAD_TIMEOUT_SECONDS = 5L
    private const val NOW_PLAYING_POLL_MILLISECONDS = 1_000L

    @Volatile
    private var queueManager = WeakReference<Any>(null)
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    // Main thread only.
    private val ordering = RequestOrdering()
    private var receiverRegistered = false
    private var lastNowPlayingItem: Any? = null

    // region Patch options

    /** Returns the token patch option. The patch replaces this method's body. */
    @JvmStatic
    fun token(): String = ""

    /** Returns the events patch option. The patch replaces this method's body if events are off. */
    @JvmStatic
    fun eventsEnabled(): Boolean = true

    // endregion

    // region Injection points

    /** Injection point: end of every PlaybackQueueManager constructor. */
    @JvmStatic
    fun onQueueManagerCreated(queueManager: Any) {
        this.queueManager = WeakReference(queueManager)
        Logger.printDebug { "Queue manager: $queueManager" }
    }

    /** Injection point: start of MusicActivity.onCreate, after the shared extension got the context. */
    @JvmStatic
    fun onMusicActivityCreated() {
        if (receiverRegistered) return
        val context = Utils.getContext()?.applicationContext ?: return

        val filter = IntentFilter().apply { ACTIONS.forEach(::addAction) }
        // Registered at runtime, so implicit broadcasts arrive while the app runs, which it does
        // while music plays.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
        if (eventsEnabled()) mainHandler.postDelayed(nowPlayingWatcher, NOW_PLAYING_POLL_MILLISECONDS)
        Logger.printInfo { "Queue API ready (api_version $API_VERSION)" }
    }

    // endregion

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                if (!isAuthorized(token(), intent.getStringExtra(EXTRA_TOKEN))) {
                    Logger.printInfo { "Rejected ${intent.action}: invalid token" }
                    return
                }
                val request = Request(intent)
                worker.execute { handle(request) }
            } catch (ex: Exception) {
                Logger.printException({ "onReceive failure" }, ex)
            }
        }
    }

    /** An empty [expected] token, which means the patch set none, rejects every request. */
    internal fun isAuthorized(expected: String, given: String?): Boolean =
        expected.isNotEmpty() && given != null && MessageDigest.isEqual(expected.toByteArray(), given.toByteArray())

    // region Actions (worker thread)

    private fun handle(request: Request) {
        try {
            when (request.action) {
                ACTION_MOVE -> move(request)
                ACTION_REMOVE -> remove(request)
                ACTION_JUMP -> jump(request)
                ACTION_CLEAR -> clear(request)
                ACTION_GET_QUEUE -> getQueue(request)
                else -> reply(request, STATUS_BAD_REQUEST, "Unsupported action")
            }
        } catch (ex: Exception) {
            Logger.printException({ "${request.action} failure" }, ex)
            reply(request, STATUS_ERROR, "Something went wrong: $ex")
        }
    }

    private fun move(request: Request) {
        val from = request.intExtra(EXTRA_FROM, -1)
        val to = request.intExtra(EXTRA_TO, -1)
        onMain {
            val queue = queue(request) ?: return@onMain
            if (!QueueEdits.isValidIndex(queue, from) || !QueueEdits.isValidIndex(queue, to)) {
                reply(request, STATUS_BAD_REQUEST, "from and to must be ${indexRange(queue)}")
                return@onMain
            }
            queue.move(from, to)
            reply(request, STATUS_OK, "Moved item $from to $to", itemExtras(queue, to))
        }
    }

    private fun remove(request: Request) {
        val videoId = request.stringExtra(EXTRA_VIDEO_ID)
        onMain {
            val queue = queue(request) ?: return@onMain
            var index = request.intExtra(EXTRA_INDEX, -1)
            if (request.stringExtra(EXTRA_INDEX) == null && !videoId.isNullOrEmpty()) {
                index = QueueEdits.upcomingIndexOf(queue, videoId)
            }
            if (!QueueEdits.isValidIndex(queue, index)) {
                reply(request, STATUS_NOT_FOUND, "No such queue item")
                return@onMain
            }
            val extras = itemExtras(queue, index)
            val removed = QueueEdits.remove(queue, index, request.intExtra(EXTRA_COUNT, 1))
            extras.putInt(EXTRA_COUNT, removed)
            reply(request, STATUS_OK, "Removed $removed item(s) at index $index", extras)
        }
    }

    private fun jump(request: Request) {
        val index = request.intExtra(EXTRA_INDEX, -1)
        onMain {
            val queue = queue(request) ?: return@onMain
            if (!QueueEdits.isValidIndex(queue, index)) {
                reply(request, STATUS_BAD_REQUEST, "index must be ${indexRange(queue)}")
                return@onMain
            }
            queue.jump(index)
            reply(request, STATUS_OK, "Playing item $index", itemExtras(queue, index))
        }
    }

    private fun clear(request: Request) {
        val scope = request.stringExtra(EXTRA_SCOPE)?.lowercase() ?: "requests"
        onMain {
            val queue = queue(request) ?: return@onMain
            val removed = when (scope) {
                "requests" -> QueueEdits.clearRequests(queue, ordering)
                "upcoming" -> QueueEdits.clearUpcoming(queue)
                else -> {
                    reply(request, STATUS_BAD_REQUEST, "scope must be requests or upcoming")
                    return@onMain
                }
            }
            reply(request, STATUS_OK, "Removed $removed item(s)", Bundle().apply { putInt(EXTRA_COUNT, removed) })
        }
    }

    private fun getQueue(request: Request) {
        val limit = request.intExtra(EXTRA_LIMIT, DEFAULT_QUEUE_LIMIT).coerceAtLeast(1)
        onMain {
            val queue = queue(request) ?: return@onMain
            val current = queue.currentIndex
            val size = queue.size
            val items = (maxOf(0, current) until size).take(limit).map { queueItem(queue.itemAt(it), it) }
            val autoplay = (0 until queue.autoplaySize).take(limit).map { queueItem(queue.autoplayItemAt(it), it) }
            val playing = current in 0 until size

            val extras = if (playing) itemExtras(items.first(), current) else Bundle()
            extras.putInt(EXTRA_CURRENT_INDEX, current)
            extras.putInt(EXTRA_SIZE, size)
            extras.putString(EXTRA_QUEUE_JSON, QueueFormat.json(API_VERSION, current, size, items, autoplay))
            extras.putString(EXTRA_QUEUE_TEXT, QueueFormat.text(current, items))
            val message =
                if (playing) "Now playing \"${items.first().title.orEmpty()}\", ${size - current - 1} queued"
                else "Queue is empty"
            reply(request, STATUS_OK, message, extras)
        }
    }

    // endregion

    // region Queue (main thread)

    private fun currentQueue() = queueManager.get()?.let(PlayerQueue::of)

    /** The queue, or null after replying UNAVAILABLE if the player is not ready. */
    private fun queue(request: Request): PlayerQueue? {
        val queue = currentQueue()
        if (queue == null) reply(request, STATUS_UNAVAILABLE, "Music app is not ready")
        return queue
    }

    private fun queueItem(item: Any, index: Int): QueueItem {
        val request = ordering.requestFor(item)
        return QueueItem(
            index = index,
            videoId = AppBridge.itemVideoId(item),
            title = AppBridge.itemTitle(item) ?: request?.title,
            artist = AppBridge.itemArtist(item),
            requester = request?.requester,
        )
    }

    private fun indexRange(queue: PlayerQueue) =
        if (queue.size == 0) "a queue index, but the queue is empty" else "between 0 and ${queue.size - 1}"

    private fun itemExtras(queue: PlayerQueue, index: Int) =
        itemExtras(queueItem(queue.itemAt(index), index), queue.currentIndex)

    private fun itemExtras(item: QueueItem, currentIndex: Int) = Bundle().apply {
        putInt(EXTRA_INDEX, item.index)
        putInt(EXTRA_POSITION, item.index - currentIndex)
        putInt(EXTRA_CURRENT_INDEX, currentIndex)
        item.videoId?.let { putString(EXTRA_VIDEO_ID, it) }
        item.title?.let { putString(EXTRA_TITLE, it) }
        item.artist?.let { putString(EXTRA_ARTIST, it) }
        item.requester?.let { putString(EXTRA_REQUESTER, it) }
    }

    /** Runs [task] on the main thread and waits for it. */
    private fun onMain(task: () -> Unit) {
        val future = FutureTask<Unit> { task() }
        Utils.runOnMainThread(future)
        future.get(MAIN_THREAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    /** Sends NOW_PLAYING events when the playing item changes. */
    private val nowPlayingWatcher = object : Runnable {
        override fun run() {
            try {
                val queue = currentQueue()
                if (queue != null) {
                    val current = queue.currentIndex
                    val item = if (current in 0 until queue.size) queue.itemAt(current) else null
                    if (item != null && item !== lastNowPlayingItem) {
                        lastNowPlayingItem = item
                        broadcastNowPlaying(queueItem(item, current), current)
                    }
                }
            } catch (ex: Exception) {
                Logger.printException({ "Now playing watcher failure" }, ex)
            }
            mainHandler.postDelayed(this, NOW_PLAYING_POLL_MILLISECONDS)
        }
    }

    private fun broadcastNowPlaying(item: QueueItem, currentIndex: Int) {
        val context = Utils.getContext() ?: return
        Logger.printInfo { "EVENT $EVENT_NOW_PLAYING index $currentIndex" }
        context.sendBroadcast(
            Intent(ACTION_EVENT)
                .putExtras(itemExtras(item, currentIndex))
                .putExtra(EXTRA_API_VERSION, API_VERSION)
                .putExtra(EXTRA_EVENT, EVENT_NOW_PLAYING),
        )
    }

    // endregion

    private fun reply(request: Request, status: String, message: String, extras: Bundle? = null) {
        Logger.printInfo { "RESULT ${request.shortAction} ${request.requestId} $status: $message" }
        val context = Utils.getContext() ?: return
        val result = Intent(ACTION_RESULT)
        extras?.let { result.putExtras(it) }
        result.putExtra(EXTRA_API_VERSION, API_VERSION)
            .putExtra(EXTRA_ACTION, request.shortAction)
            .putExtra(EXTRA_REQUEST_ID, request.requestId)
            .putExtra(EXTRA_STATUS, status)
            .putExtra(EXTRA_MESSAGE, message)
        request.replyPackage?.takeIf { it.isNotEmpty() }?.let { result.setPackage(it) }
        context.sendBroadcast(result)
    }

    /** An API intent. Extras may be strings (Tasker, adb) or typed values (apps). */
    private class Request(intent: Intent) {
        val action: String = intent.action.orEmpty()
        val shortAction = action.removePrefix(PREFIX)
        private val extras: Bundle? = intent.extras
        val requestId = stringExtra(EXTRA_REQUEST_ID)?.takeIf { it.isNotEmpty() } ?: UUID.randomUUID().toString().take(8)
        val replyPackage: String? =
            if (extras?.containsKey(EXTRA_REPLY_PACKAGE) == true) stringExtra(EXTRA_REPLY_PACKAGE) else DEFAULT_REPLY_PACKAGE

        @Suppress("DEPRECATION")
        fun stringExtra(key: String): String? = extras?.get(key)?.toString()

        fun intExtra(key: String, fallback: Int) = stringExtra(key)?.trim()?.toIntOrNull() ?: fallback
    }
}
