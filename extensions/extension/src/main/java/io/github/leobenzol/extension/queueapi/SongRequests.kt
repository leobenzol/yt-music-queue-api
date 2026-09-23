package io.github.leobenzol.extension.queueapi

/** The limits of song requests, set as patch options. 0 turns a limit off. */
data class RequestLimits(val maxPerRequester: Int, val maxTotal: Int, val maxDurationSeconds: Int)

/** The checks and messages of REQUEST. */
object SongRequests {

    data class Rejection(val status: String, val message: String)

    /**
     * @param pending Requests that have not played yet.
     * @return Why [song] cannot be requested by [requester], or null if it can.
     */
    fun check(song: MusicSearch.Result, requester: String, pending: List<RequestOrdering.Request>, limits: RequestLimits): Rejection? {
        if (limits.maxDurationSeconds > 0 && song.durationSeconds > limits.maxDurationSeconds) {
            return Rejection(QueueApiPatch.STATUS_TOO_LONG, "\"${song.title}\" is too long")
        }
        pending.firstOrNull { it.videoId == song.videoId }?.let {
            return Rejection(QueueApiPatch.STATUS_DUPLICATE, "\"${it.title}\" is already queued")
        }
        val byRequester = pending.count { it.requester == requester }
        if (limits.maxPerRequester > 0 && byRequester >= limits.maxPerRequester) {
            return Rejection(QueueApiPatch.STATUS_LIMIT_REQUESTER, "$requester already has $byRequester songs waiting")
        }
        if (limits.maxTotal > 0 && pending.size >= limits.maxTotal) {
            return Rejection(QueueApiPatch.STATUS_LIMIT_TOTAL, "The request queue is full")
        }
        return null
    }

    /** @param position Places after the playing song, 0 if it plays now. */
    fun queuedMessage(position: Int, title: String, artist: String?): String {
        val song = if (artist.isNullOrEmpty()) "\"$title\"" else "\"$title\" by $artist"
        return when {
            position <= 0 -> "Playing $song now"
            position == 1 -> "$song plays next"
            else -> "$song is queued, #$position in line"
        }
    }
}

/**
 * Recognizes a request that arrives twice. Chat apps post notifications again (updates,
 * reconnects), so the same message can be forwarded twice.
 *
 * An explicit dedupe key is exact. Without one, the same song from the same requester within
 * [windowMillis] counts as the same message. Not thread safe.
 */
class RedeliveryFilter(
    private val windowMillis: Long = 120_000,
    private val maxKeys: Int = 1000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Key to when it was last seen. */
    private val seen = object : LinkedHashMap<String, Long>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > maxKeys
    }

    fun isRedelivery(dedupeKey: String?, requester: String, song: String): Boolean {
        val now = clock()
        val last = seen.put(dedupeKey ?: "$requester\n$song", now) ?: return false
        return dedupeKey != null || now - last < windowMillis
    }
}
