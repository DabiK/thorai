import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Adapter du port AgentGateway vers le pont (ADR 06) : Kotlin JVM pur, comme le domaine.
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
    api(project(":domain"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    // Lignes ajoutées et retirées d'un diff (ADR 07).


    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // Les enregistrements du pont : fournis à l'exécution par le debug de `:app`, ici par les tests.

}
