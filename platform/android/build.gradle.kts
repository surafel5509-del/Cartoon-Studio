plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cartoonstudio.platform.android"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core:common"))
    api(project(":core:undo"))
    api(project(":core:math"))
    api(project(":core:time"))
    api(project(":domain:model"))
    api(project(":domain:drawing"))
    api(project(":domain:animation"))
    api(project(":domain:camera"))
    api(project(":domain:audio"))
    api(project(":domain:export"))
    api(project(":domain:rigging"))
    api(project(":engine:scene"))
    api(project(":engine:animation"))
    api(project(":engine:drawing"))
    api(project(":engine:rendering"))
    api(project(":engine:compositing"))
    api(project(":engine:export"))
    api(project(":data:project-store"))
    api(project(":data:asset-store"))
    api(project(":data:preferences"))
    api(project(":data:cache"))
    api(project(":platform:graphics"))
    api(project(":platform:media"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
}
