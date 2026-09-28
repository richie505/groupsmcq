pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "APPSCMcq"
// -PskipAndroid builds only the Windows app (no Android SDK needed)
if (!providers.gradleProperty("skipAndroid").isPresent) include(":app")
include(":desktop")
