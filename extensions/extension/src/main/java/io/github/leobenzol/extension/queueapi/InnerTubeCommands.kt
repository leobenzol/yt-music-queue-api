package io.github.leobenzol.extension.queueapi

import io.github.leobenzol.extension.queueapi.innertube.NavigationEndpoint
import io.github.leobenzol.extension.queueapi.innertube.QueueAddEndpoint
import io.github.leobenzol.extension.queueapi.innertube.QueueInsertPosition
import io.github.leobenzol.extension.queueapi.innertube.QueueTarget
import io.github.leobenzol.extension.queueapi.innertube.WatchEndpoint

/**
 * InnerTube commands for the app to run, encoded as NavigationEndpoint protos.
 *
 * Encoding them here means the extension does not depend on the app's obfuscated protobuf classes.
 * Null and empty ids are left out.
 */
object InnerTubeCommands {

    /** Adds a video or playlist to the queue, like "Play next" and "Add to queue". */
    fun queueAdd(videoId: String?, playlistId: String?, position: QueueInsertPosition): ByteArray {
        val target = QueueTarget.newBuilder()
            .setVideoId(videoId.orEmpty())
            .setPlaylistId(playlistId.orEmpty())
        val queueAdd = QueueAddEndpoint.newBuilder()
            .setQueueTarget(target)
            .setQueueInsertPosition(position)
        return NavigationEndpoint.newBuilder()
            .setQueueAddEndpoint(queueAdd)
            .build()
            .toByteArray()
    }

    /** Plays a video or playlist now. */
    fun watch(videoId: String?, playlistId: String?): ByteArray {
        val watch = WatchEndpoint.newBuilder()
            .setVideoId(videoId.orEmpty())
            .setPlaylistId(playlistId.orEmpty())
        return NavigationEndpoint.newBuilder()
            .setWatchEndpoint(watch)
            .build()
            .toByteArray()
    }
}
