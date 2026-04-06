package com.bitsycore.konfig

import com.bitsycore.konfig.types.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for the [Visibility] enum.
 */
class VisibilityTest {

    @Test fun `enum contains exactly two entries`() {
        assertEquals(2, Visibility.entries.size)
    }

    @Test fun `PUBLIC entry exists`() {
        assertEquals(Visibility.PUBLIC, Visibility.valueOf("PUBLIC"))
    }

    @Test fun `INTERNAL entry exists`() {
        assertEquals(Visibility.INTERNAL, Visibility.valueOf("INTERNAL"))
    }

    @Test fun `PUBLIC and INTERNAL are distinct`() {
        assertNotEquals(Visibility.PUBLIC, Visibility.INTERNAL)
    }

    @Test fun `ordinal of PUBLIC is 0`() {
        assertEquals(0, Visibility.PUBLIC.ordinal)
    }

    @Test fun `ordinal of INTERNAL is 1`() {
        assertEquals(1, Visibility.INTERNAL.ordinal)
    }

    @Test fun `name of PUBLIC is PUBLIC`() {
        assertEquals("PUBLIC", Visibility.PUBLIC.name)
    }

    @Test fun `name of INTERNAL is INTERNAL`() {
        assertEquals("INTERNAL", Visibility.INTERNAL.name)
    }

    @Test fun `entries list order is PUBLIC then INTERNAL`() {
        val entries = Visibility.entries
        assertEquals(Visibility.PUBLIC,   entries[0])
        assertEquals(Visibility.INTERNAL, entries[1])
    }

    @Test fun `valueOf is case-sensitive and throws on lowercase`() {
        var threw = false
        try {
            Visibility.valueOf("public")
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
