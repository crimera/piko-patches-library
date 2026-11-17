package app.crimera.patches.common

import app.morphe.patcher.PackageMetadata
import app.morphe.patcher.patch.packageMetadata
import java.util.WeakHashMap

/**
 * State shared by the patches of the APK being patched, created when it is first used.
 *
 * Patching another APK in the same process starts with new state. The patcher creates one
 * [PackageMetadata] for each APK it patches, so the state is kept for it and released with it.
 */
class ApkScoped<T : Any>(private val create: () -> T) {
    private val values = WeakHashMap<PackageMetadata, T>()

    /**
     * The state of the APK being patched. [create] runs at most once for each APK, and while it
     * runs, other threads wait for it.
     */
    fun get(): T = synchronized(values) { values.getOrPut(packageMetadata, create) }
}
