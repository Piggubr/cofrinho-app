// Termos de Uso e Aviso de Privacidade (features/legal do site), abertos sem login e sem internet.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

dependencies {
    implementation(projects.core.ui)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(projects.core.testing)
    // Só para o teste comparar VERSAO_DOS_TERMOS com o aviso.ts do site; a tela não usa a sessão.
    testImplementation(projects.core.data)
}
