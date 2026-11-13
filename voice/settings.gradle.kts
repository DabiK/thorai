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
    // sherpa-onnx (Android AAR) is only published on JitPack. Restricted to that group.
    // A build that includes this one and depends on :local must declare the same repository.
    maven("https://jitpack.io") {
      content { includeGroup("com.github.k2-fsa.sherpa-onnx") }
    }
  }
}

rootProject.name = "regie-voice"

include(":api", ":fake", ":elevenlabs")

// Android modules need the SDK (sdk.dir in local.properties or ANDROID_HOME).
include(":local", ":demo")
