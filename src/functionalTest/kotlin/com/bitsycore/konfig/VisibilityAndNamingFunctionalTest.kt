package com.bitsycore.konfig

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Functional tests for object visibility and custom naming:
 * [objectVisibility] (PUBLIC / INTERNAL), [objectName], [objectPackage],
 * and the interaction between visibility and nested dimension objects.
 */
class VisibilityAndNamingFunctionalTest : FunctionalTestBase() {

    // ── Default visibility (PUBLIC) ───────────────────────────────────────────

    @Test fun `default visibility emits public object`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {}
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("public object BuildKonfig {"))
    }

    // ── INTERNAL visibility ───────────────────────────────────────────────────

    @Test fun `INTERNAL visibility prefixes top-level object`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                objectVisibility = com.bitsycore.konfig.types.Visibility.INTERNAL
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("internal object BuildKonfig {"))
    }

    @Test fun `INTERNAL visibility also prefixes nested dimension object`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                objectVisibility = com.bitsycore.konfig.types.Visibility.INTERNAL
                dimension("env", defaultTo = "prod") {
                    variant("prod") { field("X", "y") }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("internal object Env"))
    }

    @Test fun `INTERNAL visibility does not appear twice for top-level object`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                objectVisibility = com.bitsycore.konfig.types.Visibility.INTERNAL
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val text = dir.generatedFile().readText()
        // "internal object BuildKonfig" must appear exactly once
        val count = text.split("internal object BuildKonfig").size - 1
        assertTrue(count == 1, "Expected exactly one 'internal object BuildKonfig', found $count")
    }

    // ── Custom objectName ─────────────────────────────────────────────────────

    @Test fun `custom objectName replaces BuildKonfig`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                objectName = "AppConfig"
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val text = dir.generatedFile().readText()
        assertTrue(text.contains("object AppConfig {"))
        assertFalse(text.contains("object BuildKonfig"))
    }

    @Test fun `custom objectName is reflected in generated file name`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                objectName = "Cfg"
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val file = dir.generatedFile()
        assertTrue(file.name == "Cfg.kt", "Expected file name 'Cfg.kt', got '${file.name}'")
    }

    @Test fun `custom objectName combined with INTERNAL visibility`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                objectName = "MyConf"
                objectVisibility = com.bitsycore.konfig.types.Visibility.INTERNAL
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("internal object MyConf {"))
    }

    // ── Custom objectPackage ──────────────────────────────────────────────────

    @Test fun `custom objectPackage overrides derived package`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                objectPackage = "org.myapp.generated"
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("package org.myapp.generated"))
    }

    @Test fun `custom objectPackage is reflected in output directory path`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                objectPackage = "io.test.pkg"
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val file = dir.generatedFile()
        // The file's parent path should reflect the package segments
        assertTrue(file.path.contains("io") && file.path.contains("test") && file.path.contains("pkg"),
            "Expected package path in '${file.path}'")
    }

    // ── generation summary log ────────────────────────────────────────────────

    @Test fun `generation summary references correct object name`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                objectName = "MyConfig"
            }
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(result.output.contains("MyConfig.kt"))
    }
}
