import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.kover)
}

// Cobertura do app inteiro (Kover): cada módulo com código mede os próprios testes, e a raiz
// junta tudo em `./gradlew koverHtmlReport` / `koverVerify`. Fora da conta: o perfil de
// inicialização e a API falsa de :core:testing, que os próprios testes exercitam.
val foraDaCobertura = setOf(":baselineprofile", ":core:testing")
val modulosMedidos = subprojects.filter { it.path !in foraDaCobertura }
dependencies { modulosMedidos.forEach { kover(it) } }
kover {
    reports {
        // Piso de linhas cobertas do app inteiro. Só sobe: quem baixa a cobertura quebra o CI.
        // Meta: 85% (02/11/2026).
        verify { rule { minBound(78) } }
        filters {
            excludes {
                // Gerado pelo compilador (Hilt, Room, Compose, recursos), não escrito por nós.
                classes("*_Factory", "*_Factory\$*", "*_HiltModules*", "*_MembersInjector", "Hilt_*", "*_Impl", "*_Impl\$*",
                    "dagger.hilt.*", "hilt_aggregated_deps.*", "*ComposableSingletons*", "*.R", "*.R\$*", "*.BuildConfig",
                    "*_GeneratedInjector", "*_ComponentTreeDeps", "*_Provide*Factory")
            }
        }
    }
}

// O que todo módulo Android de biblioteca repete: SDKs e o namespace pelo caminho
// (:core:ui vira br.com.piggu.core.ui).
subprojects {
    if (path !in foraDaCobertura) apply(plugin = "org.jetbrains.kotlinx.kover")
    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            namespace = "br.com.piggu" + path.replace(':', '.')
            compileSdk = 37
            defaultConfig.minSdk = 26
            testOptions.unitTests.isIncludeAndroidResources = true
        }
    }
}
