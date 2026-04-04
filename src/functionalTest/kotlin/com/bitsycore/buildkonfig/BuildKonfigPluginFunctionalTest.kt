package com.bitsycore.buildkonfig

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("FunctionName")
class BuildKonfigPluginFunctionalTest {

    private fun withProject(block: (projectDir: File, run: (List<String>) -> BuildResult) -> Unit) {
        val projectDir = createTempDirectory("buildkonfig-ft").toFile()
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
        walkTopDown().first { it.name == "BuildKonfig.kt" }

    @Test fun `generateBuildKonfig produces file with correct package and defaults`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.buildkonfig") }
            group = "com.example"
        """.trimIndent())

        val result = run(listOf("generateBuildKonfig", "-Pkonfig.buildtype=RELEASE"))

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateBuildKonfig")?.outcome)

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.startsWith("package com.example.test.project"))
        assertTrue(text.contains("object BuildKonfig {"))
        assertTrue(text.contains("""const val MODULE_NAME: String = "test-project""""))
        assertTrue(text.contains("const val IS_DEBUG: Boolean = false"))
        assertTrue(text.contains("""const val VARIANT: String = "RELEASE""""))
    }

    @Test fun `string fields are generated correctly`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.buildkonfig") }
            group = "com.example"
            buildKonfig {
                string("API_URL", "https://api.example.com")
                string("APP_NAME", "My \"App\"")
            }
        """.trimIndent())

        run(listOf("generateBuildKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("""const val API_URL: String = "https://api.example.com""""))
        assertTrue(text.contains("""const val APP_NAME: String = "My \"App\"""""))
    }

    @Test fun `boolean and int fields are generated correctly`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.buildkonfig") }
            group = "com.example"
            buildKonfig {
                boolean("LOGGING", false)
                integer("TIMEOUT", 30)
            }
        """.trimIndent())

        run(listOf("generateBuildKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("const val LOGGING: Boolean = false"))
        assertTrue(text.contains("const val TIMEOUT: Int = 30"))
    }

    @Test fun `debug build type sets IS_DEBUG as inline val`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.buildkonfig") }
            group = "com.example"
            buildKonfig {}
        """.trimIndent())

        run(listOf("generateBuildKonfig", "-Pkonfig.buildtype=DEBUG"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("inline val IS_DEBUG: Boolean get() = true"))
        assertTrue(text.contains("""const val VARIANT: String = "DEBUG""""))
    }

    @Test fun `variant-specific field overrides default for debug`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.buildkonfig") }
            group = "com.example"
            buildKonfig {
                string("BASE_URL", "https://prod.example.com") {
                    debug("https://dev.example.com")
                }
                boolean("LOGGING", false) {
                    debug(true)
                }
            }
        """.trimIndent())

        run(listOf("generateBuildKonfig", "-Pkonfig.buildtype=DEBUG"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("""const val BASE_URL: String = "https://dev.example.com""""))
        assertTrue(text.contains("const val LOGGING: Boolean = true"))
    }

    @Test fun `release build uses default when no release override`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.buildkonfig") }
            group = "com.example"
            buildKonfig {
                string("BASE_URL", "https://prod.example.com") {
                    debug("https://dev.example.com")
                }
            }
        """.trimIndent())

        run(listOf("generateBuildKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("""const val BASE_URL: String = "https://prod.example.com""""))
    }

    @Test fun `internal visibility prefixes object`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.buildkonfig") }
            group = "com.example"
            buildKonfig {
                objectVisibility.set(com.bitsycore.buildkonfig.Visibility.INTERNAL)
            }
        """.trimIndent())

        run(listOf("generateBuildKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("internal object BuildKonfig {"))
    }

    @Test fun `custom objectName is used`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.buildkonfig") }
            group = "com.example"
            buildKonfig {
                objectName.set("AppConfig")
            }
        """.trimIndent())

        run(listOf("generateBuildKonfig", "-Pkonfig.buildtype=RELEASE"))

        val text = dir.generatedBuildKonfig().readText()
        assertTrue(text.contains("object AppConfig {"))
    }

    @Test fun `task is UP-TO-DATE on second run`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.buildkonfig") }
            group = "com.example"
            buildKonfig { string("FOO", "bar") }
        """.trimIndent())

        val args = listOf("generateBuildKonfig", "-Pkonfig.buildtype=RELEASE")
        run(args)
        val second = run(args)

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateBuildKonfig")?.outcome)
    }

    @Test fun `attribute schema rules are registered automatically`() = withProject { dir, run ->
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("com.bitsycore.buildkonfig") }
            group = "com.example"
            buildKonfig {}

            // Verify the attribute and its rules are registered without any manual setup
            tasks.register("checkSchema") {
                doLast {
                    val schema = project.dependencies.attributesSchema
                    val attr = com.bitsycore.buildkonfig.KonfigBuildType.ATTRIBUTE
                    val details = schema.getMatchingStrategy(attr)
                    println("SCHEMA_OK")
                }
            }
        """.trimIndent())

        val result = run(listOf("checkSchema"))

        assertEquals(TaskOutcome.SUCCESS, result.task(":checkSchema")?.outcome)
        assertTrue(result.output.contains("SCHEMA_OK"))
    }
}
