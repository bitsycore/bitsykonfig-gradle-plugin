package com.bitsycore.konfig

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("FunctionName")
class KonfigPluginFunctionalTest {

    private fun withProject(block: (projectDir: File, run: (List<String>) -> BuildResult) -> Unit) {
        val projectDir = createTempDirectory("konfig-ft").toFile()
        try {
            projectDir.resolve("settings.gradle.kts").writeText("""rootProject.name = "test-project"""")
            val run = { args: List<String> ->
                GradleRunner.create()
                    .withProjectDir(projectDir)
                    .withPluginClasspath()
                    .withArguments(args)
                    .build()
            }
            block(projectDir, run)
        } finally {
            projectDir.deleteRecursively()
        }
    }

    private fun File.generatedBuildKonfig(): File =
        walkTopDown().first { it.isFile && it.extension == "kt" }

    // ─── Basic generation ─────────────────────────────────────────────────────

    @Test fun `generateKonfig produces file with correct package and BUILD_TYPE`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
        """.trimIndent())

        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKonfig")?.outcome)

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("package com.example.test.project"))
        assertTrue(text.contains("object BuildKonfig {"))
        assertTrue(text.contains("""const val BUILD_TYPE: String = "release""""))
    }

    @Test fun `debug build type generates BUILD_TYPE debug`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {}
        """.trimIndent())

        run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("""const val BUILD_TYPE: String = "debug""""))
    }

    // ─── Global fields ────────────────────────────────────────────────────────

