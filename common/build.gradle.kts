plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.shilapi.xcertplay.host"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 25
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    lint {
        // ar/ru/uk/es lag behind the default strings; they fall back to English at runtime.
        // zh-TW coverage is enforced by TraditionalChineseTranslationsTest instead.
        disable += "MissingTranslation"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // The UI suite covers several SDKs and locale-specific resource sandboxes. 1g sat right at the
        // ceiling: adding one more Robolectric class pushed a full run into OutOfMemoryError in
        // unrelated tests, on CI and locally both, which is what BydSettingsDecoyTest measured.
        unitTests.all {
            it.maxHeapSize = "2g"
            // One big Robolectric JVM accumulates hundreds of sandboxes; after roughly two
            // dozen classes a worker thread can deadlock on Robolectric's class instrumentation
            // (CarHotspotSwitchTest awaitGrant). Forking every 20 classes keeps each JVM young
            // and the suite deterministic.
            it.forkEvery = 20
        }
    }
}

dependencies {
    api(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation(libs.jmdns)
}
