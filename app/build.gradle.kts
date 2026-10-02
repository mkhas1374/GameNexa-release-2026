plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
// alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  // Read versionCode and versionName dynamically from gradle.properties
  val appVersionCode = providers.gradleProperty("app.versionCode").get().toInt()
  val appVersionName = providers.gradleProperty("app.versionName").get()

  defaultConfig {
    applicationId = "com.MinmKhas.studio.GameNexa.wrtx"
    minSdk = 24
    targetSdk = 36
    versionCode = appVersionCode
    versionName = appVersionName

    ndk {
      abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
    }

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  lint {
    checkReleaseBuilds = true
    abortOnError = true
    checkDependencies = true
  }

  signingConfigs {
    create("release") {
      val isReleaseTaskRequested = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

      val keystorePath = (project.findProperty("GAMENEXA_KEYSTORE_PATH") as? String)
        ?: System.getenv("GAMENEXA_KEYSTORE_PATH")

      val storePwd = (project.findProperty("GAMENEXA_STORE_PASSWORD") as? String)
        ?: System.getenv("GAMENEXA_STORE_PASSWORD")

      val keyAliasName = (project.findProperty("GAMENEXA_KEY_ALIAS") as? String)
        ?: System.getenv("GAMENEXA_KEY_ALIAS")

      val keyPwd = (project.findProperty("GAMENEXA_KEY_PASSWORD") as? String)
        ?: System.getenv("GAMENEXA_KEY_PASSWORD")

      val keystoreFile = keystorePath?.let { path ->
        val candidate = File(path)
        if (candidate.isAbsolute) candidate else rootProject.file(path)
      }

      val hasValidCredentials = keystoreFile != null &&
        keystoreFile.exists() &&
        !storePwd.isNullOrBlank() &&
        !keyAliasName.isNullOrBlank() &&
        !keyPwd.isNullOrBlank()

      if (hasValidCredentials) {
        storeFile = keystoreFile
        storePassword = storePwd
        keyAlias = keyAliasName
        keyPassword = keyPwd
        enableV1Signing = true
        enableV2Signing = true
      } else if (isReleaseTaskRequested) {
        throw GradleException("Release signing credentials are missing")
      }
    }

  }

  buildTypes {
    release {
      isDebuggable = false
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Use AGP's standard debug signing configuration; CI/Android Studio provide the debug keystore.
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
// secrets {
//   propertiesFileName = ".env"
//   defaultPropertiesFileName = ".env.example"
// }


// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.browser)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.play.services.appset)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  // implementation(project(":admin"))
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

