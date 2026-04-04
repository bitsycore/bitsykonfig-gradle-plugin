@file:Suppress("unused")

package com.bitsycore.konfig

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension

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
			outputDir.convention(project.layout.buildDirectory.dir("generated/konfig"))
		}

		// ── Config-cache-safe providers ───────────────────────────────────────
		val taskNamesProvider      = project.provider { project.gradle.startParameter.taskNames }
		val dimensionPropsProvider = project.providers.gradlePropertiesPrefixedBy("konfig.dimension.")

		val buildTypeDetectionEnabled: Provider<Boolean> = project.providers
			.gradleProperty("konfig.android.buildtypedetection")
			.map { it != "false" }
			.orElse(project.provider { true })

		val flavorDetectionEnabled: Provider<Boolean> = project.providers
			.gradleProperty("konfig.android.flavordetection")
			.map { it != "false" }
			.orElse(project.provider { true })

		val buildTypeProvider: Provider<BuildType> = project.providers
			.gradleProperty("konfig.buildtype")
			.map { BuildType.resolve(it) ?: BuildType.RELEASE }
			.orElse(
				buildTypeDetectionEnabled.zip(taskNamesProvider) { enabled, names ->
					if (enabled) BuildType.resolve(names.joinToString(" ")) ?: BuildType.RELEASE
					else BuildType.RELEASE
				}
			)

		// Combined provider used by all dimension resolvers
		val combinedProps = dimensionPropsProvider
			.zip(flavorDetectionEnabled) { props, fe -> props to fe }
			.zip(taskNamesProvider) { (props, fe), names -> Triple(props, fe, names) }

		// ── Task registration ─────────────────────────────────────────────────
		val generateTask = project.tasks.register("generateKonfig", GenerateKonfigTask::class.java).apply {
			configure {
				moduleName.set(project.name)
				buildType.set(buildTypeProvider)
				outputDirectory.set(extension.outputDir)
				packageName.set(extension.packageName)
				objectName.set(extension.objectName)
				objectVisibility.set(extension.objectVisibility)

				// ── Global fields ─────────────────────────────────────────────
				globalStringFields.set(resolveGlobalFields(buildTypeProvider, extension, String::class.javaObjectType))
				globalBooleanFields.set(resolveGlobalFields(buildTypeProvider, extension, Boolean::class.javaObjectType))
				globalIntFields.set(resolveGlobalFields(buildTypeProvider, extension, Int::class.javaObjectType))
				globalLongFields.set(resolveGlobalFields(buildTypeProvider, extension, Long::class.javaObjectType))
				globalFloatFields.set(resolveGlobalFields(buildTypeProvider, extension, Float::class.javaObjectType))
				globalDoubleFields.set(resolveGlobalFields(buildTypeProvider, extension, Double::class.javaObjectType))

				// ── Dimension metadata ────────────────────────────────────────
				activeDimensionNames.set(
					buildTypeProvider.zip(combinedProps) { _, (dimProps, fe, taskNames) ->
						extension.dimensions.mapNotNull { dim ->
							resolveActiveVariant(dim, dimProps, fe, taskNames) ?: return@mapNotNull null
							dim.dimensionName
						}
					}
				)
				dimensionObjectNames.set(
					buildTypeProvider.map {
						extension.dimensions.associate { dim -> dim.dimensionName to dim.objectName() }
					}
				)
				dimensionActiveVariants.set(
					buildTypeProvider.zip(combinedProps) { _, (dimProps, fe, taskNames) ->
						extension.dimensions.mapNotNull { dim ->
							val sv = resolveActiveVariant(dim, dimProps, fe, taskNames) ?: return@mapNotNull null
							dim.dimensionName to sv
						}.toMap()
					}
				)

				// ── Dimension fields (flat: "<dimName>|<fieldName>" -> value) ─
				dimensionStringFields.set(resolveDimensionFields(buildTypeProvider, combinedProps, extension, String::class.javaObjectType))
				dimensionBooleanFields.set(resolveDimensionFields(buildTypeProvider, combinedProps, extension, Boolean::class.javaObjectType))
				dimensionIntFields.set(resolveDimensionFields(buildTypeProvider, combinedProps, extension, Int::class.javaObjectType))
				dimensionLongFields.set(resolveDimensionFields(buildTypeProvider, combinedProps, extension, Long::class.javaObjectType))
				dimensionFloatFields.set(resolveDimensionFields(buildTypeProvider, combinedProps, extension, Float::class.javaObjectType))
				dimensionDoubleFields.set(resolveDimensionFields(buildTypeProvider, combinedProps, extension, Double::class.javaObjectType))
			}
		}

		// ── Auto-wire generated sources into Kotlin source sets ───────────────
		project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
			project.extensions.findByType(KotlinMultiplatformExtension::class.java)
				?.sourceSets?.findByName("commonMain")?.kotlin?.srcDir(extension.outputDir)
		}
		project.plugins.withId("org.jetbrains.kotlin.jvm") {
			project.extensions.findByType(KotlinSingleTargetExtension::class.java)
				?.sourceSets?.findByName("main")?.kotlin?.srcDir(extension.outputDir)
		}
		project.plugins.withId("org.jetbrains.kotlin.android") {
			project.extensions.findByType(KotlinSingleTargetExtension::class.java)
				?.sourceSets?.findByName("main")?.kotlin?.srcDir(extension.outputDir)
		}
		listOf("com.android.application", "com.android.library").forEach { androidPluginId ->
			project.plugins.withId(androidPluginId) {
				@Suppress("UnstableApiUsage")
				(project.extensions.findByName("android") as? CommonExtension<*, *, *, *>)
					?.sourceSets?.findByName("main")?.kotlin?.srcDir(extension.outputDir)
			}
		}

		// ── Hook compile tasks ────────────────────────────────────────────────
		project.tasks.configureEach {
			if (
				name.contains("sourcesJar") || name.contains("SourcesJar")
				|| name.contains("compileKotlin")
				|| (name.startsWith("compile") && name.contains("Kotlin"))
				|| (name.startsWith("compile") && name.contains("Main"))
			) {
				dependsOn(generateTask)
			}
		}
	}

	// ── Resolution helpers ────────────────────────────────────────────────────

	/**
	 * Resolves the active variant for a dimension:
	 * 1. Explicit `konfig.dimension.<name>` property
	 * 2. Task-name detection (if flavor detection enabled)
	 * 3. `defaultTo` fallback
	 * Returns null if no active variant can be determined.
	 */
	private fun resolveActiveVariant(
		dim: DimensionConfig,
		dimProps: Map<String, String>,
		flavorDetect: Boolean,
		taskNames: List<String>
	): String? = (
		// Support both stripped key ("env") and full key ("konfig.dimension.env")
		// because gradlePropertiesPrefixedBy() behaviour varies across Gradle versions.
		dimProps[dim.dimensionName]
			?: dimProps["konfig.dimension.${dim.dimensionName}"]
		)
		?: (if (flavorDetect) {
			dim.variants.keys.singleOrNull { variant ->
				taskNames.any { task -> task.contains(variant, ignoreCase = true) }
			}
		} else null)
		?: dim.defaultVariant

	private fun <T : Any> resolveGlobalFields(
		buildType: Provider<BuildType>,
		extension: KonfigExtension,
		type: Class<T>
	): Provider<Map<String, T>> = buildType.map { bt ->
		@Suppress("UNCHECKED_CAST")
		extension.globalFields
			.filter { it.type == type }
			.mapNotNull { field ->
				val f = field as FieldConfig<T>
				f.resolve(bt).orNull?.let { f.fieldName to it }
			}
			.toMap()
	}

	/**
	 * Builds a flat map of "<dimensionName>|<fieldName>" -> value
	 * for all active dimensions, filtered to the given type.
	 */
	@Suppress("UNCHECKED_CAST")
	private fun <T : Any> resolveDimensionFields(
		buildType: Provider<BuildType>,
		combined: Provider<Triple<Map<String, String>, Boolean, List<String>>>,
		extension: KonfigExtension,
		type: Class<T>
	): Provider<Map<String, T>> =
		buildType.zip(combined) { bt, (dimProps, fe, taskNames) ->
			val result = mutableMapOf<String, T>()
			for (dim in extension.dimensions) {
				val sv = resolveActiveVariant(dim, dimProps, fe, taskNames) ?: continue
				val vc = dim.variants[sv] ?: continue
				vc.fields
					.filter { it.type == type }
					.forEach { field ->
						val f = field as FieldConfig<T>
						f.resolve(bt).orNull?.let { value ->
							result["${dim.dimensionName}|${f.fieldName}"] = value
						}
					}
			}
			result as Map<String, T>
		}

	// ── Package-name helpers ──────────────────────────────────────────────────

	private fun defaultPackageName(project: Project): String {
		val group = project.group.toString()
			.lowercase()
			.takeIf { it.isNotBlank() && it != "unspecified" }
		val artifact = project.name
			.replace("-", ".")
			.replace(Regex("[^A-Za-z0-9.]"), "")
		return if (group != null) "$group.$artifact" else artifact.ensureValidPackage()
	}

	private fun String.ensureValidPackage(): String = split(".")
		.filter { it.isNotBlank() }
		.joinToString(".") { segment ->
			val cleaned = segment.replace(Regex("[^A-Za-z0-9_]"), "")
			when {
				cleaned.isBlank()         -> "_"
				cleaned.first().isDigit() -> "_$cleaned"
				else                      -> cleaned
			}
		}.lowercase()
}
