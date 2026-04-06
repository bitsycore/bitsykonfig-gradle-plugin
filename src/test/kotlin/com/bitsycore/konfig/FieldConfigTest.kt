package com.bitsycore.konfig

import com.bitsycore.konfig.configs.FieldConfig
import com.bitsycore.konfig.configs.constantProvider
import com.bitsycore.konfig.types.BuildType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for [FieldConfig]: construction, default resolution,
 * build-type overrides (literal and provider), and resolve() priority.
 */
class FieldConfigTest {

    // ── Construction ─────────────────────────────────────────────────────────

    @Test fun `fieldName is stored correctly`() {
        val fc = FieldConfig("MY_FIELD", String::class.java, null)
        assertEquals("MY_FIELD", fc.fieldName)
    }

    @Test fun `type is stored as javaObjectType`() {
        val fc = FieldConfig("F", Int::class.javaObjectType, null)
        assertEquals(Int::class.javaObjectType, fc.type)
    }

    @Test fun `default provider is null when not supplied`() {
        val fc = FieldConfig("F", String::class.java, null)
        // resolve with no overrides and no default → null
        assertNull(fc.resolve(BuildType.DEBUG))
        assertNull(fc.resolve(BuildType.RELEASE))
    }

    // ── Default resolution ───────────────────────────────────────────────────

    @Test fun `resolve returns default when no override for DEBUG`() {
        val fc = FieldConfig("F", String::class.java, constantProvider("default"))
        val resolved = fc.resolve(BuildType.DEBUG)
        assertNotNull(resolved)
        assertEquals("default", resolved.get())
    }

    @Test fun `resolve returns default when no override for RELEASE`() {
        val fc = FieldConfig("F", String::class.java, constantProvider("default"))
        val resolved = fc.resolve(BuildType.RELEASE)
        assertNotNull(resolved)
        assertEquals("default", resolved.get())
    }

    // ── BuildType override via literal value ─────────────────────────────────

    @Test fun `debug(literal) overrides for DEBUG`() {
        val fc = FieldConfig("F", String::class.java, constantProvider("default"))
        fc.debug("debugValue")
        assertEquals("debugValue", fc.resolve(BuildType.DEBUG)!!.get())
    }

    @Test fun `debug(literal) does not affect RELEASE`() {
        val fc = FieldConfig("F", String::class.java, constantProvider("default"))
        fc.debug("debugValue")
        assertEquals("default", fc.resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `release(literal) overrides for RELEASE`() {
        val fc = FieldConfig("F", String::class.java, constantProvider("default"))
        fc.release("releaseValue")
        assertEquals("releaseValue", fc.resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `release(literal) does not affect DEBUG`() {
        val fc = FieldConfig("F", String::class.java, constantProvider("default"))
        fc.release("releaseValue")
        assertEquals("default", fc.resolve(BuildType.DEBUG)!!.get())
    }

    @Test fun `both debug and release overrides are stored independently`() {
        val fc = FieldConfig("F", String::class.java, constantProvider("default"))
        fc.debug("d")
        fc.release("r")
        assertEquals("d", fc.resolve(BuildType.DEBUG)!!.get())
        assertEquals("r", fc.resolve(BuildType.RELEASE)!!.get())
    }

    // ── BuildType override via Provider ──────────────────────────────────────

    @Test fun `debug(provider) stores provider for DEBUG`() {
        val fc = FieldConfig("F", String::class.java, constantProvider("default"))
        fc.debug(constantProvider("fromProvider"))
        assertEquals("fromProvider", fc.resolve(BuildType.DEBUG)!!.get())
    }

    @Test fun `release(provider) stores provider for RELEASE`() {
        val fc = FieldConfig("F", String::class.java, constantProvider("default"))
        fc.release(constantProvider("releaseProvider"))
        assertEquals("releaseProvider", fc.resolve(BuildType.RELEASE)!!.get())
    }

    // ── Override replaces previous override ─────────────────────────────────

    @Test fun `second debug call overwrites first`() {
        val fc = FieldConfig("F", String::class.java, constantProvider("default"))
        fc.debug("first")
        fc.debug("second")
        assertEquals("second", fc.resolve(BuildType.DEBUG)!!.get())
    }

    @Test fun `second release call overwrites first`() {
        val fc = FieldConfig("F", String::class.java, constantProvider("default"))
        fc.release("first")
        fc.release("second")
        assertEquals("second", fc.resolve(BuildType.RELEASE)!!.get())
    }

    // ── Int / Boolean / Long field types ────────────────────────────────────

    @Test fun `Int field resolves default correctly`() {
        val fc = FieldConfig("TIMEOUT", Int::class.javaObjectType, constantProvider(30))
        assertEquals(30, fc.resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `Int field debug override resolves correctly`() {
        val fc = FieldConfig("TIMEOUT", Int::class.javaObjectType, constantProvider(30))
        fc.debug(5)
        assertEquals(5,  fc.resolve(BuildType.DEBUG)!!.get())
        assertEquals(30, fc.resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `Boolean field default resolves correctly`() {
        val fc = FieldConfig("FLAG", Boolean::class.javaObjectType, constantProvider(true))
        assertEquals(true,  fc.resolve(BuildType.DEBUG)!!.get())
        assertEquals(true,  fc.resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `Boolean field release override resolves correctly`() {
        val fc = FieldConfig("FLAG", Boolean::class.javaObjectType, constantProvider(true))
        fc.release(false)
        assertEquals(true,  fc.resolve(BuildType.DEBUG)!!.get())
        assertEquals(false, fc.resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `Long field default resolves correctly`() {
        val fc = FieldConfig("BIG", Long::class.javaObjectType, constantProvider(9_999_999_999L))
        assertEquals(9_999_999_999L, fc.resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `Double field default resolves correctly`() {
        val fc = FieldConfig("PI", Double::class.javaObjectType, constantProvider(3.14))
        assertEquals(3.14, fc.resolve(BuildType.DEBUG)!!.get())
    }

    @Test fun `Float field default resolves correctly`() {
        val fc = FieldConfig("F", Float::class.javaObjectType, constantProvider(1.5f))
        assertEquals(1.5f, fc.resolve(BuildType.DEBUG)!!.get())
    }

    // ── Override map is initially empty ─────────────────────────────────────

    @Test fun `buildTypeOverrides is empty on fresh FieldConfig`() {
        val fc = FieldConfig("F", String::class.java, null)
        assertEquals(0, fc.buildTypeOverrides.size)
    }

    @Test fun `buildTypeOverrides grows as overrides are set`() {
        val fc = FieldConfig("F", String::class.java, constantProvider("x"))
        fc.debug("d")
        assertEquals(1, fc.buildTypeOverrides.size)
        fc.release("r")
        assertEquals(2, fc.buildTypeOverrides.size)
    }
}
