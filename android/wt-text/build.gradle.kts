import org.gradle.api.tasks.Copy
import java.util.Properties

plugins {
    id("com.android.application") version "9.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
}

val signingProperties = Properties().apply {
    val signingFile = layout.projectDirectory.file("signing.properties").asFile
    if (signingFile.exists()) {
        signingFile.inputStream().use(::load)
    }
}

fun signingValue(name: String): String? {
    return providers.environmentVariable(name).orNull
        ?: signingProperties.getProperty(name)
}

val releaseStoreFile = signingValue("WT_RELEASE_STORE_FILE")
val releaseStorePassword = signingValue("WT_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("WT_RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("WT_RELEASE_KEY_PASSWORD")
val hasReleaseSigning =
    !releaseStoreFile.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.wrangletangle.text"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wrangletangle.text"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }

        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

base {
    archivesName.set("app")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.android.material:material:1.13.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    testImplementation("junit:junit:4.13.2")
}

val copyApkToDist = tasks.register<Copy>("copyApkToDist") {
    from(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
    into(layout.projectDirectory.dir("dist"))
    rename { "wt-text.apk" }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(copyApkToDist)
}
