package io.github.leobenzol.patches.queueapi

import app.morphe.patcher.Fingerprint

/*
 * The app's classes are found by strings that have been stable for years, and their members by
 * signatures and instruction shapes, never by obfuscated names.
 */

/** Logged by PlaybackQueueManager.unshuffle(). */
internal object QueueManagerFingerprint : Fingerprint(
    strings = listOf("Trying to call unshuffle on a non shuffleable queue."),
)

/** Logged while building the MediaSession queue, right after reading each item's title and subtitle. */
internal object MediaSessionQueueFingerprint : Fingerprint(
    strings = listOf("BT metadata: Set playing queue item: %s, %s, %s"),
)

internal object TokenFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "token",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
)

internal object EventsEnabledFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "eventsEnabled",
    returnType = "Z",
    parameters = listOf(),
)
