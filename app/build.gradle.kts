import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "tech.torlando.rns"
    compileSdk = 36

    defaultConfig {
        applicationId = "tech.torlando.rns"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    val releaseSigningConfigured =
        run {
            val keystoreFile = System.getenv("KEYSTORE_FILE")
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("KEY_ALIAS")
            val keyPassword = System.getenv("KEY_PASSWORD")

            !keystoreFile.isNullOrEmpty() && !keystorePassword.isNullOrEmpty() &&
                !keyAlias.isNullOrEmpty() && !keyPassword.isNullOrEmpty()
        }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                val keystoreFile = System.getenv("KEYSTORE_FILE")!!
                val keystorePassword = System.getenv("KEYSTORE_PASSWORD")!!
                val keyAlias = System.getenv("KEY_ALIAS")!!
                val keyPassword = System.getenv("KEY_PASSWORD")!!

                try {
                    val keystoreDir = file("${layout.buildDirectory.get().asFile}/keystore")
                    keystoreDir.mkdirs()
                    val decodedKeystore = file("$keystoreDir/release.keystore")
                    val cleanedKeystoreFile = keystoreFile.replace("\\s".toRegex(), "")
                    decodedKeystore.writeBytes(Base64.getDecoder().decode(cleanedKeystoreFile))

                    storeFile = decodedKeystore
                    storePassword = keystorePassword
                    this.keyAlias = keyAlias
                    this.keyPassword = keyPassword

                    println("✓ Release signing configured from environment variables")
                } catch (e: IllegalArgumentException) {
                    throw GradleException(
                        "Failed to decode KEYSTORE_FILE: ${e.message}\n" +
                            "To encode: base64 -w 0 your-keystore.jks",
                    )
                }
            }
        } else {
            println("⚠ Release signing not configured (missing environment variables)")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"

        pip {
            install("-r", "../python/requirements.txt")
        }

        pyc {
            src = true
        }
    }

    sourceSets {
        getByName("main") {
            srcDir("../python")
        }
    }
}

dependencies {
    implementation(project(":bridges"))
    implementation(project(":rnode-wizard"))
    implementation(project(":tcp-client-wizard"))

    // Core
    implementation(libs.core.ktx)
    implementation(libs.splashscreen)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.service)
    implementation(libs.compose.activity)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.preview)
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation(libs.compose.tooling)

    // Navigation
    implementation(libs.navigation)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // DataStore
    implementation(libs.datastore)
}
