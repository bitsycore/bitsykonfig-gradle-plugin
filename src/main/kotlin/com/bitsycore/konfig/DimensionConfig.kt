package com.bitsycore.konfig

import org.gradle.api.provider.ProviderFactory

class DimensionConfig @PublishedApi internal constructor(
	val dimensionName: String,
	val objectNameOverride: String?,
	val defaultVariant: String?,
	private val providerFactory: ProviderFactory
) {
	@PublishedApi
	internal val variants: MutableMap<String, VariantConfig> = mutableMapOf()

	fun variant(name: String, config: VariantConfig.() -> Unit) {
		val v = VariantConfig(name, providerFactory)
		config(v)
		variants[name] = v
	}

	/** Derived Kotlin object name: override or CamelCase of dimensionName. */
	fun objectName(): String = objectNameOverride
		?: dimensionName
			.split(Regex("[-_\\s]+"))
			.joinToString("") { part ->
				part.lowercase().replaceFirstChar { it.uppercase() }
			}
}
