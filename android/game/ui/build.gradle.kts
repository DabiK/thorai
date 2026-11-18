// La vue jeu côté Android (ADR 24) : rendu pixel sur un Canvas Compose, atlas, kit pixel et polices.
// Il ne voit que le domaine et le moteur du jeu ; `android/app` reste la seule racine de composition.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.regie.game.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 33
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Captures Robolectric des écrans (ticket 49) : `-PgameCaptures=<dossier>`, sinon rien n'est écrit.
        val captures = providers.gradleProperty("gameCaptures").orNull
        unitTests.all { test -> captures?.let { test.systemProperty("game.captures", it) } }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = false
        // Les mises à jour de versions se décident dans un ADR, pas au fil des builds (et sans réseau).
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":domain"))
    api(project(":game-engine"))

    implementation(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    // Lecture des atlas et de la palette, sans plugin de sérialisation : un arbre JSON suffit.
    implementation(libs.kotlinx.serialization.json)
    // Le QR de connexion de Codex (ADR 18), calculé sur place : seulement l'adresse publique.
    implementation(libs.zxing.core)
    debugImplementation(libs.compose.ui.tooling)
    // Le bac à sable du banc (src/debug).
    debugImplementation(libs.androidx.activity.compose)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.activity.compose)

    constraints {
        // Même cycle de vie que l'app : celui qu'amène Compose (2.9) a des règles lint périmées.
        implementation(libs.androidx.lifecycle.runtime.compose)
    }
}
