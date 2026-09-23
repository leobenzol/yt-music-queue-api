package io.github.leobenzol.patches.queueapi

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.util.returnEarly
import io.github.leobenzol.patches.shared.Constants.COMPATIBILITY_YOUTUBE_MUSIC
import io.github.leobenzol.patches.shared.MusicActivityOnCreateFingerprint
import io.github.leobenzol.patches.shared.sharedExtensionPatch
import java.util.logging.Logger

internal const val EXTENSION_CLASS = "Lio/github/leobenzol/extension/queueapi/QueueApiPatch;"
private const val BRIDGE_CLASS = "Lio/github/leobenzol/extension/queueapi/AppBridge;"

private val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{8,64}")

@Suppress("unused")
val queueApiPatch = bytecodePatch(
    name = "Queue API",
    description = "Lets other apps, such as Tasker, control the queue with broadcast intents.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_YOUTUBE_MUSIC)

    dependsOn(sharedExtensionPatch)

    extendWith("extensions/extension.mpe")

    val token by stringOption(
        key = "token",
        title = "Token",
        description = "Secret that every intent must include. 8 to 64 characters: letters, digits, - and _.",
        required = true,
    ) { it != null && TOKEN_PATTERN.matches(it) }

    val events by booleanOption(
        key = "events",
        default = true,
        title = "Now playing events",
        description = "Broadcast an event whenever the playing song changes.",
    )

    execute {
        TokenFingerprint.method.returnEarly(token!!)
        if (events == false) EventsEnabledFingerprint.method.returnEarly(false)

        // The shared extension patch sets the context before this call, because it adds its hook
        // at the start of the same method later, in finalize.
        MusicActivityOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static { }, $EXTENSION_CLASS->onMusicActivityCreated()V",
        )

        val queue = findQueueTargets()
        val metadata = findMetadataTargets()
        Logger.getLogger(this::class.java.name).info(
            "Queue API: manager ${queue.managerType}, queue ${queue.queueField.type}, " +
                "metadata ${metadata?.type ?: "unavailable"}",
        )

        with(queue) {
            makePublic(managerType)
            makePublic(queueField)
            listOf(size, currentIndex, item, move, remove, setCurrentIndex, itemVideoId).forEach { makePublic(it.reference) }
            makePublic(itemType)
        }
        metadata?.let {
            makePublic(it.type)
            makePublic(it.title.reference)
            makePublic(it.artist.reference)
        }

        val bridge = mutableClassDefBy(BRIDGE_CLASS)
        with(queue) {
            bridge.replaceBody(
                "getQueue", 1,
                """
                    check-cast p0, $managerType
                    iget-object v0, p0, $queueField
                    return-object v0
                """,
            )
            bridge.replaceBody(
                "queueSize", 1,
                """
                    check-cast p0, ${queueField.type}
                    ${size.opcode} { p0, p1 }, ${size.reference}
                    move-result v0
                    return v0
                """,
            )
            bridge.replaceBody(
                "queueCurrentIndex", 1,
                """
                    check-cast p0, ${queueField.type}
                    ${currentIndex.opcode} { p0 }, ${currentIndex.reference}
                    move-result v0
                    return v0
                """,
            )
            bridge.replaceBody(
                "queueItem", 1,
                """
                    check-cast p0, ${queueField.type}
                    ${item.opcode} { p0, p1, p2 }, ${item.reference}
                    move-result-object v0
                    return-object v0
                """,
            )
            bridge.replaceBody(
                "queueMove", 0,
                """
                    check-cast p0, ${queueField.type}
                    ${move.opcode} { p0, p1, p2, p3, p4 }, ${move.reference}
                    return-void
                """,
            )
            bridge.replaceBody(
                "queueRemove", 0,
                """
                    check-cast p0, ${queueField.type}
                    ${remove.opcode} { p0, p1, p2, p3 }, ${remove.reference}
                    return-void
                """,
            )
            bridge.replaceBody(
                "queueSetCurrentIndex", 0,
                """
                    check-cast p0, ${queueField.type}
                    ${setCurrentIndex.opcode} { p0, p1 }, ${setCurrentIndex.reference}
                    return-void
                """,
            )
            bridge.replaceBody(
                "itemVideoId", 1,
                """
                    check-cast p0, $itemType
                    ${itemVideoId.opcode} { p0 }, ${itemVideoId.reference}
                    move-result-object v0
                    return-object v0
                """,
            )
        }

        if (metadata != null) {
            for ((name, getter) in listOf("itemTitle" to metadata.title, "itemArtist" to metadata.artist)) {
                bridge.replaceBody(
                    name, 1,
                    """
                        instance-of v0, p0, ${metadata.type}
                        if-eqz v0, :unavailable
                        check-cast p0, ${metadata.type}
                        ${getter.opcode} { p0 }, ${getter.reference}
                        move-result-object v0
                        return-object v0
                        :unavailable
                        const/4 v0, 0x0
                        return-object v0
                    """,
                )
            }
        }

        hookConstructors(queue.managerType, "onQueueManagerCreated")
    }
}
