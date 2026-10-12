import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeystore = System.getenv("WHATSWEB_KEYSTORE")
val releaseStorePassword = System.getenv("WHATSWEB_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("WHATSWEB_KEY_ALIAS")
val releaseKeyPassword = System.getenv("WHATSWEB_KEY_PASSWORD")
val releaseSigningValues = listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val releaseSigningConfigured = releaseSigningValues.all { !it.isNullOrBlank() }
val geckoEngine = providers.gradleProperty("whatsweb.gecko").orNull == "true" ||
    gradle.startParameter.projectProperties["whatsweb.gecko"] == "true"
if (releaseSigningValues.any { !it.isNullOrBlank() } && !releaseSigningConfigured) {
    throw GradleException(
        "Release signing requires WHATSWEB_KEYSTORE, WHATSWEB_KEYSTORE_PASSWORD, WHATSWEB_KEY_ALIAS, and WHATSWEB_KEY_PASSWORD.",
    )
}

android {
    namespace = "com.synalysis.whatsweb"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.synalysis.whatsweb"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.4"
        if (geckoEngine) {
            // A separate install so WhatsWeb Calls does not replace the
            // system-WebView packages used for F-Droid, direct download, and Play.
            // versionName and versionCode stay in lockstep with that app.
            applicationId = "com.synalysis.whatsweb.gecko"
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (geckoEngine) {
                ndk {
                    // The emulator is x86_64 and a phone is arm64.
                    abiFilters += listOf("arm64-v8a", "x86_64")
                }
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (geckoEngine) {
                proguardFile("proguard-rules-gecko.pro")
            }
            // Unsigned unless every release signing variable is set. F-Droid
            // signs that APK itself; CI uses the project keystore.
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            if (geckoEngine) {
                ndk {
                    // Phones only. The debug build still includes the emulator ABI.
                    abiFilters += "arm64-v8a"
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
    }

    sourceSets.named("main") {
        if (geckoEngine) {
            java.srcDir("src/engineGecko/java")
            res.srcDir("src/engineGecko/res")
        } else {
            java.srcDir("src/engineWebview/java")
        }
    }
}

if (geckoEngine) {
    androidComponents {
        onVariants { variant ->
            variant.sources.manifests.addStaticManifestFile("src/engineGecko/AndroidManifest.xml")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

if (geckoEngine) {
    configurations.configureEach {
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.21")
        resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.21")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.webkit:webkit:1.14.0")
    if (geckoEngine) {
        // The plain artifact carries every CPU architecture. The emulator is
        // x86_64 and a phone is arm64; the ndk abi filters keep those two.
        implementation("org.mozilla.geckoview:geckoview:149.0.20260403140140") {
            exclude(group = "com.google.android.gms", module = "play-services-fido")
        }
    }
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

    testImplementation("junit:junit:4.13.2")
}
