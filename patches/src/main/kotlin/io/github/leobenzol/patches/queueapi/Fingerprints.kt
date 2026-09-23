package io.github.leobenzol.patches.queueapi

import app.morphe.patcher.Fingerprint

internal object TokenFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "token",
    returnType = "Ljava/lang/String;",
    parameters = listOf(),
)
