package com.bitsycore.konfig

import org.gradle.testkit.runner.TaskOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Functional tests for `konfig.properties` file support:
 * file-based variant selection, Gradle property priority override,
 * and cache invalidation when the file changes.
 */
class KonfigPropertiesFunctionalTest : FunctionalTestBase() {

    private fun buildScript() = """
        plugins { id("com.bitsycore.konfig") }
        group = "com.example"
        konfig {
            dimension("env") {
                variant("prod") { field("SERVER_URL", "https://prod.example.com") }
                variant("dev")  { field("SERVER_URL", "https://dev.example.com") }
                variant("staging") { field("SERVER_URL", "https://staging.example.com") }
            }
        }
    """

    // ── Basic file reading ────────────────────────────────────────────────────

    @Test fun `konfig_properties sets dimension variant`() = withProject { dir, run ->
        dir.writeBuildGradle(buildScript())
        dir.resolve("konfig.properties").writeText("konfig.dimension.env=dev\n")

        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedFile().readText()
        assertTrue(text.contains("""const val VARIANT: String = "dev""""))
        assertTrue(text.contains("""const val SERVER_URL: String = "https://dev.example.com""""))
    }

    @Test fun `konfig_properties sets staging variant`() = withProject { dir, run ->
        dir.writeBuildGradle(buildScript())
        dir.resolve("konfig.properties").writeText("konfig.dimension.env=staging\n")

        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        assertTrue(dir.generatedFile().readText()
            .contains("""const val VARIANT: String = "staging""""))
    }

    // ── Gradle property takes priority over file ──────────────────────────────

    @Test fun `Gradle property overrides konfig_properties`() = withProject { dir, run ->
        dir.writeBuildGradle(buildScript())
        dir.resolve("konfig.properties").writeText("konfig.dimension.env=dev\n")

        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))

        val text = dir.generatedFile().readText()
        assertTrue(text.contains("""const val VARIANT: String = "prod""""))
        assertTrue(text.contains("prod.example.com"))
    }

    // ── No konfig.properties → falls through to defaultTo or skipped ─────────

    @Test fun `without konfig_properties and no property dimension uses defaultTo`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env", defaultTo = "prod") {
                    variant("prod") { field("SERVER_URL", "https://prod.example.com") }
                    variant("dev")  { field("SERVER_URL", "https://dev.example.com") }
                }
            }
        """)
        // no konfig.properties, no -P property
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        assertTrue(dir.generatedFile().readText()
            .contains("""const val VARIANT: String = "prod""""))
    }

    // ── Cache invalidation ────────────────────────────────────────────────────

    @Test fun `task re-runs after konfig_properties file content changes`() = withProject { dir, run ->
        dir.writeBuildGradle(buildScript())
        val propsFile = dir.resolve("konfig.properties")
        propsFile.writeText("konfig.dimension.env=dev\n")

        val args = listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE")
        run(args)

        propsFile.writeText("konfig.dimension.env=prod\n")
        val second = run(args)

        assertNotEquals(TaskOutcome.UP_TO_DATE, second.task(":generateKonfig")?.outcome)
        assertTrue(dir.generatedFile().readText().contains("""const val VARIANT: String = "prod""""))
    }

    @Test fun `task is UP-TO-DATE when konfig_properties file is unchanged`() = withProject { dir, run ->
        dir.writeBuildGradle(buildScript())
        dir.resolve("konfig.properties").writeText("konfig.dimension.env=dev\n")

        val args = listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE")
        run(args)
        assertEquals(TaskOutcome.UP_TO_DATE, run(args).task(":generateKonfig")?.outcome)
    }

    @Test fun `adding konfig_properties after first run triggers re-execution`() = withProject { dir, run ->
        dir.writeBuildGradle(buildScript())
        // First run: no properties file, dimension skipped
        val args = listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE")
        run(args)

        // Now add the file
        dir.resolve("konfig.properties").writeText("konfig.dimension.env=prod\n")
        val second = run(args)

        assertNotEquals(TaskOutcome.UP_TO_DATE, second.task(":generateKonfig")?.outcome)
        assertTrue(dir.generatedFile().readText().contains("""const val VARIANT: String = "prod""""))
    }

    // ── Multiple dimensions in properties file ────────────────────────────────

    @Test fun `konfig_properties can set multiple dimensions`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("SERVER", "https://prod.example.com") }
                    variant("dev")  { field("SERVER", "https://dev.example.com") }
                }
                dimension("region") {
                    variant("eu") { field("CDN", "https://eu-cdn.example.com") }
                    variant("us") { field("CDN", "https://us-cdn.example.com") }
                }
            }
        """)
        dir.resolve("konfig.properties").writeText(
            "konfig.dimension.env=dev\nkonfig.dimension.region=us\n"
        )
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedFile().readText()
        assertTrue(text.contains("""const val SERVER: String = "https://dev.example.com""""))
        assertTrue(text.contains("""const val CDN: String = "https://us-cdn.example.com""""))
    }
}
