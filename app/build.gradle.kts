import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tools.screenshot3"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.tools.screenshot3"
        minSdk = 24
        targetSdk = 36
        
        // Generate version code from build timestamp in format mmddyyhhmm
        val buildTime = SimpleDateFormat("MMddyyHHmm").format(Date())
        versionCode = buildTime.toLong().toInt()
        versionName = "2.1.2"
        resValue("string", "generated_build_time_millis", System.currentTimeMillis().toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    @Suppress("UnstableApiUsage")
    applicationVariants.all {
        val variant = this
        outputs
            .mapNotNull { it as? ApkVariantOutputImpl }
            .forEach { output ->
                val originalName = output.outputFileName
                val baseName = originalName.replaceFirst("app", "screenshot_manager")
                // Remove the existing extension, add version name and version code, then restore extension
                val nameWithoutExt = baseName.substringBeforeLast(".")
                val extension = baseName.substringAfterLast(".", "")
                val versionNameSuffix = "-${variant.versionName}"
                val versionCodeSuffix = "_${variant.versionCode}"
                output.outputFileName = "${nameWithoutExt}${versionNameSuffix}${versionCodeSuffix}.${extension}"
            }
    }
}

tasks.register("buildInternalRelease") {
    group = "distribution"
    description = "Build the signed, shrunk internal release APK."
    dependsOn("assembleRelease")

    doLast {
        val releaseDir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val releaseApk = releaseDir
            .listFiles()
            ?.filter { it.isFile && it.extension == "apk" }
            ?.maxByOrNull { it.lastModified() }

        if (releaseApk != null) {
            println("Internal release APK: ${releaseApk.absolutePath}")
            println("Tip: run ./scripts/build-release.sh for size comparison output.")
        } else {
            println("Release APK not found in ${releaseDir.absolutePath}")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
