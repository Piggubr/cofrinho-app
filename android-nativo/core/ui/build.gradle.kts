// Composables compartilhados entre as features.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    api(projects.core.designsystem)
    // Fotos por URL com cache (Lugares e Fotos); o carregador com a sessão é montado pelo app.
    api(libs.coil.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
