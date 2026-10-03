package app.crimera.tools.lint

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Generic resolver rules. App-specific resolver fixtures stay in their patch repository and
 * exercise these rules with real release code.
 */
class ResolverLinterTest {
    @Test
    fun `raw candidate selection is rejected`() {
        val findings = lint("val selected = candidates.first()")

        assertEquals(listOf(ResolverLinter.Rule.RAW_FIRST), findings.map { it.rule })
    }

    @Test
    fun `raw find and last are rejected`() {
        val findings =
            lint(
                """
                val selected = methods.find { it.name == "target" }
                val other = fields.last()
                """.trimIndent(),
            )

        assertEquals(
            setOf(
                ResolverLinter.Rule.RAW_FIND,
                ResolverLinter.Rule.RAW_LAST,
            ),
            findings.map { it.rule }.toSet(),
        )
    }

    @Test
    fun `explicit cardinality guard permits raw first`() {
        val findings =
            lint(
                """
                if (candidates.size != 1) throw IllegalStateException()
                val selected = candidates.first()
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `explicit cardinality guard permits indexed access with nullable failure`() {
        val findings =
            lint(
                """
                fun resolve(candidates: List<String>): String? {
                    if (candidates.size != 1) return null
                    return candidates[0]
                }
                fun isTarget(candidates: List<String>): Boolean {
                    if (candidates.size != 1) return false
                    return candidates[0].isNotEmpty()
                }
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `unguarded indexed candidate access is rejected`() {
        val findings = lint("val selected = candidates[0]")

        assertEquals(listOf(ResolverLinter.Rule.RAW_INDEX), findings.map { it.rule })
    }

    @Test
    fun `parameter type indexed access is not a resolver candidate`() {
        val findings = lint("val parameterType = parameterTypes[0]")

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `at most one proof does not permit required candidate selection`() {
        val findings =
            lint(
                """
                if (candidates.size > 1) throw IllegalStateException()
                val first = candidates.first()
                val last = candidates.last()
                val found = candidates.find { it.isTarget }
                val single = candidates.single()
                """.trimIndent(),
            )

        assertEquals(
            setOf(
                ResolverLinter.Rule.RAW_FIRST,
                ResolverLinter.Rule.RAW_LAST,
                ResolverLinter.Rule.RAW_FIND,
                ResolverLinter.Rule.RAW_SINGLE,
            ),
            findings.map { it.rule }.toSet(),
        )
    }

    @Test
    fun `singleOrNull safe call is rejected as nullable fallthrough`() {
        val findings = lint("candidates.singleOrNull()?.patch()")

        assertEquals(listOf(ResolverLinter.Rule.NULLABLE_SINGLE), findings.map { it.rule })
    }

    @Test
    fun `singleOrNull with throwing fallback is accepted`() {
        val findings =
            lint(
                "val selected = candidates.singleOrNull() ?: throw IllegalStateException()",
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `nullable selection followed by a returning null branch is rejected`() {
        val findings =
            lint(
                """
                val selected = candidates.singleOrNull()
                if (selected == null) return
                selected.patch()
                """.trimIndent(),
            )

        assertEquals(listOf(ResolverLinter.Rule.NULLABLE_SINGLE), findings.map { it.rule })
    }

    @Test
    fun `nullable first and last are rejected when they fall through`() {
        val findings =
            lint(
                """
                val first = matches.firstOrNull()?.patch()
                val last = matches.lastOrNull() ?: return
                """.trimIndent(),
            )

        assertEquals(
            setOf(
                ResolverLinter.Rule.NULLABLE_FIRST,
                ResolverLinter.Rule.NULLABLE_LAST,
            ),
            findings.map { it.rule }.toSet(),
        )
    }

    @Test
    fun `at most one guard permits optional singleOrNull`() {
        val findings =
            lint(
                """
                if (matches.size > 1) throw IllegalStateException()
                matches.singleOrNull()?.patch()
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `shared cardinality helpers prove subsequent selections`() {
        val findings =
            lint(
                """
                requireExactlyOne("required candidate", candidates)
                val selected = candidates.first()
                requireAtMostOne("optional candidate", matches)
                val optional = matches.singleOrNull()?.patch()
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `mapNotNull candidate dropping is reported at the map`() {
        val findings =
            lint(
                """
                val candidates = methods.mapNotNull { method ->
                    if (method.name == "target") method else return@mapNotNull null
                }
                candidates.singleOrNull()?.patch()
                """.trimIndent(),
            )

        assertTrue(findings.any { it.rule == ResolverLinter.Rule.MAP_NOT_NULL }, findings.toString())
        assertTrue(findings.size == 2, "actual=$findings")
        assertEquals(1, findings.first { it.rule == ResolverLinter.Rule.MAP_NOT_NULL }.line)
    }

    @Test
    fun `implicit mapNotNull filtering is also reported`() {
        val findings =
            lint(
                """
                val selected = methods.mapNotNull { it.takeIf { method -> method.isTarget } }.first()
                """.trimIndent(),
            )

        assertTrue(findings.any { it.rule == ResolverLinter.Rule.MAP_NOT_NULL }, findings.toString())
    }

    @Test
    fun `mapNotNull with exact guard is accepted`() {
        val findings =
            lint(
                """
                val candidates = instructions.mapNotNull { instruction ->
                    instruction.takeIf { it.isTarget } ?: return@mapNotNull null
                }
                if (candidates.size != 1) throw IllegalStateException()
                candidates.single()
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `instruction order scans are allowed`() {
        val findings =
            lint(
                """
                val firstInstruction = method.instructions.first()
                val lastInstruction = match.instructionMatches.last()
                val register = instruction.registersUsed.firstOrNull()
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `bounded lookup over instruction candidates is allowed`() {
        val findings =
            lint(
                """
                val pageLookupCandidates = instructions.withIndex().filter { it.value.isLookup }
                val previous = pageLookupCandidates.lastOrNull { candidateIndex < index }
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `comments and strings are ignored`() {
        val findings =
            lint(
                """
                // val selected = candidates.first()
                val documentation = "methods.last() and candidates.find { true }"
                /* candidates.singleOrNull()?.patch() */
                val selected = instructions.first()
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `directive-looking text in a string does not suppress findings`() {
        val findings =
            lint(
                """
                val documentation = "newx-resolver-lint: allow raw-first"
                val selected = candidates.first()
                """.trimIndent(),
            )

        assertEquals(listOf(ResolverLinter.Rule.RAW_FIRST), findings.map { it.rule })
    }

    @Test
    fun `line directives can document an exceptional order choice`() {
        val findings =
            lint(
                """
                // newx-resolver-lint: allow raw-first because bytecode order is the contract
                val selected = candidates.first()
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `typed hook without an explicit branch policy is reported`() {
        // Regression for the 12.29.0-alpha.04 inline-download-button abort: the fail-closed guard
        // stayed silent on 12.28.0-prod.01, so the omission only surfaced on the newer APK.
        val findings =
            lint(
                """
                method.insertHook(
                    index = 0,
                ) {
                    returnVoid()
                }
                """.trimIndent(),
            )

        assertEquals(
            listOf(ResolverLinter.Rule.TYPED_HOOK_POLICY),
            findings.map { it.rule },
        )
        assertEquals(1, findings.single().line)
    }

    @Test
    fun `typed hook with an explicit branch policy is accepted`() {
        val findings =
            lint(
                """
                method.insertHook(index = 0, relocateBranchTargets = false) {
                    returnVoid()
                }
                method.insertHook(
                    index = 1,
                    relocateBranchTargets = true,
                ) {
                    returnVoid()
                }
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `typed hook policy finding can be suppressed with a directive`() {
        val findings =
            lint(
                """
                // newx-resolver-lint: allow typed-hook-policy because the guard is exercised here
                method.insertHook(0) {
                    returnVoid()
                }
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `typed hook declaration and mentions are not call sites`() {
        val findings =
            lint(
                """
                // insertHook(0) without the policy is a call, not this comment.
                val documentation = "insertHook(index = 0)"
                internal fun MutableMethod.insertHook(
                    index: Int,
                    block: Block.() -> Unit,
                ): Insertion = error("fixture")
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `typed hook policy is matched inside a nested argument list`() {
        val findings =
            lint(
                """
                method.insertHook(index = helper.resolve(0, 1)) {
                    returnVoid()
                }
                """.trimIndent(),
            )

        assertEquals(
            listOf(ResolverLinter.Rule.TYPED_HOOK_POLICY),
            findings.map { it.rule },
        )
    }

    @Test
    fun `lintDirectory is deterministic and only scans Kotlin files`() {
        val root = Files.createTempDirectory("newx-resolver-linter")
        try {
            root.resolve("z/Later.kt").apply {
                parent.createDirectories()
                writeText("val selected = candidates.last()")
            }
            root.resolve("a/Earlier.kt").apply {
                parent.createDirectories()
                writeText("val selected = candidates.first()")
            }
            root.resolve("Ignored.java").writeText("val selected = candidates.first()")

            val firstRun = ResolverLinter.lintDirectory(root)
            val secondRun = ResolverLinter.lintDirectory(root)

            assertEquals(firstRun, secondRun)
            assertEquals(
                listOf("a/Earlier.kt", "z/Later.kt"),
                firstRun.map { it.path },
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `lintDirectory rejects a source root without Kotlin files`() {
        val root = Files.createTempDirectory("newx-resolver-linter-empty")
        try {
            val exception =
                assertFailsWith<IllegalArgumentException> {
                    ResolverLinter.lintDirectory(root)
                }

            assertTrue(exception.message.orEmpty().contains("No Kotlin resolver sources"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `rigid fingerprint parameter list is reported`() {
        val findings =
            lint(
                """
                Fingerprint(
                    parameters = listOf(
                        "Lcom/x/urt/items/post/j5;",
                        "Landroidx/compose/foundation/layout/b3;",
                        "Lcom/x/urt/items/post/p5;",
                        "Landroidx/compose/ui/Modifier;",
                        "Landroidx/compose/runtime/Composer;",
                        "I",
                    ),
                )
                """.trimIndent(),
            )

        assertEquals(listOf(ResolverLinter.Rule.RIGID_SIGNATURE), findings.map { it.rule })
        assertTrue(!ResolverLinter.Rule.RIGID_SIGNATURE.advisory)
    }

    @Test
    fun `short parameter list without compose descriptors is not rigid`() {
        val findings =
            lint(
                """val fingerprint = Fingerprint(parameters = listOf("Z", navigationType, tabType))""",
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `fingerprint documentation example is not a rigid finding`() {
        val findings =
            lint(
                """
                // parameters = listOf("Landroidx/compose/ui/Modifier;", "I", "I", "I")
                val doc = "parameters = listOf(\"L\", \"L\", \"L\", \"L\")"
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `exact interface descriptor equality is reported`() {
        val findings = lint("""if (method.returnType == "Ljava/util/Set;") return candidate""")

        assertEquals(
            listOf(ResolverLinter.Rule.EXACT_INTERFACE_TYPE),
            findings.map { it.rule },
        )
        assertTrue(!ResolverLinter.Rule.EXACT_INTERFACE_TYPE.advisory)
    }

    @Test
    fun `assignability checks are not exact-interface findings`() {
        val findings =
            lint(
                """
                val setLike = descriptor == SET_DESCRIPTOR ||
                    classDef.interfaces.any { it.toString() == SET_DESCRIPTOR }
                classDef.methods.filter { method -> method.returnType.toString() != OBJECT_DESCRIPTOR }
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `single-hop register containment is reported`() {
        val findings = lint("val feeds = instruction.registersUsed.contains(prefetchRegister)")

        assertEquals(
            listOf(ResolverLinter.Rule.SINGLE_HOP_REGISTER),
            findings.map { it.rule },
        )
        assertTrue(!ResolverLinter.Rule.SINGLE_HOP_REGISTER.advisory)
    }

    @Test
    fun `literal register containment is not a single-hop finding`() {
        val findings = lint("val usesFirst = instruction.registersUsed.contains(0)")

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `propagated register sets are not single-hop findings`() {
        val findings =
            lint(
                """
                val feeds = instruction.registersUsed.any { register -> register in seededRegisters }
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `anchor finding can be suppressed`() {
        val findings =
            lint(
                """
                // newx-resolver-lint: allow rigid-signature because this fingerprint pins the ABI on purpose
                val fingerprint = Fingerprint(parameters = listOf("L", "L", "L", "L"))
                """.trimIndent(),
            )

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `existing cardinality rules remain gating`() {
        assertTrue(!ResolverLinter.Rule.RAW_FIRST.advisory)
        assertTrue(!ResolverLinter.Rule.TYPED_HOOK_POLICY.advisory)
    }

    private fun lint(source: String): List<ResolverLinter.Finding> =
        ResolverLinter.lintSource("Fixture.kt", source)
}
