package io.github.leobenzol.patches.queueapi

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.intOption
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

    val maxPerRequester by intOption(
        key = "maxPerRequester",
        default = 3,
        title = "Max waiting songs per person",
        description = "Further requests from a person are rejected until one of theirs plays. 0 allows any number.",
        required = true,
    ) { it != null && it >= 0 }

    val maxTotal by intOption(
        key = "maxTotal",
        default = 30,
        title = "Max waiting requests",
        description = "Requests allowed ahead of your own queue. 0 allows any number.",
        required = true,
    ) { it != null && it >= 0 }

    val maxDuration by intOption(
        key = "maxDuration",
        default = 600,
        title = "Max song length (seconds)",
        description = "Longer songs are rejected. 0 allows any length.",
        required = true,
    ) { it != null && it >= 0 }

    execute {
        optionFingerprint("token", "Ljava/lang/String;").method.returnEarly(token!!)
        optionFingerprint("eventsEnabled", "Z").method.returnEarly(events != false)
        optionFingerprint("maxPendingPerRequester", "I").method.returnEarly(maxPerRequester!!)
        optionFingerprint("maxPendingTotal", "I").method.returnEarly(maxTotal!!)
        optionFingerprint("maxDurationSeconds", "I").method.returnEarly(maxDuration!!)

        // The shared extension patch sets the context before this call, because it adds its hook
        // at the start of the same method later, in finalize.
        MusicActivityOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static { }, $EXTENSION_CLASS->onMusicActivityCreated()V",
        )

        val queue = findQueueTargets()
        val metadata = findMetadataTargets()
        val commands = findCommandTargets()
        Logger.getLogger(this::class.java.name).info(
            "Queue API: manager ${queue.managerType}, queue ${queue.queueField.type}, " +
                "metadata ${metadata?.type ?: "unavailable"}, commands ${commands.mappingType}",
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
        with(commands) {
            makePublic(mappingType)
            makePublic(endpointType)
            makePublic(endpointDefaultInstance)
            makePublic(parseFrom)
            makePublic(generatedRegistry)
            makePublic(findResolver.reference)
            makePublic(unresolved)
            makePublic(resolve.reference)
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

        with(commands) {
            bridge.replaceBody(
                "parseCommand", 2,
                """
                    sget-object v0, $endpointDefaultInstance
                    invoke-static { }, $generatedRegistry
                    move-result-object v1
                    invoke-static { v0, p0, v1 }, $parseFrom
                    move-result-object v0
                    return-object v0
                """,
            )
            bridge.replaceBody(
                "executeCommand", 2,
                """
                    check-cast p0, $mappingType
                    check-cast p1, $endpointType
                    ${findResolver.opcode} { p0, p1 }, ${findResolver.reference}
                    move-result-object v0
                    if-eqz v0, :unresolved
                    sget-object v1, $unresolved
                    if-eq v0, v1, :unresolved
                    new-instance v1, Ljava/util/HashMap;
                    invoke-direct { v1 }, Ljava/util/HashMap;-><init>()V
                    ${resolve.opcode} { v0, p1, v1 }, ${resolve.reference}
                    const/4 v0, 0x1
                    return v0
                    :unresolved
                    const/4 v0, 0x0
                    return v0
                """,
            )
        }

        hookConstructors(queue.managerType, "onQueueManagerCreated")
        hookConstructors(commands.mappingType, "onCommandMappingCreated")
    }
}
