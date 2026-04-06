package com.bitsycore.konfig

import org.gradle.testkit.runner.TaskOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Functional tests for the basic file-generation behaviour of the plugin:
 * package derivation, object name, BUILD_TYPE, IS_DEBUG, MODULE_NAME,
 * and the required file header.
 */
class BasicGenerationFunctionalTest : FunctionalTestBase() {

    @Test fun `generateKonfig task succeeds`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKonfig")?.outcome)
    }

    @Test fun `package is derived from group and project name`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("package com.example.test.project"))
    }

    @Test fun `default object name is BuildKonfig`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("object BuildKonfig {"))
    }

    @Test fun `RELEASE build sets BUILD_TYPE to release`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("""const val BUILD_TYPE: String = "release""""))
    }

    @Test fun `DEBUG build sets BUILD_TYPE to debug`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {}
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG"))
        assertTrue(dir.generatedFile().readText().contains("""const val BUILD_TYPE: String = "debug""""))
    }

    @Test fun `RELEASE build emits const IS_DEBUG = false`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("const val IS_DEBUG: Boolean = false"))
    }

    @Test fun `DEBUG build emits inline IS_DEBUG getter`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=DEBUG"))
        assertTrue(dir.generatedFile().readText().contains("inline val IS_DEBUG: Boolean get() = true"))
    }

    @Test fun `MODULE_NAME const is present`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("""const val MODULE_NAME: String = """))
    }

    @Test fun `generated file contains RedundantVisibilityModifier suppress header`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
            konfig {}
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("""@file:Suppress("RedundantVisibilityModifier")"""))
    }

    @Test fun `generated file contains DO NOT EDIT comment`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
        """)
        run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertTrue(dir.generatedFile().readText().contains("DO NOT EDIT"))
    }

    @Test fun `no konfig block still generates file`() = withProject { dir, run ->
        dir.writeBuildGradle("""
            plugins { id("com.bitsycore.konfig") }
            group = "com.example"
        """)
        val result = run(listOf("generateKonfig", "-Pkonfig.buildtype=RELEASE"))
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateKonfig")?.outcome)
        assertTrue(dir.generatedFile().readText().contains("object BuildKonfig"))
    }
}
