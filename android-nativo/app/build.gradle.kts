plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "br.com.piggu.app"
    // 37 porque o Compose e o Navigation 3 estáveis pedem; o comportamento segue o targetSdk 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "br.com.piggu.app"
        minSdk = 26
        targetSdk = 36
        // O nativo entra como 2.0.0 do mesmo app das lojas (D-057).
        versionCode = 200
        versionName = "2.0.0"
    }

    // demo: dados de exemplo da API falsa de :core:testing, sem backend. producao: a API de verdade.
    flavorDimensions += "ambiente"
    productFlavors {
        create("demo") {
            dimension = "ambiente"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
        }
        create("producao") {
            dimension = "ambiente"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions.unitTests {
        isIncludeAndroidResources = true
        // O Robolectric mexe no FileDescriptor do JDK para simular o Android 16.
        all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.data)
    // O carregador de imagens do Coil usa o OkHttp da sessão; o SVG é o das fotos da demonstração.
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.svg)
    implementation(projects.feature.login)
    implementation(projects.feature.legal)
    implementation(projects.feature.painel)
    implementation(projects.feature.gastos)
    implementation(projects.feature.receitas)
    implementation(projects.feature.assistente)
    implementation(projects.feature.contasfixas)
    implementation(projects.feature.orcamentos)
    implementation(projects.feature.calendario)
    implementation(projects.feature.relatorios)
    implementation(projects.feature.contas)
    implementation(projects.feature.metas)
    implementation(projects.feature.girlsmath)
    implementation(projects.feature.planejar)
    implementation(projects.feature.premios)
    implementation(projects.feature.familia)
    implementation(projects.feature.perfil)
    implementation(projects.feature.plano)
    // A API falsa responde a variante demo inteira, sem backend.
    "demoImplementation"(projects.core.testing)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.compose.material3.adaptive)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.compose.material3.navigation.suite)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    baselineProfile(projects.baselineprofile)

    testImplementation(libs.junit)
    // As capturas da A4 sobem o grafo do Hilt de verdade, com a API falsa da variante demo.
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
