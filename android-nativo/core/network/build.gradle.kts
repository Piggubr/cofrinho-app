// Cliente da API: Retrofit + OkHttp + kotlinx.serialization.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

dependencies {
    api(projects.core.model)
    api(libs.retrofit)
    api(libs.okhttp)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
