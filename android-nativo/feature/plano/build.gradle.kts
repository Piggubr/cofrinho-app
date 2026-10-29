plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    testOptions.unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.data)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    // A assinatura pelo Google Play (A8).
    implementation(libs.billing.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(projects.core.testing)
    // MonitorDeRede e o que mais pedir o Android nos testes de ViewModel.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    // A tela só desenhada: os botões de assinar e o texto de cada origem.
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
