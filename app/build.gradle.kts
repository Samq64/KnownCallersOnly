import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

android {
    namespace = "dev.samuelq.callscreen"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.samuelq.callscreen"
        minSdk = 29 // RoleManager + call screening role
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true   // R8: strips unused Kotlin stdlib code
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    packaging {
        resources.excludes += listOf("kotlin/**", "META-INF/*.version", "kotlin-tooling-metadata.json")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
