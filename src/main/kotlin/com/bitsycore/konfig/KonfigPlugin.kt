@file:Suppress("unused")

package com.bitsycore.konfig

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class KonfigPlugin : Plugin<Project> {

	override fun apply(project: Project) {
		project.dependencies.attributesSchema {
			attribute(BuildType.ATTRIBUTE) {
				compatibilityRules.add(BuildType.CompatibilityRule::class.java)
				disambiguationRules.add(BuildType.DisambiguationRule::class.java)
			}
		}

		val extension = project.extensions.create("konfig", KonfigExtension::class.java).apply {
			packageName.convention(project.provider { defaultPackageName(project) })
			objectName.convention(project.provider { "BuildKonfig" })
			objectVisibility.convention(project.provider { Visibility.PUBLIC })
			outputDir.convention(project.layout.buildDirectory.dir("generated/konfig").get())
		}

		val buildTypeProvider = project.providers.gradleProperty("konfig.buildtype")
			.map { BuildType.resolve(it) }
			.orElse(
				project.provider {
					BuildType.resolve(project.gradle.startParameter.taskNames.joinToString(" "))
						?: BuildType.RELEASE
				}
			)

		val generateTask = project.tasks.register("generateKonfig", GenerateKonfigTask::class.java).apply {
			configure {
				moduleName.set(project.name)

				buildType.set(buildTypeProvider)

				outputDirectory.set(extension.outputDir)
				packageName.set(extension.packageName)
				objectName.set(extension.objectName)
				objectVisibility.set(extension.objectVisibility)

				booleanFields.set(providerMap(buildTypeProvider, extension) { it as? FieldValue.BOOL })
				stringFields.set(providerMap(buildTypeProvider, extension) { it as? FieldValue.STRING })
				intFields.set(providerMap(buildTypeProvider, extension) { it as? FieldValue.INT })
				longFields.set(providerMap(buildTypeProvider, extension) { it as? FieldValue.LONG })
				floatFields.set(providerMap(buildTypeProvider, extension) { it as? FieldValue.FLOAT })
				doubleFields.set(providerMap(buildTypeProvider, extension) { it as? FieldValue.DOUBLE })
			}
		}

		// ADD DEPENDENCY TO COMPILE TASKS
		project.tasks.configureEach {
			if (
				name.contains("sourcesJar")
				|| name.contains("SourcesJar")
				|| name.contains("compileKotlin")
				|| (name.startsWith("compile") && name.contains("Kotlin"))
				|| (name.startsWith("compile") && name.contains("Main"))
			) {
				dependsOn(generateTask)
			}
		}
	}

	private fun <T : Any> providerMap(
		buildVariant: Provider<BuildType>,
		extension: KonfigExtension,
		filter: (FieldValue<*>) -> FieldValue<T>?
	): Provider<Map<String, T>> = buildVariant.map { variant ->
		extension.fields.mapNotNull { (name, fieldValue) ->
			filter(fieldValue)?.let {
				val resolved = it.resolve(variant).orNull
				resolved?.let { value -> name to value }
			}
		}.toMap()
	}

	private fun defaultPackageName(project: Project): String {
		val group = project.group.toString()
			.lowercase()
			.takeIf { it.isNotBlank() && it != "unspecified" }

		val artifact = project.name
			.replace("-", ".")
			.replace(Regex("[^A-Za-z0-9.]"), "")

		return if (group != null) {
			"$group.$artifact"
		} else {
			artifact.ensureValidPackage()
		}
	}

	private fun String.ensureValidPackage(): String {
		return split(".")
			.filter { it.isNotBlank() }
			.joinToString(".") { segment ->
				val cleaned = segment
					.replace(Regex("[^A-Za-z0-9_]"), "")

				when {
					cleaned.isBlank() -> "_"
					cleaned.first().isDigit() -> "_$cleaned"
					else -> cleaned
				}
			}.lowercase()
	}
}
