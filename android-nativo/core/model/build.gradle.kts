// Tipos do contrato da API (docs/API_SPEC.md), sem nada de Android.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.snakeyaml)
}

// O ContratoTest confere os tipos contra o OpenAPI gerado do backend.
tasks.test {
    val contrato = rootDir.resolve("../docs/api")
    inputs.dir(contrato).withPropertyName("contrato").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("piggu.contrato", contrato.canonicalPath)
}
