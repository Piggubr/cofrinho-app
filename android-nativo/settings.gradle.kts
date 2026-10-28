pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "piggu"

include(
    ":app",
    ":core:model",
    ":core:network",
    ":core:data",
    ":core:database",
    ":core:datastore",
    ":core:designsystem",
    ":core:ui",
    ":core:testing",
    ":feature:login",
    ":feature:painel",
    ":feature:gastos",
    ":baselineprofile",
)
