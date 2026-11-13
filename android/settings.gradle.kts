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
    }
}

rootProject.name = "regie"

include(":domain", ":app")

// La voix vit dans un build Gradle séparé (ticket 04, ADR 01). Il est inclus
// en composite build dès que le dossier existe, sans rien changer ici.
if (file("../voice/settings.gradle.kts").exists()) {
    includeBuild("../voice")
}
