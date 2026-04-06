package com.bitsycore.konfig

import com.bitsycore.konfig.configs.VariantConfig
import com.bitsycore.konfig.configs.constantProvider
import com.bitsycore.konfig.types.BuildType
import kotlin.test.*

/**
 * Unit tests for [VariantConfig].
 *
 * Covers:
 * - Basic field declaration (literal and provider default)
 * - Duplicate field guard
 * - [FieldHandle] fluent API wired through the config
 * - debug {} / release {} block scopes
 * - getOrCreateField (creates on first call, retrieves on subsequent calls)
 * - Multiple fields coexist without interference
 */
class VariantConfigTest {

    private fun makeVariant(name: String = "test") = VariantConfig(name)

    // ── variantName ──────────────────────────────────────────────────────────

    @Test fun `variantName is stored`() {
        val v = makeVariant("prod")
        assertEquals("prod", v.variantName)
    }

    // ── field with literal default ────────────────────────────────────────────

    @Test fun `field with String default is added to fields list`() {
        val v = makeVariant()
        v.field("URL", "https://example.com")
        assertEquals(1, v.fields.size)
        assertEquals("URL", v.fields[0].fieldName)
    }

    @Test fun `field with Int default is added`() {
        val v = makeVariant()
        v.field("TIMEOUT", 30)
        assertEquals("TIMEOUT", v.fields[0].fieldName)
    }

    @Test fun `field with Boolean default is added`() {
        val v = makeVariant()
        v.field("LOGS_ENABLED", true)
        assertEquals("LOGS_ENABLED", v.fields[0].fieldName)
    }

