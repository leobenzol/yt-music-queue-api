package io.github.leobenzol.extension.queueapi

import io.github.leobenzol.extension.queueapi.innertube.QueueInsertPosition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Base64

/** Compares against the bytes of the MVP's hand written encoder, which were checked on a device. */
class InnerTubeCommandsTest {
    private fun ByteArray.base64() = Base64.getEncoder().encodeToString(this)

    @Test
    fun `queueAdd with a video at the end`() = assertEquals(
        // NavigationEndpoint { 163162354: { 1: { 1: "BSTsnWoslP4" } 2: 2 } }
        "ko+17gQRCg0KC0JTVHNuV29zbFA0EAI=",
        InnerTubeCommands.queueAdd("BSTsnWoslP4", null, QueueInsertPosition.INSERT_AT_END).base64(),
    )

    @Test
    fun `queueAdd with a playlist after the current video`() = assertEquals(
        "ko+17gQVChESD1BMeHl6MTIzNDU2Nzg5MBAB",
        InnerTubeCommands.queueAdd(null, "PLxyz1234567890", QueueInsertPosition.INSERT_AFTER_CURRENT_VIDEO).base64(),
    )

    @Test
    fun `queueAdd leaves out empty ids`() = assertEquals(
        InnerTubeCommands.queueAdd("BSTsnWoslP4", null, QueueInsertPosition.INSERT_AT_END).base64(),
        InnerTubeCommands.queueAdd("BSTsnWoslP4", "", QueueInsertPosition.INSERT_AT_END).base64(),
    )

    @Test
    fun `watch a video`() = assertEquals(
        "6qjduQENCgtCU1Rzbldvc2xQNA==",
        InnerTubeCommands.watch("BSTsnWoslP4", null).base64(),
    )
}
