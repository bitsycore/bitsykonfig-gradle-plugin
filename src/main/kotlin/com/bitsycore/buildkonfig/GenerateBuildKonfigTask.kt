package com.bitsycore.buildkonfig

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateBuildKonfigTask : DefaultTask() {

	@get:Input
	abstract val packageName: Property<String>

	@get:Input
	abstract val moduleName: Property<String>

	@get:Input
	abstract val objectName: Property<String>

	@get:Input
	abstract val objectVisibility: Property<Visibility>

	@get:Input
	abstract val buildType: Property<KonfigBuildType>

	@get:Input
	abstract val stringFields: MapProperty<String, String>

	@get:Input
	abstract val booleanFields: MapProperty<String, Boolean>

	@get:Input
	abstract val intFields: MapProperty<String, Int>

	@get:OutputDirectory
	abstract val outputDirectory: DirectoryProperty

	@TaskAction
	fun generate() {
		val pkg = packageName.get()
		val isDebug = buildType.get() == KonfigBuildType.DEBUG
		val visibility = objectVisibility.get()
		val visibilityPrefix = if (visibility == Visibility.INTERNAL) "internal " else ""
		val outDir = outputDirectory.get().asFile
		if (outDir.exists()) outDir.deleteRecursively()

		val pkgDir = outDir.resolve(pkg.replace('.', '/'))
		pkgDir.mkdirs()

		val content = buildString {
			appendLine("package $pkg")
			appendLine()
			appendLine("${visibilityPrefix}object ${objectName.get()} {")
			appendLine("\tconst val MODULE_NAME: String = \"${moduleName.get()}\"")
			if (isDebug)
				appendLine("\tinline val IS_DEBUG: Boolean get() = true")
			else
				appendLine("\tconst val IS_DEBUG: Boolean = false")
			appendLine("\tconst val VARIANT: String = \"${buildType.get()}\"")

			stringFields.get().forEach { (name, value) ->
				appendLine("\tconst val $name: String = \"${value.replace("\"", "\\\"")}\"")
			}
			booleanFields.get().forEach { (name, value) ->
				appendLine("\tconst val $name: Boolean = $value")
			}
			intFields.get().forEach { (name, value) ->
				appendLine("\tconst val $name: Int = $value")
			}

			appendLine("}")
		}

		pkgDir.resolve("BuildKonfig.kt").writeText(content)
	}
}