    @Test fun `field with Long default is added`() {
        val v = makeVariant()
        v.field("BIG_NUM", 9_000_000_000L)
        assertEquals(9_000_000_000L, v.fields[0].resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `field with Double default is added`() {
        val v = makeVariant()
        v.field("PI", 3.14159)
        assertEquals(3.14159, v.fields[0].resolve(BuildType.DEBUG)!!.get())
    }

    @Test fun `field with Float default is added`() {
        val v = makeVariant()
        v.field("F", 2.0f)
        assertEquals(2.0f, v.fields[0].resolve(BuildType.DEBUG)!!.get())
    }

    // ── field with Provider default ───────────────────────────────────────────

    @Test fun `field with provider default stores provider`() {
        val v = makeVariant()
        v.field("API_KEY", constantProvider("secret"))
        val resolved = v.fields[0].resolve(BuildType.RELEASE)
        assertNotNull(resolved)
        assertEquals("secret", resolved.get())
    }

    // ── FieldHandle returned by field() ──────────────────────────────────────

    @Test fun `field returns non-null FieldHandle`() {
        val v = makeVariant()
        val handle = v.field("X", "val")
        assertNotNull(handle)
    }

    @Test fun `FieldHandle debug sets debug override`() {
        val v = makeVariant()
        v.field("URL", "prod").debug("dev")
        val fc = v.fields[0]
        assertEquals("dev",  fc.resolve(BuildType.DEBUG)!!.get())
        assertEquals("prod", fc.resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `FieldHandle release sets release override`() {
        val v = makeVariant()
        v.field("URL", "default").release("rel")
        val fc = v.fields[0]
        assertEquals("default", fc.resolve(BuildType.DEBUG)!!.get())
        assertEquals("rel",     fc.resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `FieldHandle debug and release can be chained independently`() {
        val v = makeVariant()
        val handle = v.field("LOG", "info")
        handle.debug("verbose")
        handle.release("error")
        val fc = v.fields[0]
        assertEquals("verbose", fc.resolve(BuildType.DEBUG)!!.get())
        assertEquals("error",   fc.resolve(BuildType.RELEASE)!!.get())
    }

    // ── Duplicate field guard ────────────────────────────────────────────────

    @Test fun `declaring duplicate field name throws IllegalArgumentException`() {
        val v = makeVariant()
        v.field("DUPE", "first")
        assertFailsWith<IllegalArgumentException> {
            v.field("DUPE", "second")
        }
    }

    @Test fun `duplicate guard error message contains field name`() {
        val v = makeVariant("myVariant")
        v.field("DUPE", "first")
        val ex = assertFailsWith<IllegalArgumentException> {
            v.field("DUPE", "second")
        }
        assertTrue(ex.message!!.contains("DUPE"))
    }

    @Test fun `duplicate guard error message contains variant name`() {
        val v = makeVariant("myVariant")
        v.field("DUPE", "first")
        val ex = assertFailsWith<IllegalArgumentException> {
            v.field("DUPE", "second")
        }
        assertTrue(ex.message!!.contains("myVariant"))
    }

    @Test fun `fields with different names do not trigger duplicate error`() {
        val v = makeVariant()
        v.field("A", "1")
        v.field("B", "2")
        assertEquals(2, v.fields.size)
    }

    // ── debug {} block ───────────────────────────────────────────────────────

    @Test fun `debug block sets override for DEBUG build type`() {
        val v = makeVariant()
        v.debug { field("LOG", "verbose") }
        val fc = v.fields.first { it.fieldName == "LOG" }
        assertEquals("verbose", fc.resolve(BuildType.DEBUG)!!.get())
        assertNull(fc.resolve(BuildType.RELEASE))
    }

    @Test fun `debug block with multiple fields stores all`() {
        val v = makeVariant()
        v.debug {
            field("A", "alpha")
            field("B", 99)
        }
        assertEquals(2, v.fields.size)
        assertEquals("alpha", v.fields.first { it.fieldName == "A" }.resolve(BuildType.DEBUG)!!.get())
        assertEquals(99, v.fields.first { it.fieldName == "B" }.resolve(BuildType.DEBUG)!!.get())
    }

    // ── release {} block ─────────────────────────────────────────────────────

    @Test fun `release block sets override for RELEASE build type`() {
        val v = makeVariant()
        v.release { field("LOG", "error") }
        val fc = v.fields.first { it.fieldName == "LOG" }
        assertEquals("error", fc.resolve(BuildType.RELEASE)!!.get())
        assertNull(fc.resolve(BuildType.DEBUG))
    }

    @Test fun `release block with provider stores provider`() {
        val v = makeVariant()
        v.release { field("KEY", constantProvider("relProv")) }
        val fc = v.fields.first { it.fieldName == "KEY" }
        assertEquals("relProv", fc.resolve(BuildType.RELEASE)!!.get())
    }

    // ── debug + release blocks for same field name ───────────────────────────

    @Test fun `field can appear in both debug and release blocks`() {
        val v = makeVariant()
        v.debug   { field("LOG", "verbose") }
        v.release { field("LOG", "error")   }
        // getOrCreateField reuses existing FieldConfig
        assertEquals(1, v.fields.size)
        val fc = v.fields[0]
        assertEquals("verbose", fc.resolve(BuildType.DEBUG)!!.get())
        assertEquals("error",   fc.resolve(BuildType.RELEASE)!!.get())
    }

    // ── Multiple independent fields ──────────────────────────────────────────

    @Test fun `multiple fields are independent`() {
        val v = makeVariant()
        v.field("URL",     "https://api.example.com")
        v.field("TIMEOUT", 30)
        v.field("DEBUG_MODE", false)

        assertEquals(3, v.fields.size)
        assertEquals("https://api.example.com", v.fields[0].resolve(BuildType.RELEASE)!!.get())
        assertEquals(30,    v.fields[1].resolve(BuildType.RELEASE)!!.get())
        assertEquals(false, v.fields[2].resolve(BuildType.RELEASE)!!.get())
    }

    // ── Empty variant ────────────────────────────────────────────────────────

    @Test fun `fresh VariantConfig has no fields`() {
        val v = makeVariant()
        assertTrue(v.fields.isEmpty())
    }
}
