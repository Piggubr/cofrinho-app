// Repositórios offline-first: Room primeiro, rede depois, sincronização pelo WorkManager.
plugins {
    alias(libs.plugins.android.library)
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(libs.androidx.work.runtime.ktx)
}
