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
        // GeckoView, Mozilla's Android browser engine
        maven { url = uri("https://maven.mozilla.org/maven2/") }
    }
}
rootProject.name = "AquaBrowser"
include(":app")
