import com.google.protobuf.gradle.*

plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

dependencies {
    implementation(libs.protobuf.kotlin.lite)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                named("java") {
                    option("lite")
                }
                id("kotlin") {
                    option("lite")
                }
            }
        }
    }
}

sourceSets {
    main {
        proto {
            srcDir("../composeApp/src/commonMain/proto")
        }
    }
}
