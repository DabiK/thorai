import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.library)
}

// Free, offline voice with sherpa-onnx: Parakeet TDT (transcription), Silero (speech detection),
// Piper (synthesis). Models are downloaded on first use, never shipped in the repository or the APK.
android {
  namespace = "app.regie.voice.local"
  compileSdk = 36

  defaultConfig {
    minSdk = 33
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  // Recordings shared by the tests of every module.
  sourceSets.getByName("test").resources.directories.add(rootProject.file("testdata").path)
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
    allWarningsAsErrors.set(true)
    optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
  }
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

dependencies {
  api(project(":api"))
  implementation(libs.sherpa.onnx)
  implementation(libs.commons.compress)
  implementation(libs.okhttp)

  testImplementation(libs.kotlin.test.junit5)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.okhttp.mockwebserver3)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}
