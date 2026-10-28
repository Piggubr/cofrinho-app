// Composables compartilhados entre as features.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    api(projects.core.designsystem)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core.ktx)
}
