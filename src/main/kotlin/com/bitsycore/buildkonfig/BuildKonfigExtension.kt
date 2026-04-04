package com.bitsycore.buildkonfig

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

abstract class BuildKonfigExtension @Inject constructor(
	private val objects: ObjectFactory,
	private val providers: ProviderFactory
) {

	val packageName: Property<String> = objects.property(String::class.java)
	val objectName: Property<String> = objects.property(String::class.java)
	val objectVisibility: Property<Visibility> = objects.property(Visibility::class.java)
	val outputDir: DirectoryProperty = objects.directoryProperty()

	internal val fields: MutableMap<String, FieldValue<*>> = mutableMapOf()

	fun string(
		name: String,
		value: String? = null,
		variantConfig: (FieldValue<String>.() -> Unit)? = null
	) {
		string(name, providers.provider { value }, variantConfig)
	}

	fun string(
		name: String,
		value: Provider<String>,
		variantConfig: (FieldValue<String>.() -> Unit)? = null
	) {
		val field = FieldValue.String(providers, value)
		variantConfig?.invoke(field)
		fields[name] = field
	}

	fun boolean(
		name: String,
		value: Boolean? = null,
		variantConfig: (FieldValue<Boolean>.() -> Unit)? = null
	) {
		boolean(name, providers.provider { value }, variantConfig)
	}

	fun boolean(
		name: String,
		value: Provider<Boolean>,
		variantConfig: (FieldValue<Boolean>.() -> Unit)? = null
	) {
		val field = FieldValue.Boolean(providers, value)
		variantConfig?.invoke(field)
		fields[name] = field
	}

	fun integer(
		name: String,
		value: Int? = null,
		variantConfig: (FieldValue<Int>.() -> Unit)? = null
	) {
		integer(name, providers.provider { value }, variantConfig)
	}

	fun integer(
		name: String,
		value: Provider<Int>,
		variantConfig: (FieldValue<Int>.() -> Unit)? = null
	) {
		val field = FieldValue.Int(providers, value)
		variantConfig?.invoke(field)
		fields[name] = field
	}
}
