import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.android.application) apply false
}

// Coordonnées utilisées par `includeBuild("../voice")` depuis android/ :
// app.regie.voice:api, app.regie.voice:fake, app.regie.voice:elevenlabs, app.regie.voice:local.
// Les modules Android (:local, :demo) se configurent dans leur propre build.gradle.kts.
subprojects {
  group = "app.regie.voice"
  version = "0.1.0"

  pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    extensions.configure<JavaPluginExtension> {
      sourceCompatibility = JavaVersion.VERSION_17
      targetCompatibility = JavaVersion.VERSION_17
    }
    extensions.configure<KotlinJvmProjectExtension> {
      compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
      }
    }
    // Le temps virtuel de kotlinx-coroutines-test est encore marqué expérimental.
    tasks.named<KotlinCompile>("compileTestKotlin") {
      compilerOptions.optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
    // Enregistrements WAV partagés par les tests de tous les modules.
    extensions.configure<SourceSetContainer> {
      named("test") { resources.srcDir(rootProject.file("testdata")) }
    }
    tasks.withType<Test>().configureEach {
      useJUnitPlatform()
    }
  }
}
