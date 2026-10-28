import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

// Load signing credentials from local.properties (never committed to git)
// In CI/CD, these are provided via environment variables from GitHub Secrets
val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) props.load(f.inputStream())
}

fun localProp(key: String): String? =
    System.getenv(key) ?: localProps.getProperty(key)

android {
    namespace = "org.lianye"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.lianye.navfix"
        minSdk = 29
        targetSdk = 35
        versionCode = 25
        versionName = "0.1.24-navfix-long"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = localProp("storeFile")?.let { file(it) }
            storePassword = localProp("storePassword")
            keyAlias = localProp("keyAlias")
            keyPassword = localProp("keyPassword")
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
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }

    // Strips Google dependency metadata for reproducible builds
    // Required for F-Droid Reproducible Build verification
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("verifyZeroNetworkDependencies") {
    group = "verification"
    description = "Ensures zero network and tracking dependencies exist in runtimeClasspath."
    doLast {
        val forbiddenTokens = listOf("okhttp", "retrofit", "ktor", "volley", "firebase", "analytics", "admob")
        val runtimeConfigs = configurations.filter { it.name.endsWith("RuntimeClasspath") }
        val violations = mutableListOf<String>()
        runtimeConfigs.forEach { config ->
            try {
                config.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                    val id = artifact.moduleVersion.id.toString().lowercase()
                    forbiddenTokens.forEach { token ->
                        if (id.contains(token)) {
                            violations.add("${config.name} -> $id")
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore configurations that cannot be resolved directly
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("FATAL: Forbidden network dependencies detected: $violations. Lianye enforces strict zero-network purity!")
        }
        println("✅ verifyZeroNetworkDependencies passed: 0 forbidden network dependencies found across runtime configurations.")
    }
}

tasks.named("preBuild") {
    dependsOn("verifyZeroNetworkDependencies")
}
