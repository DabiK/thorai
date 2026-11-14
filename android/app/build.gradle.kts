plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.regie"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.regie"
        minSdk = 33
        ndk { abiFilters += setOf("arm64-v8a", "x86_64") }
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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
        // OldTargetApi : la cible suit compileSdk 36, le plus haut SDK installé (ADR 02).
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
    implementation(libs.voice.api)
    implementation(libs.voice.local)
    implementation(libs.voice.elevenlabs)
    implementation(project(":gateway-bridge"))
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
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
