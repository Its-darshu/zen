plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---------------------------------------------------------------------------
// Release signing
//
// Credentials are read from Gradle properties or the environment and never from
// the repository: no keystore, no password and no alias is committed here or
// anywhere else in the tree (see .gitignore).
//
// A release build is never signed with the debug key. If the credentials are
// absent the release build fails with an explanation instead of quietly
// producing an artifact signed by a key that every Android developer on earth
// already has. Debug builds are unaffected and keep working with no setup.
// ---------------------------------------------------------------------------

/** A Gradle property (`-P`, `gradle.properties`) or an environment variable. */
fun signingCredential(name: String): String? =
    (providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull)
        ?.takeIf { it.isNotBlank() }

val releaseSigningCredentials = linkedMapOf(
    "ZEN_RELEASE_STORE_FILE" to signingCredential("ZEN_RELEASE_STORE_FILE"),
    "ZEN_RELEASE_STORE_PASSWORD" to signingCredential("ZEN_RELEASE_STORE_PASSWORD"),
    "ZEN_RELEASE_KEY_ALIAS" to signingCredential("ZEN_RELEASE_KEY_ALIAS"),
    "ZEN_RELEASE_KEY_PASSWORD" to signingCredential("ZEN_RELEASE_KEY_PASSWORD"),
)

val missingReleaseCredentials = releaseSigningCredentials.filterValues { it == null }.keys.toList()
val hasReleaseSigningCredentials = missingReleaseCredentials.isEmpty()
val releaseKeystore = releaseSigningCredentials["ZEN_RELEASE_STORE_FILE"]?.let { file(it) }

android {
    namespace = "com.zenmode.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zenmode.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.zenmode.app.ZenModeTestRunner"

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.generateKotlin", "true")
        }
    }

    signingConfigs {
        // Created only when every credential is present. Half a config is worse
        // than none: it would fail late, inside the signing task, with a message
        // about a null password rather than about the missing setup.
        if (hasReleaseSigningCredentials) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = releaseSigningCredentials["ZEN_RELEASE_STORE_PASSWORD"]
                keyAlias = releaseSigningCredentials["ZEN_RELEASE_KEY_ALIAS"]
                keyPassword = releaseSigningCredentials["ZEN_RELEASE_KEY_PASSWORD"]
                // minSdk is 29, so the JAR signature v1 buys nothing.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Deliberately left unset when credentials are absent. There is no
            // fallback to the debug key — `verifyReleaseSigning` stops the build
            // first, so an unsigned or debug-signed release cannot be produced
            // by accident.
            signingConfig = if (hasReleaseSigningCredentials) {
                signingConfigs.getByName("release")
            } else {
                null
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

/**
 * Stops a release build that has nothing to sign it with.
 *
 * Without this the build would still succeed and hand back an unsigned APK,
 * which is easy to mistake for a releasable one. Failing here, loudly, with the
 * exact property names, is the whole point of the check.
 */
val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Fails a release build when production signing credentials are unavailable."

    // Captured at configuration time so the check stays configuration-cache safe.
    val missing = missingReleaseCredentials
    val keystore = releaseKeystore

    doLast {
        if (missing.isNotEmpty()) {
            throw GradleException(
                """
                |Release signing credentials are unavailable, so this release build was stopped.
                |
                |Zen never signs a release with the debug key: that key is shared by every
                |Android install on earth, so anything signed with it can be replaced by
                |anyone. There is deliberately no fallback.
                |
                |Missing: ${missing.joinToString(", ")}
                |
                |Supply all four as Gradle properties or environment variables:
                |
                |  ZEN_RELEASE_STORE_FILE      path to the release keystore
                |  ZEN_RELEASE_STORE_PASSWORD  keystore password
                |  ZEN_RELEASE_KEY_ALIAS       key alias
                |  ZEN_RELEASE_KEY_PASSWORD    password for that key
                |
                |Put them in ~/.gradle/gradle.properties or the CI secret store —
                |never in this repository, and never in a committed file.
                |
                |`./gradlew assembleDebug` needs none of this and is unaffected.
                """.trimMargin(),
            )
        }
        if (keystore != null && !keystore.isFile) {
            throw GradleException(
                "ZEN_RELEASE_STORE_FILE points at ${keystore.absolutePath}, which is not a file.",
            )
        }
    }
}

// Guards the entry points that can produce a release artifact. Attached to the
// packaging tasks as well as the assemble/bundle aliases, so invoking a lower
// level task directly does not slip past the check.
tasks.configureEach {
    if (name in setOf("assembleRelease", "bundleRelease", "packageRelease", "packageReleaseBundle")) {
        dependsOn(verifyReleaseSigning)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Compose screens are tested with Robolectric so the UI suite runs on the
    // JVM alongside the rest of the unit tests, with no device required.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
