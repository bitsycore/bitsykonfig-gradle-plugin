package com.bitsycore.konfig

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

abstract class KonfigExtension @Inject constructor(
	private val objects: ObjectFactory,
	@PublishedApi
	internal val providers: ProviderFactory
) {

	val packageName: Property<String> = objects.property(String::class.java)
	val objectName: Property<String> = objects.property(String::class.java)
	val objectVisibility: Property<Visibility> = objects.property(Visibility::class.java)
	val outputDir: DirectoryProperty = objects.directoryProperty()

	@PublishedApi
	internal val fields: MutableMap<String, FieldValue<*>> = mutableMapOf()

	inline fun <reified T : Any> field(
		name: String,
		value: Provider<T>,
		noinline variantConfig: (FieldValue<T>.() -> Unit)? = null
	) {
		@Suppress("UNCHECKED_CAST")
		val field = when (T::class) {
			String::class -> FieldValue.STRING(providers, value as Provider<String>)
			Boolean::class -> FieldValue.BOOL(providers, value as Provider<Boolean>)
			Int::class -> FieldValue.INT(providers, value as Provider<Int>)
			Long::class -> FieldValue.LONG(providers, value as Provider<Long>)
			Float::class -> FieldValue.FLOAT(providers, value as Provider<Float>)
			Double::class -> FieldValue.DOUBLE(providers, value as Provider<Double>)
			else -> error("Unsupported type for field '$name': ${T::class}")
		} as FieldValue<T>

		variantConfig?.invoke(field)
		fields[name] = field
	}

	inline fun <reified T : Any> field(
		name: String,
		value: T,
		noinline variantConfig: (FieldValue<T>.() -> Unit)? = null
	) {
		field(name, providers.provider { value }, variantConfig)
	}
}
