package com.bitsycore.konfig

import org.gradle.testkit.runner.TaskOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Functional tests for Gradle task caching and incremental build behaviour:
 * UP-TO-DATE on unchanged inputs, re-execution on changed inputs,
 * and the `-Pkonfig.force` bypass.
 */
class CachingFunctionalTest : FunctionalTestBase() {

    // ── UP-TO-DATE on unchanged inputs ────────────────────────────────────────

    @Test fun `task is UP-TO-DATE on second run with unchanged inputs`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("FOO", "bar") }
        """)
        val args = listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE")
        run(args)
        assertEquals(TaskOutcome.UP_TO_DATE, run(args).task(":generateKonfig")?.outcome)
    }

    @Test fun `task is UP-TO-DATE for empty konfig block`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {}
        """)
        val args = listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE")
        run(args)
        assertEquals(TaskOutcome.UP_TO_DATE, run(args).task(":generateKonfig")?.outcome)
    }

    // ── Re-executes when inputs change ────────────────────────────────────────

    @Test fun `task re-runs after build type changes`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {}
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        val second = run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG"))
        assertNotEquals(TaskOutcome.UP_TO_DATE, second.task(":generateKonfig")?.outcome)
    }

    @Test fun `task re-runs after field value changes`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("FOO", "first") }
        """)
        val args = listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE")
        run(args)

        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("FOO", "second") }
        """)
        assertNotEquals(TaskOutcome.UP_TO_DATE, run(args).task(":generateKonfig")?.outcome)
    }

    @Test fun `task re-runs after dimension variant property changes`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("URL", "https://prod.example.com") }
                    variant("dev")  { field("URL", "https://dev.example.com") }
                }
            }
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))
        val second = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=dev"))
        assertNotEquals(TaskOutcome.UP_TO_DATE, second.task(":generateKonfig")?.outcome)
    }

    @Test fun `task re-runs after konfig_properties file changes`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("URL", "https://prod.example.com") }
                    variant("dev")  { field("URL", "https://dev.example.com") }
                }
            }
        """)
        val propsFile = dir.resolve("konfig.properties")
        propsFile.writeText("konfig.dimension.env=dev\n")
        val args = listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE")
        run(args)

        propsFile.writeText("konfig.dimension.env=prod\n")
        assertNotEquals(TaskOutcome.UP_TO_DATE, run(args).task(":generateKonfig")?.outcome)
    }

    // ── -Pkonfig.force ────────────────────────────────────────────────────────

    @Test fun `konfig force prevents UP-TO-DATE on second run`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
        """)
        val args = listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.force")
        run(args)
        assertNotEquals(TaskOutcome.UP_TO_DATE, run(args).task(":generateKonfig")?.outcome)
    }

    @Test fun `without konfig force task stays UP-TO-DATE`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
        """)
        val args = listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE")
        run(args)
        assertEquals(TaskOutcome.UP_TO_DATE, run(args).task(":generateKonfig")?.outcome)
    }

    @Test fun `removing konfig force restores UP-TO-DATE behaviour`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
        """)
        // Run once without force to prime the cache
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        // Second run also without force → UP-TO-DATE
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE")).task(":generateKonfig")?.outcome
        )
    }

    // ── Generated output survives subsequent UP-TO-DATE runs ─────────────────

    @Test fun `generated file content is unchanged after UP-TO-DATE run`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("STABLE", "value") }
        """)
        val args = listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE")
        run(args)
        val textAfterFirst = dir.generatedFile().readText()
        run(args) // UP-TO-DATE
        assertEquals(textAfterFirst, dir.generatedFile().readText())
        assertTrue(textAfterFirst.contains("""const val STABLE: String = "value""""))
    }
}
