rootProject.name = "Nuvio"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Avix: mpvRx engine (app.gyrolet.mpvlib) is only published as GitHub release assets
        // of Riteshp2001/mpvlibAndroid. Restricted to that group; the AAR's SHA-256 is verified
        // by :composeApp:verifyMpvlibChecksum before any Kotlin compilation.
        exclusiveContent {
            forRepository {
                ivy {
                    name = "mpvlibGitHubReleases"
                    url = uri("https://github.com/Riteshp2001/mpvlibAndroid/releases/download")
                    patternLayout {
                        // Only .aar assets are published; metadata discovery would otherwise probe .jar (404).
                        artifact("v[revision]/[artifact]-[revision].aar")
                    }
                    metadataSources {
                        artifact()
                    }
                }
            }
            filter {
                includeGroup("app.gyrolet.mpvlib")
            }
        }
    }
}

include(":composeApp")
include(":androidApp")
