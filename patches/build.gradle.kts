group = "io.github.leobenzol"

patches {
    about {
        name = "YT Music Queue API"
        description = "Lets other apps, such as Tasker, control the YouTube Music queue"
        source = "git@github.com:leobenzol/yt-music-queue-api.git"
        author = "Leonardo Benini"
        contact = "https://github.com/leobenzol"
        website = "https://github.com/leobenzol/yt-music-queue-api"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
