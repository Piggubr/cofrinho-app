plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    // O ID OAuth do site: o Google põe ele no aud do ID token, que o backend confere (D-084).
    // O botão do Google só aparece com o ID do Android criado no Google Cloud
    // (-Ppiggu.googleNoAndroid=true ou em gradle.properties), senão o login falha com DEVELOPER_ERROR.
    defaultConfig {
        buildConfigField("String", "GOOGLE_ID_DO_SITE", "\"80166136253-iun0d3ano0st9kdfvl94e803dbjloa6t.apps.googleusercontent.com\"")
        buildConfigField("boolean", "GOOGLE_NO_ANDROID", (findProperty("piggu.googleNoAndroid") ?: "false").toString())
    }
    buildFeatures.buildConfig = true
    testOptions.unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.data)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(projects.core.testing)
}
