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
        val root = Paths.get("src/main/kotlin/app/crimera/patches/common")
        assertTrue(Files.isDirectory(root), "shared package is missing: $root")

        val forbidden = listOf(
            "app.crimera.patches.newx",
            "app.crimera.patches.instagram",
            "app.crimera.patches.twitter",
        )
        val violations = mutableListOf<String>()
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

        assertTrue(
            violations.isEmpty(),
            "shared infrastructure imports app-specific code:\n" + violations.joinToString("\n"),
        )
    }
}
