plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cartoonstudio.feature.editor"
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

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    api(project(":ui:design-system"))
    api(project(":ui:components"))
    api(project(":platform:android"))
    api(project(":platform:graphics"))
    api(project(":core:math"))
    api(project(":core:time"))
    api(project(":core:undo"))
    api(project(":domain:model"))
    api(project(":domain:drawing"))
    api(project(":domain:animation"))
    api(project(":domain:camera"))
    api(project(":domain:audio"))
    api(project(":domain:export"))
    api(project(":domain:rigging"))
    api(project(":engine:scene"))
    api(project(":engine:rendering"))
    api(project(":engine:drawing"))
    api(project(":engine:animation"))
    api(project(":data:project-store"))
    api(project(":data:asset-store"))
    api(project(":data:preferences"))
    api(project(":feature:drawing"))
    api(project(":feature:timeline"))
    api(project(":feature:animation"))
    api(project(":feature:assets"))
    api(project(":feature:scenes"))
    api(project(":feature:audio"))
    api(project(":feature:compositing"))
    api(project(":feature:export"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
}
