package com.bitsycore.konfig

import com.bitsycore.konfig.configs.BuildTypedFieldDeclScope
import com.bitsycore.konfig.configs.VariantConfig
import com.bitsycore.konfig.configs.constantProvider
import com.bitsycore.konfig.types.BuildType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [BuildTypedFieldDeclScope].
 *
 * This scope is the receiver inside `debug { }` / `release { }` blocks on a
 * [VariantConfig].  It pins the [BuildType] and delegates to
 * [VariantConfig.getOrCreateField] so overrides land on the correct entry.
 */
class BuildTypedFieldDeclScopeTest {

    private fun makeScope(buildType: BuildType, variantName: String = "v"): Pair<BuildTypedFieldDeclScope, VariantConfig> {
        val owner = VariantConfig(variantName)
        val scope = BuildTypedFieldDeclScope(buildType, owner)
        return scope to owner
    }

    // ── buildType is stored ──────────────────────────────────────────────────

    @Test fun `DEBUG scope stores DEBUG build type`() {
        val (scope, _) = makeScope(BuildType.DEBUG)
        assertEquals(BuildType.DEBUG, scope.buildType)
    }

    @Test fun `RELEASE scope stores RELEASE build type`() {
        val (scope, _) = makeScope(BuildType.RELEASE)
        assertEquals(BuildType.RELEASE, scope.buildType)
    }

    // ── owner is stored ──────────────────────────────────────────────────────

    @Test fun `owner is the supplied VariantConfig`() {
        val owner = VariantConfig("myVariant")
        val scope = BuildTypedFieldDeclScope(BuildType.DEBUG, owner)
        assertEquals(owner, scope.owner)
    }

    // ── field(name, literal) in DEBUG scope ──────────────────────────────────

    @Test fun `field in DEBUG scope creates override for DEBUG`() {
        val (scope, owner) = makeScope(BuildType.DEBUG)
        scope.field("LOG", "verbose")
        val fc = owner.fields.first { it.fieldName == "LOG" }
        assertEquals("verbose", fc.resolve(BuildType.DEBUG)!!.get())
    }

    @Test fun `field in DEBUG scope does not set RELEASE override`() {
        val (scope, owner) = makeScope(BuildType.DEBUG)
        scope.field("LOG", "verbose")
        val fc = owner.fields.first { it.fieldName == "LOG" }
        assertNull(fc.resolve(BuildType.RELEASE))
    }

    // ── field(name, literal) in RELEASE scope ────────────────────────────────

    @Test fun `field in RELEASE scope creates override for RELEASE`() {
        val (scope, owner) = makeScope(BuildType.RELEASE)
        scope.field("LOG", "error")
        val fc = owner.fields.first { it.fieldName == "LOG" }
        assertEquals("error", fc.resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `field in RELEASE scope does not set DEBUG override`() {
        val (scope, owner) = makeScope(BuildType.RELEASE)
        scope.field("LOG", "error")
        val fc = owner.fields.first { it.fieldName == "LOG" }
        assertNull(fc.resolve(BuildType.DEBUG))
    }

    // ── field(name, provider) ────────────────────────────────────────────────

    @Test fun `field with provider in DEBUG scope stores provider`() {
        val (scope, owner) = makeScope(BuildType.DEBUG)
        scope.field("KEY", constantProvider("devKey"))
        val fc = owner.fields.first { it.fieldName == "KEY" }
        assertEquals("devKey", fc.resolve(BuildType.DEBUG)!!.get())
    }

    @Test fun `field with provider in RELEASE scope stores provider`() {
        val (scope, owner) = makeScope(BuildType.RELEASE)
        scope.field("KEY", constantProvider("prodKey"))
        val fc = owner.fields.first { it.fieldName == "KEY" }
        assertEquals("prodKey", fc.resolve(BuildType.RELEASE)!!.get())
    }

    // ── getOrCreateField reuse ────────────────────────────────────────────────

    @Test fun `same field name in debug and release scopes shares one FieldConfig`() {
        val owner = VariantConfig("v")
        val dbgScope = BuildTypedFieldDeclScope(BuildType.DEBUG, owner)
        val relScope = BuildTypedFieldDeclScope(BuildType.RELEASE, owner)
        dbgScope.field("LOG", "verbose")
        relScope.field("LOG", "error")
        // Only one FieldConfig should exist
        assertEquals(1, owner.fields.size)
        val fc = owner.fields[0]
        assertEquals("verbose", fc.resolve(BuildType.DEBUG)!!.get())
        assertEquals("error",   fc.resolve(BuildType.RELEASE)!!.get())
    }

    // ── Multiple fields in one scope block ───────────────────────────────────

    @Test fun `multiple fields in debug scope are all created`() {
        val (scope, owner) = makeScope(BuildType.DEBUG)
        scope.field("A", "alpha")
        scope.field("B", 42)
        scope.field("C", true)
        assertEquals(3, owner.fields.size)
    }

    @Test fun `multiple fields in release scope are independent`() {
        val (scope, owner) = makeScope(BuildType.RELEASE)
        scope.field("X", 100)
        scope.field("Y", 200)
        assertEquals(100, owner.fields.first { it.fieldName == "X" }.resolve(BuildType.RELEASE)!!.get())
        assertEquals(200, owner.fields.first { it.fieldName == "Y" }.resolve(BuildType.RELEASE)!!.get())
    }

    // ── Int / Boolean / Long scoped fields ───────────────────────────────────

    @Test fun `Int field in debug scope resolves correctly`() {
        val (scope, owner) = makeScope(BuildType.DEBUG)
        scope.field("TIMEOUT", 5)
        assertEquals(5, owner.fields[0].resolve(BuildType.DEBUG)!!.get())
    }

    @Test fun `Boolean field in release scope resolves correctly`() {
        val (scope, owner) = makeScope(BuildType.RELEASE)
        scope.field("FLAG", false)
        assertEquals(false, owner.fields[0].resolve(BuildType.RELEASE)!!.get())
    }

    @Test fun `Long field in debug scope resolves correctly`() {
        val (scope, owner) = makeScope(BuildType.DEBUG)
        scope.field("BIG", 123_456_789_000L)
        assertEquals(123_456_789_000L, owner.fields[0].resolve(BuildType.DEBUG)!!.get())
    }

    // ── Scope used via VariantConfig.debug{} / release{} ─────────────────────

    @Test fun `VariantConfig debug block reaches correct scope`() {
        val v = VariantConfig("test")
        v.debug { field("M", "debugMsg") }
        assertEquals("debugMsg", v.fields.first { it.fieldName == "M" }.resolve(BuildType.DEBUG)!!.get())
    }

    @Test fun `VariantConfig release block reaches correct scope`() {
        val v = VariantConfig("test")
        v.release { field("M", "releaseMsg") }
        assertEquals("releaseMsg", v.fields.first { it.fieldName == "M" }.resolve(BuildType.RELEASE)!!.get())
    }
}
