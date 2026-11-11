plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.openmeasure.ar"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core-measure"))
    api(libs.arcore)
    api(libs.kotlinx.coroutines.android)
    // Moved from :app (T25): PoseHeightService runs the detector next to the ARCore frame.
    implementation(libs.mlkit.pose.detection)
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio.android)
    implementation(libs.filament.utils.android)
}
