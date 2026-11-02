plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.woody.planner"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.woody.planner"
        minSdk = 31
        targetSdk = 36
        versionCode = 11
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Р СџР С•Р Т‘Р С—Р С‘РЎРѓРЎРЉ debug-Р С”Р В»РЎР‹РЎвЂЎР С•Р С, РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ release-РЎРѓР В±Р С•РЎР‚Р С”РЎС“ Р СР С•Р В¶Р Р…Р С• Р В±РЎвЂ№Р В»Р С• РЎРѓРЎР‚Р В°Р В·РЎС“ Р С—Р С•РЎРѓРЎвЂљР В°Р Р†Р С‘РЎвЂљРЎРЉ Р Р…Р В° РЎвЂљР ВµР В»Р ВµРЎвЂћР С•Р Р….
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Р РЋР С”РЎР‚Р С‘Р Р…РЎв‚¬Р С•РЎвЂљРЎвЂ№ (Robolectric) РІР‚вЂќ РЎвЂљР С•Р В»РЎРЉР С”Р С• Р С—Р С• Р В·Р В°Р С—РЎР‚Р С•РЎРѓРЎС“: gradlew testDebugUnitTest -Pscreenshots
            if (project.hasProperty("screenshots")) {
                test.systemProperty("roborazzi.test.record", "true")
                test.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                test.filter.includeTestsMatching("com.woody.planner.screenshots.*")
            } else {
                test.filter.excludeTestsMatching("com.woody.planner.screenshots.*")
            }
        }
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "dev.chrisbanes.haze.ExperimentalHazeApi",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.glance.appwidget)

    implementation(libs.haze)
    implementation(libs.haze.glass)

    testImplementation(libs.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
