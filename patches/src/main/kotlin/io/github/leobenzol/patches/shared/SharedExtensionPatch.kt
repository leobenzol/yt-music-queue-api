package io.github.leobenzol.patches.shared

import app.morphe.patches.all.misc.extension.ExtensionHook
import app.morphe.patches.all.misc.extension.sharedExtensionPatch

/**
 * Adds the shared extension (the Morphe extensions library) and gives it the app context, the same
 * way Morphe Patches does for YouTube Music.
 */
val sharedExtensionPatch = sharedExtensionPatch(
    ExtensionHook(YouTubeMusicApplicationInitFingerprint),
    ExtensionHook(MusicActivityOnCreateFingerprint),
)
