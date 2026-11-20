// Settings for vpnx project
@file:Suppress("UnstableApiUsage")

rootProject.name = "vpnx"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        exclusiveContent {
            forRepository {
                ivy {
                    name = "AndroidLibXrayLiteGitHubRelease"
                    url = uri("https://github.com/goodyrussia/AndroidLibXrayLite/releases/download")
                    patternLayout {
                        artifact("[revision]/[artifact].[ext]")
                    }
                    metadataSources {
                        artifact()
                    }
                }
            }
            filter {
                includeModule("com.github.Asterisk4Magisk", "libv2ray")
            }
        }
    }
}

include(":app")
