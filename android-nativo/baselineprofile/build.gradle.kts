// Gera o perfil de inicialização do app (./gradlew :app:generateBaselineProfile, com um
// aparelho ou emulador conectado). Não roda no build normal nem no CI.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "br.com.piggu.baselineprofile"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    flavorDimensions += "ambiente"
    productFlavors {
        // O pacote do app medido, lido pelo GeradorDePerfil.
        create("demo") {
            dimension = "ambiente"
            testInstrumentationRunnerArguments["targetAppId"] = "br.com.piggu.app.demo"
        }
        create("producao") {
            dimension = "ambiente"
            testInstrumentationRunnerArguments["targetAppId"] = "br.com.piggu.app"
        }
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
