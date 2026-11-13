import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    // Lint d'Android sur un module JVM : `check` le lance aussi ici.
    alias(libs.plugins.android.lint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

lint {
    abortOnError = true
    warningsAsErrors = true
    // Les mises à jour de versions se décident dans un ADR, pas au fil des builds.
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
