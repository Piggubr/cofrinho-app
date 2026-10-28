// API falsa e utilidades de teste. Também entra no app da variante demo (modo demonstração).
plugins {
    alias(libs.plugins.android.library)
}

dependencies {
    api(projects.core.model)
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.okhttp.mockwebserver)
}
