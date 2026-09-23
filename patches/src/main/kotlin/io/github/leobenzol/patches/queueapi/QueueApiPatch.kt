package io.github.leobenzol.patches.queueapi

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.stringOption
import app.morphe.util.returnEarly
import io.github.leobenzol.patches.shared.Constants.COMPATIBILITY_YOUTUBE_MUSIC
import io.github.leobenzol.patches.shared.MusicActivityOnCreateFingerprint
import io.github.leobenzol.patches.shared.sharedExtensionPatch

internal const val EXTENSION_CLASS = "Lio/github/leobenzol/extension/queueapi/QueueApiPatch;"

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

    execute {
        TokenFingerprint.method.returnEarly(token!!)

        // The shared extension patch sets the context before this call, because it adds its hook
        // at the start of the same method later, in finalize.
        MusicActivityOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static { }, $EXTENSION_CLASS->onMusicActivityCreated()V",
        )
    }
}
