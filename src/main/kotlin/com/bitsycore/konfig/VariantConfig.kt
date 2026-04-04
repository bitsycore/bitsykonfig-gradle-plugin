package com.bitsycore.konfig

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

class VariantConfig @PublishedApi internal constructor(
	val variantName: String,
	@PublishedApi internal val providerFactory: ProviderFactory
) {
	@PublishedApi
	internal val fields: MutableList<FieldConfig<*>> = mutableListOf()

	inline fun <reified T : Any> field(
		name: String,
		default: T,
		noinline config: (FieldConfig<T>.() -> Unit)? = null
	) {
		val f = FieldConfig(name, T::class.javaObjectType, providerFactory, providerFactory.provider { default })
		config?.invoke(f)
		fields.add(f)
	}

	inline fun <reified T : Any> field(
		name: String,
		default: Provider<T>,
		noinline config: (FieldConfig<T>.() -> Unit)? = null
	) {
		val f = FieldConfig(name, T::class.javaObjectType, providerFactory, default)
		config?.invoke(f)
		fields.add(f)
	}
}
