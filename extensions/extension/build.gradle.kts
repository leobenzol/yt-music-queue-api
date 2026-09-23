plugins {
    alias(libs.plugins.protobuf)
}

extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.template.extension"
}

dependencies {
    // Provided at runtime by the shared extension.
    compileOnly(libs.morphe.extensions.library)

    implementation(libs.protobuf.javalite)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.android.json)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}
