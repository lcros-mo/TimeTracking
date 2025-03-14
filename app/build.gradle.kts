import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.services)
    kotlin("kapt")
    id("kotlin-parcelize")
    id("kotlin-android")
}

// Cargar variables desde local.properties
val localProperties = File(rootProject.projectDir, "local.properties")
val properties = Properties()

if (localProperties.exists()) {
    properties.load(localProperties.inputStream())
}

val apiKey = properties.getProperty("API_KEY") ?: "DEFAULT_API_KEY"

android {
    signingConfigs {
        create("release") {
            storeFile = file("C:\\Users\\LuisCros\\Desktop\\GP KS\\GP.jks")
            storePassword = "477094"
            keyAlias = "GPS"
            keyPassword = "477094"
        }
    }
    namespace = "com.timetracking.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.timetracking.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 14
        versionName = "1.5.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_KEY", "\"$apiKey\"")
        }
        release {
            buildConfigField("String", "API_KEY", "\"$apiKey\"")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
        arg("room.expandProjection", "true")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.play.services.auth.v2120)
    implementation(libs.googleid)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.app.update.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)

    // Rooms
    val roomVersion = "2.6.1"
    implementation(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    // PDF
    implementation(libs.itext7.core)

    // Para conexiones HTTP
    implementation(libs.okhttp)

    // Para manejo de corrutinas
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.app.update.ktx)

    // API
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
}

