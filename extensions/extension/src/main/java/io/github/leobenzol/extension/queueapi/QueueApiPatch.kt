package io.github.leobenzol.extension.queueapi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import app.morphe.extension.shared.Logger
import app.morphe.extension.shared.Utils
import java.security.MessageDigest
import java.util.UUID

/**
 * Lets other apps control the queue with broadcast intents.
 *
 * Every intent must include the token set when patching, and is answered with one [ACTION_RESULT]
 * broadcast.
 */
@Suppress("unused")
object QueueApiPatch {
    const val API_VERSION = 1

    private const val PREFIX = "io.github.leobenzol.queueapi."

    /** The actions of the API. Each one is handled from the step that adds it. */
    private val ACTIONS = listOf(
        "REQUEST", "ADD", "PLAY", "MOVE", "REMOVE", "JUMP", "CLEAR", "GET_QUEUE", "EXECUTE",
    ).map { PREFIX + it }

    const val ACTION_RESULT = PREFIX + "RESULT"

    const val EXTRA_TOKEN = "token"
    const val EXTRA_REQUEST_ID = "request_id"
    const val EXTRA_REPLY_PACKAGE = "reply_package"
    const val EXTRA_API_VERSION = "api_version"
    const val EXTRA_ACTION = "action"
    const val EXTRA_STATUS = "status"
    const val EXTRA_MESSAGE = "message"

    const val STATUS_BAD_REQUEST = "BAD_REQUEST"

    /** Receives results when a request does not name a reply package. */
    private const val DEFAULT_REPLY_PACKAGE = "net.dinglisch.android.taskerm"

    private var receiverRegistered = false

    /** Returns the token patch option. The patch replaces this method's body. */
    @JvmStatic
    fun token(): String = ""

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
        Logger.printInfo { "Queue API ready (api_version $API_VERSION)" }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                if (!isAuthorized(token(), intent.getStringExtra(EXTRA_TOKEN))) {
                    Logger.printInfo { "Rejected ${intent.action}: invalid token" }
                    return
                }
                reply(context, intent, STATUS_BAD_REQUEST, "Unsupported action")
            } catch (ex: Exception) {
                Logger.printException({ "onReceive failure" }, ex)
            }
        }
    }

    /** An empty [expected] token, which means the patch set none, rejects every request. */
    internal fun isAuthorized(expected: String, given: String?): Boolean =
        expected.isNotEmpty() && given != null && MessageDigest.isEqual(expected.toByteArray(), given.toByteArray())

    private fun reply(context: Context, request: Intent, status: String, message: String) {
        val action = request.action.orEmpty().removePrefix(PREFIX)
        val requestId = request.getStringExtra(EXTRA_REQUEST_ID)?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().take(8)
        Logger.printInfo { "RESULT $action $requestId $status: $message" }

        val replyPackage =
            if (request.hasExtra(EXTRA_REPLY_PACKAGE)) request.getStringExtra(EXTRA_REPLY_PACKAGE) else DEFAULT_REPLY_PACKAGE
        val result = Intent(ACTION_RESULT)
            .putExtra(EXTRA_API_VERSION, API_VERSION)
            .putExtra(EXTRA_ACTION, action)
            .putExtra(EXTRA_REQUEST_ID, requestId)
            .putExtra(EXTRA_STATUS, status)
            .putExtra(EXTRA_MESSAGE, message)
        if (!replyPackage.isNullOrEmpty()) result.setPackage(replyPackage)
        context.sendBroadcast(result)
    }
}
