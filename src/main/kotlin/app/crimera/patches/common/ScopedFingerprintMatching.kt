package app.crimera.patches.common

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionFilter
import app.morphe.patcher.Match
import app.morphe.patcher.StringFilter
import app.morphe.patcher.patch.classDefByOrNull
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method

private data class MethodShape(
    val returnType: String,
    val parameterCount: Int,
)

private data class IndexedMethod(
    val originalClass: ClassDef,
    val originalMethod: Method,
) {
    fun resolveCurrent(): Method? {
        val currentClass = classDefByOrNull(originalClass.type) ?: return null
        if (currentClass === originalClass && currentClass !is MutableClass) return originalMethod

        return currentClass.methods.singleOrNull { currentMethod ->
            currentMethod.name == originalMethod.name &&
                currentMethod.returnType == originalMethod.returnType &&
                currentMethod.parameterTypes == originalMethod.parameterTypes
        }
    }
}

private object ShapeMethodIndex {
    private val methodsByShape =
        ApkScoped<Map<MethodShape, List<IndexedMethod>>> {
            buildMap<MethodShape, MutableList<IndexedMethod>> {
                classDefForEach { classDef ->
                    classDef.methods.forEach { method ->
                        val methodShape = MethodShape(method.returnType, method.parameterTypes.size)
                        getOrPut(methodShape, ::mutableListOf).add(IndexedMethod(classDef, method))
                    }
                }
            }
        }

    fun methods(shape: MethodShape): List<Method> =
        methodsByShape.get()[shape].orEmpty().mapNotNull { method -> method.resolveCurrent() }
}

private fun String.isExactTypeDeclaration(): Boolean =
    length == 1 && single() in "BCDFIJSVZ" ||
        startsWith('L') && endsWith(';') ||
        startsWith('[') && endsWith(';')

/**
 * Fingerprint that records its declared return type and parameter arity for the retained shape
 * index. Ordinary fingerprints, and shape fingerprints with a string, use Patcher's native
 * all-match search, which finds the classes with the string in its instruction indexes.
 */
open class ShapeFingerprint(
    returnType: String? = null,
    parameters: List<String>? = null,
    name: String? = null,
    accessFlags: List<AccessFlags>? = null,
    filters: List<InstructionFilter>? = null,
    strings: List<String>? = null,
    custom: ((method: Method, classDef: ClassDef) -> Boolean)? = null,
) : Fingerprint(
    name = name,
    accessFlags = accessFlags,
    returnType = returnType,
    parameters = parameters,
    filters = filters,
    strings = strings,
    custom = custom,
) {
    val declaredReturnType: String? = returnType?.takeIf(String::isExactTypeDeclaration)
    val declaredParameterCount: Int? = parameters?.size
    internal val hasString: Boolean = strings != null || filters?.any { it is StringFilter } == true
}

/** Native all-match for ordinary fingerprints; explicit declarations retain the measured shape table. */
fun Fingerprint.scopedMatchAllOrNull(): List<Match>? {
    val nestedClassFingerprint = classFingerprint
    if (nestedClassFingerprint != null) {
        val originalClass = nestedClassFingerprint.matchOrNull()?.originalClassDef ?: return null
        val classDef = classDefByOrNull(originalClass.type) ?: return null
        val matches = buildList {
            classDef.methods.forEach { method ->
                val match = matchOrNull(method, classDef) ?: return@forEach
                add(match)
                clearMatch()
            }
        }
        return matches.ifEmpty { null }
    }

    val shapeFingerprint = this as? ShapeFingerprint ?: return matchAllOrNull()
    if (shapeFingerprint.hasString) return matchAllOrNull()
    val returnType = shapeFingerprint.declaredReturnType ?: return matchAllOrNull()
    val parameterCount = shapeFingerprint.declaredParameterCount ?: return matchAllOrNull()
    val shape = MethodShape(returnType, parameterCount)
    val candidates = ShapeMethodIndex.methods(shape)
    val matches = buildList {
        candidates.forEach { method ->
            val classDef = classDefByOrNull(method.definingClass) ?: return@forEach
            val match = matchOrNull(method, classDef) ?: return@forEach
            add(match)
            clearMatch()
        }
    }
    return matches.ifEmpty { null }
}

fun Fingerprint.scopedMatchAll(): List<Match> =
    scopedMatchAllOrNull() ?: throw patchException()
