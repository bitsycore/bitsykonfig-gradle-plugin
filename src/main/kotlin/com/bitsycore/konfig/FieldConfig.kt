package com.bitsycore.konfig

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

class FieldConfig<T : Any> @PublishedApi internal constructor(
	val fieldName: String,
	internal val type: Class<T>,
	private val providerFactory: ProviderFactory,
	internal val default: Provider<T>
) {
	internal val buildTypeOverrides: MutableMap<BuildType, Provider<T>> = mutableMapOf()

	fun debug(value: T) {
		buildTypeOverrides[BuildType.DEBUG] = providerFactory.provider { value }
	}

	fun debug(value: Provider<T>) {
		buildTypeOverrides[BuildType.DEBUG] = value
	}

	fun release(value: T) {
		buildTypeOverrides[BuildType.RELEASE] = providerFactory.provider { value }
	}

	fun release(value: Provider<T>) {
		buildTypeOverrides[BuildType.RELEASE] = value
	}

	internal fun resolve(buildType: BuildType): Provider<T> =
		buildTypeOverrides[buildType] ?: default
}
