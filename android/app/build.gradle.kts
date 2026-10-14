import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pbrockt.fahrradcomputer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pbrockt.fahrradcomputer"
        minSdk = 26
        targetSdk = 35
        // versionCode steigt bei jedem veroeffentlichten Build, auch bei Alphas - sonst
        // verweigert Android das Update. versionName folgt SemVer: "x.y.z" ist ein
        // Release, "x.y.z-alpha.N" eine Alpha. release.yml prueft, dass der Tag passt.
        versionCode = 7
        versionName = "0.0.7-alpha.1"

        buildConfigField("String", "UPDATE_REPO", "\"pbrockt/fahrrad-computer\"")
    }

    // Fester Signaturschluessel: haelt die Signatur ueber Builds stabil, damit sich Updates
    // ueber die installierte Version legen lassen. Schluessel und Passwort liegen NICHT im
    // Repo: In GitHub Actions kommen sie aus den Secrets SIGNING_KEYSTORE_BASE64 und
    // SIGNING_PASSWORD (siehe release.yml), lokal liegt die Datei unversioniert hier und
    // das Passwort als signing.password in local.properties.
    val releaseKeystore = file("release.keystore")
    val signingPassword: String? = System.getenv("SIGNING_PASSWORD")?.takeIf { it.isNotEmpty() }
        ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { f ->
            Properties().apply { f.inputStream().use { load(it) } }.getProperty("signing.password")
        }
    val canSign = releaseKeystore.exists() && signingPassword != null

    signingConfigs {
        create("stabil") {
            if (canSign) {
                storeFile = releaseKeystore
                storePassword = signingPassword
                keyAlias = "fahrradcomputer"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            // Ohne Schluessel faellt der Build auf die Debug-Signatur zurueck, damit
            // assembleRelease immer ein installierbares APK liefert.
            signingConfig = if (canSign) {
                signingConfigs.getByName("stabil")
            } else {
                signingConfigs.getByName("debug")
            }
            // Kein Minify, solange die App nicht am Rad getestet ist.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            if (canSign) signingConfig = signingConfigs.getByName("stabil")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.health.connect)
    implementation(libs.mapsforge.map.android)
    implementation(libs.mapsforge.themes)
    implementation(libs.anthropic.java)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    // XmlPullParser fuer die JVM-Tests - auf dem Handy liefert Android ihn mit.
    testImplementation(libs.kxml2)
}
