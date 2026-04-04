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
	internal val dimensions: MutableList<DimensionConfig> = mutableListOf()

	@PublishedApi
	internal val globalFields: MutableList<FieldConfig<*>> = mutableListOf()

	// ─── Dimension DSL ───────────────────────────────────────────────────────

	fun dimension(
		name: String,
		objectNameOverride: String? = null,
		defaultTo: String? = null,
		config: DimensionConfig.() -> Unit
	) {
		require(dimensions.none { it.dimensionName == name }) {
			"konfig: dimension '$name' is already declared"
		}
		val d = DimensionConfig(name, objectNameOverride, defaultTo, providers)
		config(d)
		dimensions.add(d)
	}

	// ─── Global field DSL ────────────────────────────────────────────────────

	inline fun <reified T : Any> field(
		name: String,
		default: T,
		noinline config: (FieldConfig<T>.() -> Unit)? = null
	) {
		require(globalFields.none { it.fieldName == name }) {
			"konfig: global field '$name' is already declared"
		}
		val f = FieldConfig(name, T::class.javaObjectType, providers, providers.provider { default })
		config?.invoke(f)
		globalFields.add(f)
	}

	inline fun <reified T : Any> field(
		name: String,
		default: Provider<T>,
		noinline config: (FieldConfig<T>.() -> Unit)? = null
	) {
		require(globalFields.none { it.fieldName == name }) {
			"konfig: global field '$name' is already declared"
		}
		val f = FieldConfig(name, T::class.javaObjectType, providers, default)
		config?.invoke(f)
		globalFields.add(f)
	}
}
