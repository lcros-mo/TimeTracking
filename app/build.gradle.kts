import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.services)
    kotlin("kapt")
    id("kotlin-parcelize")
}

// Cargar variables desde local.properties
val localProperties = File(rootProject.projectDir, "local.properties")
val properties = Properties()

if (localProperties.exists()) {
    properties.load(localProperties.inputStream())
}

val apiKey = properties.getProperty("API_KEY") ?: "DEFAULT_API_KEY"
val keystorePath = properties.getProperty("keystore.path")
val keystorePassword = properties.getProperty("keystore.password")
val keystoreAlias = properties.getProperty("keystore.alias")
val keystoreAliasPassword = properties.getProperty("keystore.alias.password")

android {
    signingConfigs {
        create("release") {
            // Verificar que las propiedades existen antes de usarlas
            if (keystorePath != null && keystorePassword != null &&
                keystoreAlias != null && keystoreAliasPassword != null) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keystoreAlias
                keyPassword = keystoreAliasPassword
            } else {
                // Log para desarrollo - esto no afectará a las builds de producción
                // siempre que las propiedades estén configuradas
                println("WARNING: Keystore properties not found in local.properties!")
            }
        }
    }

    // El resto de tu configuración permanece igual
    namespace = "com.timetracking.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.timetracking.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 24
        versionName = "1.7.2"

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

    buildFeatures {
        // Habilitar View Binding para facilitar el acceso a vistas
        viewBinding = true
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
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Authentication
    implementation(libs.play.services.auth.v2120)
    implementation(libs.googleid)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)

    // Architecture Components
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // PDF Generation
    implementation(libs.itext7.core)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // App Updates
    implementation(libs.app.update.ktx)

    // Fragment KTX - esto proporciona viewModels()
    implementation(libs.androidx.fragment.ktx)
}