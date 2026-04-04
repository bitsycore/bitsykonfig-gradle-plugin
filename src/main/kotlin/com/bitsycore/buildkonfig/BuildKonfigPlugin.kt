package com.bitsycore.buildkonfig

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider

class BuildKonfigPlugin : Plugin<Project> {

	override fun apply(project: Project) {
		project.dependencies.attributesSchema {
			attribute(KonfigBuildType.ATTRIBUTE) {
				compatibilityRules.add(KonfigBuildType.CompatibilityRule::class.java)
				disambiguationRules.add(KonfigBuildType.DisambiguationRule::class.java)
			}
		}

		val extension = project.extensions.create("buildKonfig", BuildKonfigExtension::class.java).apply {
			packageName.convention(project.provider { defaultPackageName(project) })
			objectName.convention(project.provider { "BuildKonfig" })
			objectVisibility.convention(project.provider { Visibility.PUBLIC })
			outputDir.convention(project.layout.buildDirectory.dir("generated/buildkonfig").get())
		}

		val buildTypeProvider = project.providers.gradleProperty("konfig.buildtype")
			.map { KonfigBuildType.resolve(it) }
			.orElse(
				project.provider {
					KonfigBuildType.resolve(project.gradle.startParameter.taskNames.joinToString(" "))
						?: KonfigBuildType.RELEASE
				}
			)

		val generateTask = project.tasks.register("generateBuildKonfig", GenerateBuildKonfigTask::class.java).apply {
			configure {
				moduleName.set(project.name)

				buildType.set(buildTypeProvider)

				outputDirectory.set(extension.outputDir)
				packageName.set(extension.packageName)
				objectName.set(extension.objectName)
				objectVisibility.set(extension.objectVisibility)

				booleanFields.set(providerMap(buildTypeProvider, extension) { it as? FieldValue.Boolean })
				stringFields.set(providerMap(buildTypeProvider, extension) { it as? FieldValue.String })
				intFields.set(providerMap(buildTypeProvider, extension) { it as? FieldValue.Int })
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
		buildVariant: Provider<KonfigBuildType>,
		extension: BuildKonfigExtension,
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
