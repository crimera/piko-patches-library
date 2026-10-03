import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    kotlin("jvm") version "2.2.21"
    `maven-publish`
    // Declared here so the :extension module shares this build's plugin classpath (one Kotlin
    // Gradle plugin version) instead of resolving AGP's bundled Kotlin separately. Matches the AGP
    // the Morphe patches Gradle plugin applies to consumer extension modules.
    id("com.android.library") version "9.1.0" apply false
}

group = "app.crimera"
description = "Shared patch-side infrastructure, resolver safeguards and the settings DSL for piko Morphe patch bundles"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11

    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        // The resolver helpers use experimental context parameters (`context(_: BytecodePatchContext)`).
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    // The patcher API every helper emits into.
    api("app.morphe:morphe-patcher:1.6.0")

    // Patch-side utilities (bundled resources, resource mapping, settings helpers).
    api("app.morphe:morphe-patches-library:1.5.0")

    // The dexlib2 fork the patcher and this library are built against.
    api("com.github.MorpheApp.smali:smali-dexlib2:d92701d947")

    // Typed bytecode emission used by the semantic bridge emitters.
    api("crimera:morphe-bytecode:0.1.3")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}

// The licence and attribution travel with the artifact, not just the repository.
tasks.jar {
    metaInf { from("LICENSE", "NOTICE") }
}

tasks.named<Jar>("sourcesJar") {
    metaInf { from("LICENSE", "NOTICE") }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = "app.crimera"
            artifactId = "piko-patches-library"
            version = project.version.toString()

            pom {
                name = "Piko Patches Library"
                description = "Shared patch-side infrastructure, resolver safeguards and the settings DSL for piko Morphe patch bundles"
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
