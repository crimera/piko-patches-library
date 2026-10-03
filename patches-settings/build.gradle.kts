import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    kotlin("jvm")
    `maven-publish`
}

group = "app.crimera"
version = rootProject.version
description = "Patch-side DSL and bytecode injection for contributing settings to the shared piko settings registry"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11

    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        // The injection helpers use experimental context parameters (`context(_: BytecodePatchContext)`).
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    // The patcher API every helper emits into. Consumers pin their own (newer) patcher; this is
    // the oldest version the code is compiled against.
    api("app.morphe:morphe-patcher:1.6.0")

    // `cloneMutable` and friends.
    api("app.morphe:morphe-patches-library:1.5.0")

    // The dexlib2 fork the patcher is built against.
    api("com.github.MorpheApp.smali:smali-dexlib2:d92701d947")

    // Typed bytecode emission.
    api("crimera:morphe-bytecode:0.1.3")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed") }
}

tasks.jar {
    metaInf { from(rootProject.file("LICENSE"), rootProject.file("NOTICE")) }
}

tasks.named<Jar>("sourcesJar") {
    metaInf { from(rootProject.file("LICENSE"), rootProject.file("NOTICE")) }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = "app.crimera"
            artifactId = "piko-patches-settings"
            version = project.version.toString()

            pom {
                name = "Piko Patches Settings"
                description = project.description
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
