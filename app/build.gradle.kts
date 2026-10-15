import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Optional developer API key for DEBUG builds, read from the git-ignored local.properties:
//   anthropic.apiKey=sk-ant-...
// Release builds never contain it; users enter their own key in Settings.
val devAnthropicKey: String = Properties().run {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
    getProperty("anthropic.apiKey", "").trim()
}

android {
    namespace = "io.github.ckirby04.nemesis"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.ckirby04.nemesis"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "io.github.ckirby04.nemesis.NemesisTestRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEV_ANTHROPIC_KEY", "\"$devAnthropicKey\"")
        }
        release {
            buildConfigField("String", "DEV_ANTHROPIC_KEY", "\"\"")
            // Signed with the debug key so the class build installs anywhere; swap in a real
            // keystore before publishing to a store.
            signingConfig = signingConfigs.getByName("debug")
            // Whole-app R8. The template's package-scoped optimization (androidx/kotlin only) produced
            // a release APK that crashed at launch with IllegalAccessError inside Room.
            optimization {
                enable = true
            }
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
    packaging {
        // The Anthropic SDK's transitive deps (Jackson, Kotlin, OpenTelemetry) ship overlapping metadata.
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
                "META-INF/INDEX.LIST", "META-INF/*.kotlin_module", "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.anthropic.java)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
