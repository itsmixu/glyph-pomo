pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    @Suppress("DEPRECATION")
    repositories {
        google()
        mavenCentral()
        flatDir { dirs("$rootDir/app/libs") }
    }
}

rootProject.name = "GlyphPomodoro"
include(":app")
