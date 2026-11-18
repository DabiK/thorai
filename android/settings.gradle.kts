pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") { content { includeGroup("com.github.k2-fsa.sherpa-onnx") } }
    }
}

rootProject.name = "regie"

include(":command-model", ":domain", ":gateway-bridge", ":bridge-fixtures", ":app")

// La vue jeu (ticket 40) vit à part, dans android/game/.
include(":game-engine")
project(":game-engine").projectDir = file("game/engine")

// La voix vit dans un build Gradle séparé (ticket 04, ADR 01). Il est inclus
// en composite build dès que le dossier existe, sans rien changer ici.
if (file("../voice/settings.gradle.kts").exists()) {
    includeBuild("../voice")
}