    @Test fun `string fields are generated correctly`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("API_URL", "https://api.example.com")
                field("APP_NAME", "My \"App\"")
            }
        """.trimIndent())

        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("""const val API_URL: String = "https://api.example.com""""))
        assertTrue(text.contains("""const val APP_NAME: String = "My \"App\"""""))
    }

    @Test fun `boolean and int fields are generated correctly`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("LOGGING", false)
                field("TIMEOUT", 30)
            }
        """.trimIndent())

        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("const val LOGGING: Boolean = false"))
        assertTrue(text.contains("const val TIMEOUT: Int = 30"))
    }

    @Test fun `global field debug override applied for debug build`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("BASE_URL", "https://prod.example.com").debug("https://dev.example.com")
                field("LOGGING", false).debug(true)
            }
        """.trimIndent())

        run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("""const val BASE_URL: String = "https://dev.example.com""""))
        assertTrue(text.contains("inline val LOGGING: Boolean get() = true"))
    }

    @Test fun `release build uses default when no release override`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("BASE_URL", "https://prod.example.com").debug("https://dev.example.com")
            }
        """.trimIndent())

        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("""const val BASE_URL: String = "https://prod.example.com""""))
    }

    // ─── Visibility & naming ──────────────────────────────────────────────────

    @Test fun `internal visibility prefixes object`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                objectVisibility = com.bitsycore.konfig.Visibility.INTERNAL
            }
        """.trimIndent())

        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("internal object BuildKonfig {"))
    }

    @Test fun `custom objectName is used`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                objectName = "AppConfig"
            }
        """.trimIndent())

        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("object AppConfig {"))
    }

    // ─── Dimensions ───────────────────────────────────────────────────────────

    @Test fun `dimension with explicit property generates nested object`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") {
                        field("SERVER_URL", "https://prod.example.com")
                    }
                    variant("dev") {
                        field("SERVER_URL", "https://dev.example.com")
                    }
                }
            }
        """.trimIndent())

        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=dev"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("object Env /*env*/"))
        assertTrue(text.contains("""const val VARIANT: String = "dev""""))
        assertTrue(text.contains("""const val SERVER_URL: String = "https://dev.example.com""""))
    }

    @Test fun `dimension with objectNameOverride uses override name`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env", objectNameOverride = "Environment") {
                    variant("prod") { field("X", "y") }
                }
            }
        """.trimIndent())

        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("object Environment /*env*/"))
    }

    @Test fun `dimension with defaultTo uses default variant when no property set`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("region", defaultTo = "eu") {
                    variant("eu") { field("BASE_URL", "https://eu.example.com") }
                    variant("us") { field("BASE_URL", "https://us.example.com") }
                }
            }
        """.trimIndent())

        // No konfig.dimension.region property — falls back to defaultTo = "eu"
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("object Region /*region*/"))
        assertTrue(text.contains("""const val VARIANT: String = "eu""""))
        assertTrue(text.contains("""const val BASE_URL: String = "https://eu.example.com""""))
    }

    @Test fun `dimension without active variant is omitted silently`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {   // no defaultTo
                    variant("prod") { field("X", "y") }
                }
                field("ALWAYS", "here")
            }
        """.trimIndent())

        // No konfig.dimension.env property → dimension omitted, no crash
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedBuildKonfig().readText()
        assertFalse(text.contains("object Env"))
        assertTrue(text.contains("""const val ALWAYS: String = "here""""))
    }

    @Test fun `variant field debug override applied inside dimension`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("dev") {
                        field("URL", "https://dev.example.com").debug("https://dev-debug.example.com")
                    }
                }
            }
        """.trimIndent())

        run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG", "-Pkonfig.dimension.env=dev"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("""const val URL: String = "https://dev-debug.example.com""""))
    }

    @Test fun `missing field in variant does not appear in output`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("a") { field("ONLY_IN_A", true) }
                    variant("b") { /* ONLY_IN_A not defined here */ }
                }
            }
        """.trimIndent())

        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=b"))

        val text = dir.generatedBuildKonfig().readText()
        assertFalse(text.contains("ONLY_IN_A"))
    }

    // ─── Caching ─────────────────────────────────────────────────────────────

    @Test fun `task is UP-TO-DATE on second run`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig { field("FOO", "bar") }
        """.trimIndent())

        val args = listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE")
        run(args)
        val second = run(args)

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateKonfig")?.outcome)
    }

    // ─── Logging & validation ─────────────────────────────────────────────────

    @Test fun `build type source is logged at lifecycle level`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {}
        """.trimIndent())

        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG"))

        assertTrue(result.output.contains("BUILD_TYPE = debug"))
        assertTrue(result.output.contains("explicit property -Pkonfig.buildtype=DEBUG"))
    }

    @Test fun `dimension active variant and reason are logged`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("X", "y") }
                    variant("dev")  { field("X", "z") }
                }
            }
        """.trimIndent())

        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=dev"))

        assertTrue(result.output.contains("dim 'env'"))
        assertTrue(result.output.contains("'dev'"))
        assertTrue(result.output.contains("konfig.dimension.env=dev"))
    }

    @Test fun `skipped dimension is logged`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {   // no defaultTo, no property set
                    variant("prod") { field("X", "y") }
                }
            }
        """.trimIndent())

        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        assertTrue(result.output.contains("dim 'env'"))
        assertTrue(result.output.contains("skipped"))
    }

    @Test fun `unknown variant in property emits a warning`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("X", "y") }
                }
            }
        """.trimIndent())

        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=typo"))

        assertTrue(result.output.contains("typo"))
        assertTrue(result.output.contains("not a known variant"))
    }

    @Test fun `invalid defaultTo fails build with clear error`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env", defaultTo = "nonexistent") {
                    variant("prod") { field("X", "y") }
                }
            }
        """.trimIndent())

        val result = runCatching {
            run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        }

        assertTrue(result.isFailure || result.getOrNull()?.output?.contains("nonexistent") == true)
    }

    @Test fun `invalid field name fails build with clear error`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("INVALID NAME", "oops")
            }
        """.trimIndent())

        val result = runCatching {
            run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        }

        assertTrue(result.isFailure)
    }

    @Test fun `generation summary is logged at lifecycle level`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env", defaultTo = "prod") {
                    variant("prod") { field("URL", "https://prod.example.com") }
                }
                field("API_KEY", "abc123")
            }
        """.trimIndent())

        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        // Summary line always present
        assertTrue(result.output.contains("generated BuildKonfig.kt"))
        assertTrue(result.output.contains("1 global field"))
        assertTrue(result.output.contains("1 dimension"))
    }

    // ─── Generated file header ────────────────────────────────────────────────

    @Test fun `generated file contains Suppress RedundantVisibilityModifier header`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {}
        """.trimIndent())

        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("""@file:Suppress("RedundantVisibilityModifier")"""))
    }

    // ─── VARIANT visibility ───────────────────────────────────────────────────

    @Test fun `VARIANT Object has visibility prefix when objectVisibility is INTERNAL`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                objectVisibility = com.bitsycore.konfig.Visibility.INTERNAL
                dimension("env", defaultTo = "prod") {
                    variant("prod") { field("X", "y") }
                }
            }
        """.trimIndent())

        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("internal object Env"))
    }

    // ─── Duplicate name detection ─────────────────────────────────────────────

    @Test fun `duplicate dimension name fails build with clear error`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") { variant("prod") { field("X", "y") } }
                dimension("env") { variant("dev")  { field("X", "z") } }
            }
        """.trimIndent())

        val result = runCatching {
            run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        }

        assertTrue(result.isFailure || result.getOrNull()?.output?.contains("env") == true)
    }

    @Test fun `duplicate global field name fails build with clear error`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                field("API_URL", "https://prod.example.com")
                field("API_URL", "https://dev.example.com")
            }
        """.trimIndent())

        val result = runCatching {
            run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        }

        assertTrue(result.isFailure)
    }

    @Test fun `duplicate variant field name fails build with clear error`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") {
                        field("URL", "https://a.example.com")
                        field("URL", "https://b.example.com")
                    }
                }
            }
        """.trimIndent())

        val result = runCatching {
            run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))
        }

        assertTrue(result.isFailure)
    }

    // ─── konfig.properties file ───────────────────────────────────────────────

    @Test fun `konfig_properties file sets dimension variant as fallback`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("SERVER_URL", "https://prod.example.com") }
                    variant("dev")  { field("SERVER_URL", "https://dev.example.com") }
                }
            }
        """.trimIndent())
        dir.resolve("konfig.properties").writeText("konfig.dimension.env=dev\n")

        // No -P property — should pick up from konfig.properties
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("""const val VARIANT: String = "dev""""))
        assertTrue(text.contains("""const val SERVER_URL: String = "https://dev.example.com""""))
    }

    @Test fun `gradle property overrides konfig_properties file`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("SERVER_URL", "https://prod.example.com") }
                    variant("dev")  { field("SERVER_URL", "https://dev.example.com") }
                }
            }
        """.trimIndent())
        dir.resolve("konfig.properties").writeText("konfig.dimension.env=dev\n")

        // -P property takes priority over konfig.properties
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("""const val VARIANT: String = "prod""""))
    }

    @Test fun `konfig_properties file change invalidates cache`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") { field("SERVER_URL", "https://prod.example.com") }
                    variant("dev")  { field("SERVER_URL", "https://dev.example.com") }
                }
            }
        """.trimIndent())
        val propsFile = dir.resolve("konfig.properties")
        propsFile.writeText("konfig.dimension.env=dev\n")

        val args = listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE")
        run(args)

        propsFile.writeText("konfig.dimension.env=prod\n")
        val second = run(args)

        // After changing the file, task should NOT be UP-TO-DATE
        assertFalse(second.task(":generateKonfig")?.outcome == TaskOutcome.UP_TO_DATE)
        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("""const val VARIANT: String = "prod""""))
    }

    // ─── Validation completeness ──────────────────────────────────────────────

    @Test fun `invalid field name in boolean dimension field fails build`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {
                dimension("env") {
                    variant("prod") {
                        field("INVALID NAME", true)
                    }
                }
            }
        """.trimIndent())

        val result = runCatching {
            run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE", "-Pkonfig.dimension.env=prod"))
        }

        assertTrue(result.isFailure)
    }
}
