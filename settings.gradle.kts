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
include(":tool")

// The tool builder passes -DlightSdk.toolOnly=true to configure only what
// :tool needs.
if (providers.systemProperty("lightSdk.toolOnly").orNull != "true") {
    include(":sdk:trust")
    include(":sdk:server")
    include(":sdk:emulator")
    include(":examples:ui-demo")
    project(":examples:ui-demo").projectDir = file("examples/ui-demo")
    include(":examples:tool-manager-demo")
    project(":examples:tool-manager-demo").projectDir = file("examples/tool-manager-demo")
    include(":examples:weather")
    project(":examples:weather").projectDir = file("examples/weather")
    include(":examples:authenticator")
    project(":examples:authenticator").projectDir = file("examples/authenticator")
    include(":examples:audio-demo")
    project(":examples:audio-demo").projectDir = file("examples/audio-demo")
}

// Local-only: build the Norwegian light-keyboard fork in-place and substitute it for the
// published artifact, exactly as light-sdk's own settings.gradle.kts does. Guarded on the
// sibling checkout existing, so it is a no-op for anyone who clones this repo alone —
// including the release workflow, which runs on ubuntu-latest with a bare
// actions/checkout of this repo and therefore keeps shipping the stock keyboard.
//
// THE COORDINATE MUST MATCH gradle/libs.versions.toml's `light-keyboard`
// (com.github.lightphone:light-keyboard). If they drift, this silently does nothing and
// the build quietly falls back to Light's stock keyboard.
//
// Caveat worth remembering: a release built BY HAND on a machine that has ../light-keyboard
// would embed the fork. Public releases must come from CI.
if (file("../light-keyboard").exists()) {
    includeBuild("../light-keyboard") {
        dependencySubstitution {
            substitute(module("com.github.lightphone:light-keyboard")).using(project(":ui"))
        }
    }
}
