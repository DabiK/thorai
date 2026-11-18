import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releasePropertiesFile = providers.environmentVariable("REGIE_RELEASE_CONFIG")
    .map { file(it) }.getOrElse(File(System.getProperty("user.home"), ".config/regie/release.properties"))
val releaseProperties = Properties().apply {
    if (releasePropertiesFile.isFile) releasePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "app.regie"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.regie"
        minSdk = 33
        ndk { abiFilters += setOf("arm64-v8a", "x86_64") }
        targetSdk = 36
        versionCode = providers.gradleProperty("regieVersionCode").getOrElse("1001").toInt()
        versionName = providers.gradleProperty("regieVersionName").getOrElse("0.1.0")
    }

    signingConfigs {
        create("release") {
            if (releasePropertiesFile.isFile) {
                storeFile = file(releaseProperties.getProperty("storeFile"))
                storePassword = releaseProperties.getProperty("storePassword")
                keyAlias = releaseProperties.getProperty("keyAlias")
                keyPassword = releaseProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Ticket 50 (ADR 22) : la version jeu s'installe à côté de l'app classique, jamais
        // par-dessus. Un suffixe, pas un identifiant écrit en dur : il suit le renommage de l'app.
        configureEach { applicationIdSuffix = ".jeu" }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }

    // Son nom et celui de son mode console, distincts dans le lanceur et dans Accessibilité.
    sourceSets {
        getByName("debug") { res.srcDir("src/jeu/res") }
        getByName("release") { res.srcDir("src/jeu/res") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = false
        // Les mises à jour de versions se décident dans un ADR, pas au fil des builds (et sans réseau).
        // OldTargetApi : la cible suit compileSdk 36, le plus haut SDK installé (ADR 05).
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
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
    implementation(project(":command-model"))
    implementation(libs.voice.api)
    implementation(libs.voice.local)
    implementation(libs.voice.elevenlabs)
    implementation(project(":gateway-bridge"))
    // La vue jeu (ticket 41) : rendu pixel et kit, pas encore branchés sur les écrans.
    implementation(project(":game-ui"))
    // Les enregistrements rejoués par la passerelle factice, qui n'est choisissable qu'en debug.
    debugImplementation(project(":bridge-fixtures"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    // Le texte des agents est en markdown : analysé ici, rendu en Compose (ADR 07).
    implementation(libs.commonmark)
    implementation(libs.zxing.core)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
