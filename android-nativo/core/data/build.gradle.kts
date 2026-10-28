// Repositórios offline-first: Room primeiro, rede depois. A sessão e o monitor de rede.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    testOptions.unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
}

dependencies {
    api(projects.core.model)
    api(projects.core.network)
    api(projects.core.datastore)
    api(projects.core.database)
    // api: o app liga o HiltWorkerFactory no WorkManager (PigguApplication).
    api(libs.androidx.work.runtime.ktx)
    api(libs.androidx.hilt.work)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
}
