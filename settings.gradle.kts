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

rootProject.name = "Rancher"

include(
    ":app",
    ":core-model",
    ":android-accessibility",
    ":android-snapshot",
    ":android-actions",
    ":debug-harness",
)
