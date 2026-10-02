rootProject.name = "piko-patches-library"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

// In-app (dex) code shipped to the apps the patches modify. The root project is the patch-side jar.
include(":extension")
// Themeable settings registry, renderer and widgets; depends on :extension for logging.
include(":extension-settings")


dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io") { name = "JitPack" }

        // Morphe's patcher and patches library. Anonymous reads work; credentials are added
        // when present so CI and local builds use the same resolution path.
        val gprUser = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
        val gprKey = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            if (gprUser != null && gprKey != null) {
                credentials {
                    username = gprUser
                    password = gprKey
                }
            }
        }
        maven {
            name = "CrimeraPackages"
            url = uri("https://maven.pkg.github.com/crimera/morphe-bytecode")
            if (gprUser != null && gprKey != null) {
                credentials {
                    username = gprUser
                    password = gprKey
                }
            }
        }
    }
}
