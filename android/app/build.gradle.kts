plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
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
        versionCode = 1
        versionName = "0.1.0-alpha.1"

        buildConfigField("String", "UPDATE_REPO", "\"pbrockt/fahrrad-computer\"")
    }

    // Fester Signaturschluessel wie bei der Trainingsauswertung: haelt die Signatur ueber
    // CI-Laeufe stabil, damit sich Updates ueber die installierte Version legen lassen.
    // Fuer eine Play-Store-Veroeffentlichung waere ein Schluessel im Repo nicht tragbar.
    val releaseKeystore = file("release.keystore")

    signingConfigs {
        create("stabil") {
            if (releaseKeystore.exists()) {
                storeFile = releaseKeystore
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = "fahrradcomputer"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (releaseKeystore.exists()) {
                signingConfigs.getByName("stabil")
            } else {
                signingConfigs.getByName("debug")
            }
            // Kein Minify, solange die App nicht am Rad getestet ist.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            if (releaseKeystore.exists()) signingConfig = signingConfigs.getByName("stabil")
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

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
