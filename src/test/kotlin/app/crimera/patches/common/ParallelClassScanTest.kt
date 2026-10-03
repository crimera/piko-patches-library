package app.crimera.patches.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParallelClassScanTest {
    /** A scan large enough to run in parallel must still read like the serial `classDefForEach`. */
    @Test
    fun `parallel flat map keeps element order`() {
        val items = (0 until 10_000).toList()

        val result = items.flatMapParallel { listOf(it, -it) }

        assertEquals(items.flatMap { listOf(it, -it) }, result)
    }

    /** The first failing class must surface as itself, not as an executor wrapper. */
    @Test
    fun `parallel flat map rethrows the original exception`() {
        val items = (0 until 10_000).toList()

        val failure =
            assertFailsWith<IllegalStateException> {
                items.flatMapParallel { if (it == 7_000) error("boom at $it") else listOf(it) }
            }

        assertEquals("boom at 7000", failure.message)
    }
}
