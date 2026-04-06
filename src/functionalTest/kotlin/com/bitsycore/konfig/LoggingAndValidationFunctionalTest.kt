package com.bitsycore.konfig

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Functional tests for lifecycle logging and build-time validation errors:
 * BUILD_TYPE source message, dimension resolution log, skipped-dimension log,
 * unknown variant warning, invalid defaultTo error, invalid field names,
 * and generation summary.
 */
class LoggingAndValidationFunctionalTest : FunctionalTestBase() {

    // ── BUILD_TYPE lifecycle log ──────────────────────────────────────────────

    @Test fun `build type source is logged at lifecycle level`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {}
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG"))
        assertTrue(result.output.contains("BUILD_TYPE = debug"))
        assertTrue(result.output.contains("explicit property -Pkonfig.buildtype=DEBUG"))
    }

    @Test fun `build type defaults to release and logs reason`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {}
        """)
        // No -Pkonfig.buildtype → should fall back to RELEASE
        val result = run(listOf("generateKonfig"))
        assertTrue(result.output.contains("BUILD_TYPE = release"))
    }

    // ── Dimension resolution log ──────────────────────────────────────────────

    @Test fun `active dimension variant and reason are logged`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("X", "y") }
                    variant("dev")  { field("X", "z") }
                }
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=dev"))
        assertTrue(result.output.contains("dim 'env'"))
        assertTrue(result.output.contains("'dev'"))
        assertTrue(result.output.contains("konfig.dimension.env=dev"))
    }

    // ── Skipped dimension log ─────────────────────────────────────────────────

    @Test fun `skipped dimension is logged at lifecycle level`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("X", "y") }
                }
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("dim 'env'"))
        assertTrue(result.output.contains("skipped"))
    }

    // ── Unknown variant warning ───────────────────────────────────────────────

    @Test fun `unknown variant in property emits a warning`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("X", "y") }
                }
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=typo"))
        assertTrue(result.output.contains("typo"))
        assertTrue(result.output.contains("not a known variant"))
    }

    // ── Generation summary ────────────────────────────────────────────────────

    @Test fun `generation summary is logged with field and dimension counts`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env", defaultTo = "prod") {
                    variant("prod") { field("URL", "https://prod.example.com") }
                }
                field("API_KEY", "abc123")
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("generated BuildKonfig.kt"))
        assertTrue(result.output.contains("1 global field"))
        assertTrue(result.output.contains("1 dimension"))
    }

    @Test fun `generation summary says no global fields when none declared`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {}
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("no global fields"))
    }

    @Test fun `generation summary says no dimensions when none declared`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("K", "v") }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("no dimensions"))
    }

    @Test fun `generation summary counts multiple global fields correctly`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("A", "1")
                field("B", "2")
                field("C", "3")
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("3 global field"))
    }

    // ── Validation errors ─────────────────────────────────────────────────────

    @Test fun `invalid global field name fails build`() = withFailingProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("INVALID NAME", "oops")
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("INVALID NAME") || result.output.contains("not a valid Kotlin identifier"))
    }

    @Test fun `invalid field name starting with digit fails build`() = withFailingProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("1INVALID", "oops")
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("1INVALID") || result.output.contains("not a valid Kotlin identifier"))
    }

    @Test fun `invalid field name in dimension variant fails build`() = withFailingProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") {
                        field("INVALID NAME", true)
                    }
                }
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))
        assertTrue(result.output.contains("INVALID NAME") || result.output.contains("not a valid Kotlin identifier"))
    }

    @Test fun `invalid defaultTo variant fails build with error message`() = withFailingProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env", defaultTo = "nonexistent") {
                    variant("prod") { field("X", "y") }
                }
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("nonexistent"))
    }

    @Test fun `invalid objectName fails build`() = withFailingProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                objectName = "123Invalid"
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("123Invalid") || result.output.contains("not a valid Kotlin identifier"))
    }
}
