package com.bitsycore.konfig

import com.bitsycore.konfig.configs.constantProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [constantProvider] and the internal [ConstantProvider] it creates.
 *
 * The [ConstantProvider] class is private; we access its behaviour through the
 * public [constantProvider] factory and the [org.gradle.api.provider.Provider] interface.
 */
class ConstantProviderTest {

    // ── get / getOrNull / isPresent ──────────────────────────────────────────

    @Test fun `String provider get returns value`() {
        val p = constantProvider("hello")
        assertEquals("hello", p.get())
    }

    @Test fun `Int provider get returns value`() {
        val p = constantProvider(42)
        assertEquals(42, p.get())
    }

    @Test fun `Boolean provider get returns true`() {
        val p = constantProvider(true)
        assertEquals(true, p.get())
    }

    @Test fun `Boolean provider get returns false`() {
        val p = constantProvider(false)
        assertEquals(false, p.get())
    }

    @Test fun `Long provider get returns value`() {
        val p = constantProvider(Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, p.get())
    }

    @Test fun `Double provider get returns value`() {
        val p = constantProvider(3.14)
        assertEquals(3.14, p.get())
    }

    @Test fun `Float provider get returns value`() {
        val p = constantProvider(1.5f)
        assertEquals(1.5f, p.get())
    }

    @Test fun `isPresent is always true`() {
        assertTrue(constantProvider("anything").isPresent)
    }

    @Test fun `getOrNull returns value not null`() {
        val p = constantProvider("value")
        assertNotNull(p.getOrNull())
        assertEquals("value", p.getOrNull())
    }

    @Test fun `getOrElse returns stored value ignoring default`() {
        val p = constantProvider("stored")
        assertEquals("stored", p.getOrElse("fallback"))
    }

    // ── map ──────────────────────────────────────────────────────────────────

    @Test fun `map transforms value`() {
        val p = constantProvider("hello")
        val mapped = p.map { it.uppercase() }
        assertEquals("HELLO", mapped.get())
    }

    @Test fun `map on Int transforms to String`() {
        val p = constantProvider(7)
        val mapped = p.map { "num=$it" }
        assertEquals("num=7", mapped.get())
    }

    @Test fun `nested map chains correctly`() {
        val p = constantProvider(2)
        val result = p.map { it * 3 }.map { it + 1 }
        assertEquals(7, result.get())
    }

    // ── flatMap ──────────────────────────────────────────────────────────────

    @Test fun `flatMap returns inner provider value`() {
        val p = constantProvider("base")
        val result = p.flatMap { constantProvider("flat:$it") }
        assertEquals("flat:base", result.get())
    }

    // ── filter ───────────────────────────────────────────────────────────────

    @Test fun `filter passes when spec is satisfied`() {
        val p = constantProvider(10)
        val filtered = p.filter { it > 5 }
        assertEquals(10, filtered.get())
    }

    @Test fun `filter throws when spec is not satisfied`() {
        val p = constantProvider(3)
        var threw = false
        try {
            p.filter { it > 5 }.get()
        } catch (_: NoSuchElementException) {
            threw = true
        }
        assertTrue(threw)
    }

    // ── orElse ───────────────────────────────────────────────────────────────

    @Test fun `orElse with value returns stored value`() {
        val p = constantProvider("original")
        assertEquals("original", p.orElse("other").get())
    }

    @Test fun `orElse with provider returns stored value`() {
        val p = constantProvider("original")
        assertEquals("original", p.orElse(constantProvider("other")).get())
    }

    // ── zip ──────────────────────────────────────────────────────────────────

    @Test fun `zip combines two providers`() {
        val p1 = constantProvider("Hello")
        val p2 = constantProvider("World")
        val zipped = p1.zip(p2) { a, b -> "$a $b" }
        assertEquals("Hello World", zipped.get())
    }

    @Test fun `zip with Int providers produces sum`() {
        val p1 = constantProvider(3)
        val p2 = constantProvider(4)
        val sum = p1.zip(p2) { a, b -> a + b }
        assertEquals(7, sum.get())
    }

    // ── identity / equality ──────────────────────────────────────────────────

    @Test fun `two providers with same value each return that value independently`() {
        val p1 = constantProvider("same")
        val p2 = constantProvider("same")
        assertEquals(p1.get(), p2.get())
    }

    @Test fun `provider wrapping empty string returns empty string`() {
        val p = constantProvider("")
        assertEquals("", p.get())
        assertTrue(p.isPresent)
    }
}
