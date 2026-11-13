plugins {
  alias(libs.plugins.kotlin.jvm)
}

// Kotlin pur : coroutines et rien d'autre. Ni Android, ni réseau.
dependencies {
  api(libs.kotlinx.coroutines.core)

  testImplementation(libs.kotlin.test)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}

// Le test d'architecture lit les sources de tous les modules du build.
tasks.test {
  inputs.files(
    rootProject.fileTree(rootDir) {
      include("*/src/main/**/*.kt", "*/build.gradle.kts")
      exclude("build/**", ".gradle/**")
    },
  ).withPropertyName("allMainSources")
}
