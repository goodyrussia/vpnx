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
        // libsu (root shell) is published via JitPack.
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "vpnx"
include(":app")
