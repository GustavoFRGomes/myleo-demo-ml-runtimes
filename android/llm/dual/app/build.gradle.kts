import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// onnxruntime-genai ships its Android AAR as a GitHub release asset (it is not
// on Maven Central), so a Gradle task fetches it into app/libs (gitignored)
// before the compile classpath is resolved.
val genaiAarFile = layout.projectDirectory.file("libs/onnxruntime-genai-android-0.17.0.aar")
val downloadGenAiAar = tasks.register("downloadGenAiAar") {
    val target = genaiAarFile.asFile
    outputs.file(target)
    doLast {
        if (!target.exists()) {
            target.parentFile.mkdirs()
            val url = "https://github.com/microsoft/onnxruntime-genai/releases/download/v0.17.0/" +
                "onnxruntime-genai-android-0.17.0.aar"
            URI(url).toURL().openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }
}

android {
    namespace = "org.ggomes.myleo.llm.dual"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.ggomes.myleo.llm.dual"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // onnxruntime-genai dynamically loads libonnxruntime.so, which is NOT in
    // the genai AAR — it comes from the ONNX Runtime Android AAR.
    implementation(libs.onnxruntime.android)
    // LiteRT-LM runtime (the other half of the runtime picker).
    implementation(libs.litertlm.android)
    implementation(files(downloadGenAiAar.map { genaiAarFile.asFile }))
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.assertj.core)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
