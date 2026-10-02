package app.crimera.patches.common

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The shared package is consumed by every app patch set. If it imports app-specific code it stops
 * being extractable into a library and a rewrite silently couples back to one app's namespace, so
 * the dependency direction is enforced instead of reviewed by hand.
 */
class SharedInfrastructureBoundaryTest {
    @Test
    fun `shared infrastructure does not import app-specific packages`() {
        val roots = listOf(
            Paths.get("src/main/kotlin/app/crimera/patches/common"),
            Paths.get("patches-settings/src/main/kotlin/app/crimera/patches/settings"),
        )
        roots.forEach { assertTrue(Files.isDirectory(it), "shared package is missing: $it") }

        val forbidden = listOf(
            "app.crimera.patches.newx",
            "app.crimera.patches.instagram",
            "app.crimera.patches.twitter",
        )
        val violations = mutableListOf<String>()
        roots.forEach { root ->
            Files.walk(root).use { paths ->
                paths
                    .filter { it.isRegularFile() && it.toString().endsWith(".kt") }
                    .forEach { path ->
                        path.readText().lineSequence().forEachIndexed { index, line ->
                            val trimmed = line.trim()
                            if (trimmed.startsWith("import ").not()) return@forEachIndexed
                            val imported = trimmed.removePrefix("import ")
                            if (forbidden.any(imported::startsWith)) {
                                violations += "$path:${index + 1}: $trimmed"
                            }
                        }
                    }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "shared infrastructure imports app-specific code:\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun `shared extension code does not reference app-specific packages`() {
        val roots = listOf(
            Paths.get("extension/src/main/java"),
            Paths.get("extension-settings/src/main/java"),
        )
        roots.forEach { assertTrue(Files.isDirectory(it), "shared extension source is missing: $it") }

        val forbidden = listOf(
            "app.morphe.extension.newx",
            "app.morphe.extension.instagram",
            "app.morphe.extension.twitter",
        )
        val violations = mutableListOf<String>()
        roots.forEach { root ->
            Files.walk(root).use { paths ->
                paths
                    .filter { it.isRegularFile() && it.toString().endsWith(".java") }
                    .forEach { path ->
                        path.readText().lineSequence().forEachIndexed { index, line ->
                            if (forbidden.any(line::contains)) violations += "$path:${index + 1}: ${line.trim()}"
                        }
                    }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "shared extension code references app-specific code:\n" + violations.joinToString("\n"),
        )
    }
}
