import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// CI stamps each build with its run number so a newer APK always installs over an older one.
val buildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").map(String::toInt).orElse(1).get()

android {
    namespace = "io.github.chiragbhatn.expensetracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.chiragbhatn.expensetracker"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "2.0.$buildNumber"
    }

    signingConfigs {
        create("release") {
            // Every APK must be signed with the same key for updates to install over the
            // existing app (keeping its data). A private key can be supplied through the
            // environment, see README; otherwise the shared key in keystore/ is used.
            val privateKeystore = providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull
            if (privateKeystore != null) {
                storeFile = file(privateKeystore)
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").get()
            } else {
                storeFile = file("keystore/shared-release.p12")
                storeType = "pkcs12"
                storePassword = "expense-tracker"
                keyAlias = "expense-tracker"
                keyPassword = "expense-tracker"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        // The offline text-recognition library ships native code for four CPU types. Storing it
        // compressed roughly halves the APK people download; Android unpacks it on install.
        jniLibs.useLegacyPackaging = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        // The exported Room schemas, so migration tests can build each database version.
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Migration tests read the exported Room schemas as debug assets, so export them first.
tasks.matching { it.name == "mergeDebugAssets" }.configureEach { dependsOn("kspDebugKotlin") }

tasks.withType<Test>().configureEach {
    // Robolectric UI tests need more than Gradle's 512 MB default.
    maxHeapSize = "2g"
    testLogging {
        events(TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.mlkit.text.recognition)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
