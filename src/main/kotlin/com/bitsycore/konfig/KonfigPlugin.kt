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

		// ── Build-type provider with source tracking ──────────────────────────
		val rawBuildTypeProp = project.providers.gradleProperty("konfig.buildtype")

		val buildTypeProvider: Provider<BuildType> = rawBuildTypeProp
			.map { BuildType.resolve(it) ?: BuildType.RELEASE }
			.orElse(
				buildTypeDetectionEnabled.zip(taskNamesProvider) { enabled, names ->
					if (enabled) BuildType.resolve(names.joinToString(" ")) ?: BuildType.RELEASE
					else BuildType.RELEASE
				}
			)

		// Encodes WHY the build type was chosen; stored as a task input for execution-time logging.
		// Format: plain descriptive string.
		val buildTypeSourceProvider: Provider<String> = rawBuildTypeProp
			.map { raw ->
				val resolved = BuildType.resolve(raw)
				if (resolved != null) "explicit property -Pkonfig.buildtype=$raw"
				else                  "explicit property -Pkonfig.buildtype=$raw (unrecognized value, fell back to RELEASE)"
			}
			.orElse(
				buildTypeDetectionEnabled.zip(taskNamesProvider) { enabled, names ->
					when {
						!enabled -> "detection disabled by konfig.android.buildtypedetection=false, using RELEASE"
						names.isEmpty() -> "no tasks running, using RELEASE"
						else -> {
							val resolved = BuildType.resolve(names.joinToString(" "))
							if (resolved != null)
								"task-name detection matched ${resolved.name.lowercase()} in [${names.joinToString()}]"
							else
								"no debug/release pattern found in tasks [${names.joinToString()}], using RELEASE"
						}
					}
				}
			)

		// Combined provider for dimension resolvers
		val combinedProps = dimensionPropsProvider
			.zip(flavorDetectionEnabled) { props, fe -> props to fe }
			.zip(taskNamesProvider) { (props, fe), names -> Triple(props, fe, names) }

		// Encodes resolution status for EVERY declared dimension (including skipped ones).
		// Format per entry: "<TAG>\t<variant>\t<reason>"
		//   TAG = OK | WARN_UNKNOWN | WARN_AMBIGUOUS | SKIP | ERROR
		val dimensionResolutionLogProvider: Provider<Map<String, String>> =
			buildTypeProvider.zip(combinedProps) { _, (dimProps, fe, taskNames) ->
				extension.dimensions.associate { dim ->
					dim.dimensionName to resolveWithSource(dim, dimProps, fe, taskNames)
				}
			}

		// ── Task registration ─────────────────────────────────────────────────
		val generateTask = project.tasks.register("generateKonfig", GenerateKonfigTask::class.java).apply {
			configure {
				moduleName.set(project.name)
				buildType.set(buildTypeProvider)
				buildTypeSource.set(buildTypeSourceProvider)
				dimensionResolutionLog.set(dimensionResolutionLogProvider)
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
	 * Resolves the active variant for a dimension (used for actual field resolution).
	 * Returns null if the dimension should be skipped.
	 */
	private fun resolveActiveVariant(
		dim: DimensionConfig,
		dimProps: Map<String, String>,
		flavorDetect: Boolean,
		taskNames: List<String>
	): String? {
		val encoded = resolveWithSource(dim, dimProps, flavorDetect, taskNames)
		val parts   = encoded.split("\t", limit = 3)
		return when (parts[0]) {
			"OK" -> parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
			else -> null
		}
	}

	/**
	 * Resolves a dimension and encodes the result as a tab-separated string for logging.
	 *
	 * Format: `"<TAG>\t<variant>\t<reason>"`
	 * - TAG = `OK` — active, variant resolved successfully
	 * - TAG = `WARN_UNKNOWN` — property set but value is not a known variant
	 * - TAG = `WARN_AMBIGUOUS` — multiple variants matched task names
	 * - TAG = `SKIP` — no active variant could be determined
	 * - TAG = `ERROR` — configuration error (e.g. invalid defaultTo)
	 */
	private fun resolveWithSource(
		dim: DimensionConfig,
		dimProps: Map<String, String>,
		flavorDetect: Boolean,
		taskNames: List<String>
	): String {
		// Validate defaultTo at resolution time
		if (dim.defaultVariant != null && !dim.variants.containsKey(dim.defaultVariant)) {
			return "ERROR\t\tdefaultTo='${dim.defaultVariant}' is not a known variant " +
				"(known: ${dim.variants.keys.sorted().joinToString()})"
		}

		// Priority 1: explicit Gradle property konfig.dimension.<name>=<value>
		val propKey   = "konfig.dimension.${dim.dimensionName}"
		val fromProp  = dimProps[dim.dimensionName] ?: dimProps[propKey]
		if (fromProp != null) {
			return if (dim.variants.containsKey(fromProp)) {
				"OK\t$fromProp\tproperty -P$propKey=$fromProp"
			} else {
				"WARN_UNKNOWN\t\tproperty -P$propKey=$fromProp is not a known variant " +
					"(known: ${dim.variants.keys.sorted().joinToString()}) -- dimension skipped"
			}
		}

		// Priority 2: task-name detection
		if (flavorDetect && taskNames.isNotEmpty()) {
			val matches = dim.variants.keys.filter { variant ->
				taskNames.any { task -> task.contains(variant, ignoreCase = true) }
			}
			when (matches.size) {
				1 -> return "OK\t${matches.first()}\ttask-name detection: '${matches.first()}' " +
					"found in [${taskNames.joinToString()}]"
				in 2..Int.MAX_VALUE -> return "WARN_AMBIGUOUS\t\ttask names matched multiple variants " +
					"${matches.sorted()} in [${taskNames.joinToString()}] -- dimension skipped"
			}
		}

		// Priority 3: defaultTo fallback
		if (dim.defaultVariant != null) {
			return "OK\t${dim.defaultVariant}\tdefaultTo='${dim.defaultVariant}'"
		}

		// Not set
		val hints = buildList {
			add("no -P$propKey property")
			if (!flavorDetect) add("flavor detection disabled")
			else if (taskNames.isEmpty()) add("no tasks running")
			else add("no variant name found in tasks [${taskNames.joinToString()}]")
			add("no defaultTo set")
		}
		return "SKIP\t\t${hints.joinToString(", ")}"
	}

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
