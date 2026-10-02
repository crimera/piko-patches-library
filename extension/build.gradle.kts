plugins {
    id("com.android.library")
    `maven-publish`
}

group = "app.crimera"
version = rootProject.version

android {
    namespace = "app.crimera.piko.extension"
    compileSdk = 36

    defaultConfig {
        // The lowest floor among the apps consuming the library (Instagram builds start at API 28,
        // the extension floor is 26).
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // The extension is dexed for API 26+; platform APIs newer than that must be guarded.
        checkOnly += setOf("NewApi")
        abortOnError = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // Provided at runtime by the Morphe shared extension bundle, so it is not part of this artifact.
    // Consumers pin their own version of it.
    compileOnly("app.morphe:morphe-extensions-library:1.5.0")
    compileOnly("androidx.annotation:annotation:1.9.1")

    testImplementation("junit:junit:4.13.2")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "app.crimera"
                artifactId = "piko-extension-library"
                version = project.version.toString()

                pom {
                    name = "Piko Extension Library"
                    description = "Shared in-app (dex) code for piko Morphe patch bundles"
                    url = "https://github.com/crimera/piko-patches-library"
                    licenses {
                        license {
                            name = "GNU General Public License v3.0 or later"
                            url = "https://www.gnu.org/licenses/gpl-3.0.html"
                        }
                    }
                    developers {
                        developer {
                            id = "crimera"
                            name = "crimera"
                        }
                    }
                    scm {
                        url = "https://github.com/crimera/piko-patches-library"
                        connection = "scm:git:https://github.com/crimera/piko-patches-library.git"
                    }
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/crimera/piko-patches-library")
                credentials {
                    username = providers.gradleProperty("gpr.user").getOrElse(System.getenv("GITHUB_ACTOR") ?: "")
                    password = providers.gradleProperty("gpr.key").getOrElse(System.getenv("GITHUB_TOKEN") ?: "")
                }
            }
        }
    }
}
