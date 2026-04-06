package com.bitsycore.konfig

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Functional tests for the `dimension()` DSL:
 * explicit property selection, objectNameOverride, defaultTo fallback,
 * silent omission when no variant is resolved, variant field overrides,
 * VARIANT const, missing fields, and multiple dimensions.
 */
class DimensionsFunctionalTest : FunctionalTestBase() {

    // ── Explicit property selection ───────────────────────────────────────────

    @Test fun `explicit property selects correct variant`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("SERVER_URL", "https://prod.example.com") }
                    variant("dev")  { field("SERVER_URL", "https://dev.example.com") }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=dev"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("object Env /*env*/"))
        assertTrue(text.contains("""const val VARIANT: String = "dev""""))
        assertTrue(text.contains("""const val SERVER_URL: String = "https://dev.example.com""""))
    }

    @Test fun `selecting prod variant generates prod fields`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("SERVER_URL", "https://prod.example.com") }
                    variant("dev")  { field("SERVER_URL", "https://dev.example.com") }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("""const val VARIANT: String = "prod""""))
        assertTrue(text.contains("""const val SERVER_URL: String = "https://prod.example.com""""))
        assertFalse(text.contains("dev.example.com"))
    }

    // ── objectNameOverride ────────────────────────────────────────────────────

    @Test fun `objectNameOverride is used instead of derived name`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env", objectNameOverride = "Environment") {
                    variant("prod") { field("X", "y") }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("object Environment /*env*/"))
        assertFalse(text.contains("object Env /*"))
    }

    // ── defaultTo fallback ────────────────────────────────────────────────────

    @Test fun `defaultTo is used when no property is set`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("region", defaultTo = "eu") {
                    variant("eu") { field("BASE_URL", "https://eu.example.com") }
                    variant("us") { field("BASE_URL", "https://us.example.com") }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("object Region /*region*/"))
        assertTrue(text.contains("""const val VARIANT: String = "eu""""))
        assertTrue(text.contains("""const val BASE_URL: String = "https://eu.example.com""""))
    }

    @Test fun `explicit property overrides defaultTo`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("region", defaultTo = "eu") {
                    variant("eu") { field("BASE_URL", "https://eu.example.com") }
                    variant("us") { field("BASE_URL", "https://us.example.com") }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.region=us"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("""const val VARIANT: String = "us""""))
        assertFalse(text.contains("eu.example.com"))
    }

    // ── Silent omission ───────────────────────────────────────────────────────

    @Test fun `dimension without resolved variant is omitted silently`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("X", "y") }
                }
                field("ALWAYS", "here")
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val text = dir.generatedFile().readText()
        assertFalse(text.contains("object Env"))
        assertTrue(text.contains("""const val ALWAYS: String = "here""""))
    }

    // ── Variant field debug/release overrides ─────────────────────────────────

    @Test fun `variant field debug override applied for DEBUG build`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("dev") {
                        field("URL", "https://dev.example.com").debug("https://dev-debug.example.com")
                    }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG", "-Pkonfig.dimension.env=dev"))
        assertTrue(dir.generatedFile().readText()
            .contains("""const val URL: String = "https://dev-debug.example.com""""))
    }

    @Test fun `variant field release override applied for RELEASE build`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") {
                        field("URL", "https://default.example.com").release("https://prod.example.com")
                    }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))
        assertTrue(dir.generatedFile().readText()
            .contains("""const val URL: String = "https://prod.example.com""""))
    }

    // ── Missing fields ─────────────────────────────────────────────────────────

    @Test fun `field defined in variant A does not appear when variant B is active`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("a") { field("ONLY_IN_A", true) }
                    variant("b") { /* no ONLY_IN_A */ }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=b"))
        assertFalse(dir.generatedFile().readText().contains("ONLY_IN_A"))
    }

    // ── Multiple dimensions ────────────────────────────────────────────────────

    @Test fun `two dimensions both appear in output when both resolved`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("SERVER", "https://prod.example.com") }
                }
                dimension("region") {
                    variant("eu") { field("CDN", "https://eu-cdn.example.com") }
                }
            }
        """)
        run(listOf(
            "generateKonfig",
            "-Pkonfig.buildtype=RELEASE",
            "-Pkonfig.dimension.env=prod",
            "-Pkonfig.dimension.region=eu"
        ))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("object Env /*env*/"))
        assertTrue(text.contains("object Region /*region*/"))
        assertTrue(text.contains("""const val SERVER: String = "https://prod.example.com""""))
        assertTrue(text.contains("""const val CDN: String = "https://eu-cdn.example.com""""))
    }

    @Test fun `one dimension resolved and one skipped coexist correctly`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env", defaultTo = "prod") {
                    variant("prod") { field("SERVER", "https://prod.example.com") }
                }
                dimension("region") {   // no defaultTo, no property
                    variant("eu") { field("CDN", "https://eu-cdn.example.com") }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("object Env /*env*/"))
        assertFalse(text.contains("object Region"))
    }

    // ── CamelCase objectName derivation ──────────────────────────────────────

    @Test fun `hyphenated dimension name becomes CamelCase object name`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("build-env", defaultTo = "prod") {
                    variant("prod") { field("X", "y") }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("object BuildEnv /*build-env*/"))
    }

    @Test fun `underscore dimension name becomes CamelCase object name`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("my_region", defaultTo = "eu") {
                    variant("eu") { field("X", "y") }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("object MyRegion /*my_region*/"))
    }

    // ── VARIANT const is always present ──────────────────────────────────────

    @Test fun `VARIANT const is always present in dimension object`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env", defaultTo = "staging") {
                    variant("staging") { /* no fields */ }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("""const val VARIANT: String = "staging""""))
    }
}
