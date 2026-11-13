import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

// Trial app of the voice (ticket 04): dictation with L2, provider switch, speech, measures and an
// adb-driven bench. Composition root: the only module allowed to import every adapter.
android {
  namespace = "app.regie.voicelab"
  compileSdk = 36

  defaultConfig {
    applicationId = "app.regie.voicelab"
    minSdk = 33 // the Thor runs Android 13
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
    // The Thor is arm64: the other ABIs of sherpa-onnx would only weigh on the APK.
    ndk { abiFilters += "arm64-v8a" }
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
  }

  // The corpus (consignes.txt, termes.txt) is packaged as is: one source, no copy by hand.
  sourceSets.getByName("main").assets.directories.add(rootProject.file("corpus").path)
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
  implementation(project(":api"))
  implementation(project(":elevenlabs"))
  implementation(project(":local"))
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.androidx.activity.compose)

  testImplementation(project(":fake"))
  testImplementation(libs.kotlin.test.junit5)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}
