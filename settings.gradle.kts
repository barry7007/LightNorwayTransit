pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            name = "JitPack"
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "LightNorwayTransit"

includeBuild("plugin")
include(":lint-rules")
include(":sdk:shared")
include(":sdk:ui")
include(":sdk:client")
include(":sdk:server")
include(":sdk:emulator")
include(":tool")
include(":examples:ui-demo")
project(":examples:ui-demo").projectDir = file("examples/ui-demo")
include(":examples:weather")
project(":examples:weather").projectDir = file("examples/weather")
include(":examples:authenticator")
project(":examples:authenticator").projectDir = file("examples/authenticator")
include(":examples:audio-demo")
project(":examples:audio-demo").projectDir = file("examples/audio-demo")

// The app this repo exists for. Kept under examples/ so it sits where the SDK expects a tool
// and stays trivially mergeable with upstream, which never touches this path.
include(":examples:Sanntid")
project(":examples:Sanntid").projectDir = file("examples/Sanntid")
