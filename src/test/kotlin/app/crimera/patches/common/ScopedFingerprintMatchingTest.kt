package app.crimera.patches.common

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.HiddenApiRestriction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableAnnotation
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScopedFingerprintMatchingTest {
    @Test
    fun `shape and native all-match agree and replaced owners are resolved after mutation`() {
        val originalMethod = fixtureMethod("shape-candidate")
        val fixture = BytecodeContextFixture(fixtureClass(originalMethod))

        fixture.use { contextFixture ->
            val shapeFingerprint = ShapeFingerprint(
                returnType = "V",
                parameters = emptyList(),
                custom = ::containsShapeCandidate,
            )
            val nativeFingerprint = Fingerprint(
                returnType = "V",
                parameters = emptyList(),
                custom = ::containsShapeCandidate,
            )

            val shapeMatches = with(contextFixture.context) {
                shapeFingerprint.scopedMatchAllOrNull()
            }
            val nativeMatches = with(contextFixture.context) {
                nativeFingerprint.matchAllOrNull()
            }

            assertEquals(1, shapeMatches?.size)
            assertEquals(
                nativeMatches?.map { match -> match.originalMethod.toString() },
                shapeMatches?.map { match -> match.originalMethod.toString() },
            )

            val currentOwner = contextFixture.context.mutableClassDefByOrNull(FIXTURE_OWNER)
                ?: error("fixture owner was not found")
            val currentMethod = currentOwner.methods.single { method -> method.name == FIXTURE_METHOD }
            currentMethod.implementation?.removeInstruction(0)
                ?: error("fixture method has no implementation")

            assertNull(with(contextFixture.context) { shapeFingerprint.scopedMatchAllOrNull() })
        }
    }

    @Test
    fun `shape search re-resolves a mutable original owner after its method is replaced`() {
        val originalOwner = MutableClass(fixtureClass(fixtureMethod(SHAPE_MARKER)))
        val fixture = BytecodeContextFixture(originalOwner)

        fixture.use { contextFixture ->
            val fingerprint = ShapeFingerprint(
                returnType = "V",
                parameters = emptyList(),
                custom = ::containsShapeCandidate,
            )

            assertEquals(
                1,
                with(contextFixture.context) { fingerprint.scopedMatchAllOrNull()?.size },
            )

            val currentMethod = originalOwner.methods.single { method -> method.name == FIXTURE_METHOD }
            originalOwner.methods.remove(currentMethod)
            originalOwner.methods.add(MutableMethod(fixtureMethod("replaced-method")))

            assertNull(with(contextFixture.context) { fingerprint.scopedMatchAllOrNull() })
        }
    }

    @Test
    fun `missing nested-class fingerprint remains an optional null result`() {
        val fixture = BytecodeContextFixture(fixtureClass(fixtureMethod("unrelated")))

        fixture.use { contextFixture ->
            val fingerprint = Fingerprint(
                classFingerprint = Fingerprint(definingClass = MISSING_OWNER),
                returnType = "V",
                parameters = emptyList(),
            )

            assertNull(with(contextFixture.context) { fingerprint.scopedMatchAllOrNull() })
        }
    }

    private class BytecodeContextFixture(classDef: ClassDef) : AutoCloseable {
        private val root = Files.createTempDirectory("scoped-fingerprint-test").toFile()
        val context = createContext(root)

        init {
            // Patcher exposes no public in-memory context fixture, so seed its real class index here.
            val patchClassesType = Class.forName("app.morphe.patcher.util.PatchClasses")
            val patchClassesConstructor = patchClassesType.declaredConstructors.single { constructor ->
                constructor.parameterCount == 1 && Set::class.java.isAssignableFrom(constructor.parameterTypes[0])
            }
            val patchClasses = patchClassesConstructor.newInstance(setOf(classDef))
            BytecodePatchContext::class.java.getDeclaredMethod(
                "setPatchClasses\$morphe_patcher",
                patchClassesType,
            ).invoke(context, patchClasses)
        }

        override fun close() {
            context.close()
            root.deleteRecursively()
        }

        private fun createContext(root: File): BytecodePatchContext {
            val metadataType = Class.forName("app.morphe.patcher.PackageMetadata")
            val metadata = metadataType.declaredConstructors.single { constructor ->
                constructor.parameterCount == 4
            }
                .newInstance("app.crimera.test", "1", "1", null)
            val constructor = BytecodePatchContext::class.java.declaredConstructors.single { candidate ->
                candidate.parameterCount == 2 && candidate.parameterTypes[0] == PatcherConfig::class.java
            }
            return constructor.newInstance(
                PatcherConfig(
                    apkFile = File(root, "unused.apk"),
                    temporaryFilesPath = root,
                ),
                metadata,
            ) as BytecodePatchContext
        }
    }

    private companion object {
        const val FIXTURE_OWNER = "Lapp/crimera/test/ScopedShape;"
        const val FIXTURE_METHOD = "candidate"
        const val MISSING_OWNER = "Lapp/crimera/test/MissingOwner;"
        const val SHAPE_MARKER = "shape-candidate"

        fun fixtureClass(method: ImmutableMethod): ImmutableClassDef = ImmutableClassDef(
            FIXTURE_OWNER,
            AccessFlags.PUBLIC.value,
            "Ljava/lang/Object;",
            emptyList(),
            null,
            emptySet<ImmutableAnnotation>(),
            emptyList<ImmutableField>(),
            listOf(method),
        )

        fun fixtureMethod(marker: String): ImmutableMethod {
            val implementation = MethodImplementationBuilder(1)
            implementation.addInstruction("const-string v0, \"$marker\"".toInstruction())
            implementation.addInstruction("return-void".toInstruction())
            return ImmutableMethod(
                FIXTURE_OWNER,
                FIXTURE_METHOD,
                emptyList<ImmutableMethodParameter>(),
                "V",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                emptySet<ImmutableAnnotation>(),
                emptySet<HiddenApiRestriction>(),
                implementation.methodImplementation,
            )
        }

        fun containsShapeCandidate(method: Method, classDef: ClassDef): Boolean =
            classDef.type == FIXTURE_OWNER &&
                method.implementation?.instructions?.any { instruction ->
                    instruction.getReference<StringReference>()?.string == SHAPE_MARKER
                } == true
    }
}
