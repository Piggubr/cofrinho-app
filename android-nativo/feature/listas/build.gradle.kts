// Compras, Lugares e Filmes (A7).
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
    // Photo Picker e câmera (rememberLauncherForActivityResult).
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.exifinterface)
    // Leitor de código de barras do Google Play services: sem permissão de câmera no app.
    implementation(libs.play.services.code.scanner)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
