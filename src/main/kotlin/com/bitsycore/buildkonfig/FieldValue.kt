package com.bitsycore.buildkonfig

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

sealed class FieldValue<T : Any>(
	internal open val provider: ProviderFactory,
	internal open val default: Provider<T>,
	internal open val buildTypes: MutableMap<KonfigBuildType, Provider<T>> = mutableMapOf()
) {

	fun release(value: Provider<T>) {
		buildTypes[KonfigBuildType.RELEASE] = value
	}

	fun release(value: T) {
		buildTypes[KonfigBuildType.RELEASE] = provider.provider { value }
	}

	fun release(value: () -> T) {
		buildTypes[KonfigBuildType.RELEASE] = provider.provider { value() }
	}

	fun debug(value: Provider<T>) {
		buildTypes[KonfigBuildType.DEBUG] = value
	}

	fun debug(value: T) {
		buildTypes[KonfigBuildType.DEBUG] = provider.provider { value }
	}

	fun debug(value: () -> T) {
		buildTypes[KonfigBuildType.DEBUG] = provider.provider { value() }
	}

	internal fun resolve(konfigBuildType: KonfigBuildType): Provider<T> {
		return buildTypes[konfigBuildType] ?: default
	}

	@ConsistentCopyVisibility
	internal data class String internal constructor(
		override val provider: ProviderFactory,
		override val default: Provider<kotlin.String>,
		override val buildTypes: MutableMap<KonfigBuildType, Provider<kotlin.String>> = mutableMapOf()
	) : FieldValue<kotlin.String>(provider, default, buildTypes)

	@ConsistentCopyVisibility
	internal data class Int internal constructor(
		override val provider: ProviderFactory,
		override val default: Provider<kotlin.Int>,
		override val buildTypes: MutableMap<KonfigBuildType, Provider<kotlin.Int>> = mutableMapOf()
	) : FieldValue<kotlin.Int>(provider, default, buildTypes)

	@ConsistentCopyVisibility
	internal data class Boolean internal constructor(
		override val provider: ProviderFactory,
		override val default: Provider<kotlin.Boolean>,
		override val buildTypes: MutableMap<KonfigBuildType, Provider<kotlin.Boolean>> = mutableMapOf()
	) : FieldValue<kotlin.Boolean>(provider, default, buildTypes)
}
