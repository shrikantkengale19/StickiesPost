import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use { load(it) }
    }
}
val notesSyncProvider = providers.gradleProperty("notesSyncProvider")
    .orElse("FIREBASE")
    .get()
    .uppercase()
check(notesSyncProvider in setOf("FIREBASE", "SUPABASE")) {
    "notesSyncProvider must be SUPABASE or FIREBASE (received '$notesSyncProvider')."
}
if (notesSyncProvider == "FIREBASE") {
    check(rootProject.file("app/google-services.json").isFile) {
        "Firebase sync requires app/google-services.json for this application."
    }
    pluginManager.apply("com.google.gms.google-services")
}
fun buildConfigString(value: String) =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.example.stickiespost"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.stickiespost"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "SUPABASE_URL",
            buildConfigString(localProperties.getProperty("supabase.url", ""))
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            buildConfigString(localProperties.getProperty("supabase.publishable_key", ""))
        )
        buildConfigField("String", "NOTES_SYNC_PROVIDER", buildConfigString(notesSyncProvider))
        buildConfigField("String", "FIREBASE_TEST_EMAIL", buildConfigString(""))
        buildConfigField("String", "FIREBASE_TEST_PASSWORD", buildConfigString(""))
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "FIREBASE_TEST_EMAIL",
                buildConfigString(localProperties.getProperty("firebase.test.email", ""))
            )
            buildConfigField(
                "String",
                "FIREBASE_TEST_PASSWORD",
                buildConfigString(localProperties.getProperty("firebase.test.password", ""))
            )
        }
        release {
            optimization {
                enable = true
            }
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        create("unsigned") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.supabase.bom))
    implementation(platform(libs.firebase.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.android)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    ksp(libs.androidx.room.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}