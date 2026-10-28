import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.roborazzi) apply false
}

// O que todo módulo Android de biblioteca repete: SDKs e o namespace pelo caminho
// (:core:ui vira br.com.piggu.core.ui).
subprojects {
    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            namespace = "br.com.piggu" + path.replace(':', '.')
            compileSdk = 37
            defaultConfig.minSdk = 26
            testOptions.unitTests.isIncludeAndroidResources = true
        }
    }
}
