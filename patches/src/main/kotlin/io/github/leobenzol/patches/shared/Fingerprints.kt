/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-patches/commit/724e6d61b2ecd868c1a9a37d465a688e83a74799
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package io.github.leobenzol.patches.shared

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

// Copied from Morphe Patches (app.morphe.patches.music.shared and music.misc.extension.hooks).

internal const val YOUTUBE_MUSIC_MAIN_ACTIVITY_CLASS_TYPE = "Lcom/google/android/apps/youtube/music/activities/MusicActivity;"

internal object MusicActivityOnCreateFingerprint : Fingerprint(
    definingClass = YOUTUBE_MUSIC_MAIN_ACTIVITY_CLASS_TYPE,
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;")
)

internal object YouTubeMusicApplicationInitFingerprint : Fingerprint(
    name = "onCreate",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        string("activity")
    )
)
