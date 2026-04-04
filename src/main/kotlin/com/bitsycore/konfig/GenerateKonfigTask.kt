package com.bitsycore.konfig

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class GenerateKonfigTask : DefaultTask() {

	@get:Input
	abstract val packageName: Property<String>

	@get:Input
	abstract val moduleName: Property<String>

	@get:Input
	abstract val objectName: Property<String>

	@get:Input
	abstract val objectVisibility: Property<Visibility>

	@get:Input
	abstract val buildType: Property<BuildType>

	@get:Input
	abstract val stringFields: MapProperty<String, String>

	@get:Input
	abstract val booleanFields: MapProperty<String, Boolean>

	@get:Input
	abstract val intFields: MapProperty<String, Int>

	@get:Input
	abstract val longFields: MapProperty<String, Long>

	@get:Input
	abstract val floatFields: MapProperty<String, Float>

	@get:Input
	abstract val doubleFields: MapProperty<String, Double>

	@get:OutputDirectory
	abstract val outputDirectory: DirectoryProperty

	@TaskAction
	fun generate() {
		val pkg = packageName.get()
		val isDebug = buildType.get() == BuildType.DEBUG
		val visibilityPrefix = when (objectVisibility.get()) {
			Visibility.INTERNAL -> "internal "
			Visibility.PUBLIC -> "public "
		}
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
				val escaped = buildString {
					value.forEach { c ->
						when (c) {
							'\\' -> append("\\\\")
							'\"' -> append("\\\"")
							'\n' -> append("\\n")
							'\r' -> append("\\r")
							'\t' -> append("\\t")
							'$'  -> append("\\$")
							else -> append(c)
						}
					}
				}
				appendLine("\tconst val $name: String = \"$escaped\"")
			}
			booleanFields.get().forEach { (name, value) ->
				appendLine("\tconst val $name: Boolean = $value")
			}
			intFields.get().forEach { (name, value) ->
				appendLine("\tconst val $name: Int = $value")
			}
			longFields.get().forEach { (name, value) ->
				appendLine("\tconst val $name: Long = ${value}L")
			}
			floatFields.get().forEach { (name, value) ->
				val floatValidLiteral = when {
					value.isNaN() -> "Float.NaN"
					value == Float.POSITIVE_INFINITY -> "Float.POSITIVE_INFINITY"
					value == Float.NEGATIVE_INFINITY -> "Float.NEGATIVE_INFINITY"
					else -> value.toString().let {
						if (!it.contains('.') && !it.contains('E') && !it.contains('e')) "$it.0" else it
					} + "f"
				}
				appendLine("\tconst val $name: Float = $floatValidLiteral")
			}
			doubleFields.get().forEach { (name, value) ->
				val doubleValidLiteral = when {
					value.isNaN() -> "Double.NaN"
					value == Double.POSITIVE_INFINITY -> "Double.POSITIVE_INFINITY"
					value == Double.NEGATIVE_INFINITY -> "Double.NEGATIVE_INFINITY"
					else -> value.toString().let {
						if (!it.contains('.') && !it.contains('E') && !it.contains('e')) "$it.0" else it
					}
				}
				appendLine("\tconst val $name: Double = $doubleValidLiteral")
			}

			appendLine("}")
		}

		pkgDir.resolve("BuildKonfig.kt").writeText(content)
	}
}
