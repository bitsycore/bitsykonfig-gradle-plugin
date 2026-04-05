package com.bitsycore.konfig

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

@KonfigDsl
abstract class KonfigExtension @Inject constructor(
    private val objects: ObjectFactory,
) {
    // ── Backing Gradle properties (used by the plugin for lazy wiring) ────────

    internal val objectPackageProp:      Property<String>     = objects.property(String::class.java)
    internal val objectNameProp:       Property<String>     = objects.property(String::class.java)
    internal val objectVisibilityProp: Property<Visibility> = objects.property(Visibility::class.java)

    /** Output directory — kept as [DirectoryProperty] for full Gradle lazy semantics. */
    val outputDir: DirectoryProperty = objects.directoryProperty()

    // ── User-facing var properties ────────────────────────────────────────────

    /** Package for the generated object (e.g. `"com.example.app"`). */
    var objectPackage: String
        get()      = objectPackageProp.get()
        set(value) = objectPackageProp.set(value)

    /** Name of the generated Kotlin object (default: `"BuildKonfig"`). */
    var objectName: String
        get()      = objectNameProp.get()
        set(value) = objectNameProp.set(value)

    /** Visibility of the generated object (default: [Visibility.PUBLIC]). */
    var objectVisibility: Visibility
        get()      = objectVisibilityProp.get()
        set(value) = objectVisibilityProp.set(value)

    // ── DSL internals ─────────────────────────────────────────────────────────

    @PublishedApi
    internal val dimensions: MutableList<DimensionConfig> = mutableListOf()

    /** Backing store for global fields — reuses [VariantConfig] for its field/debug/release logic. */
    @PublishedApi
    internal val globalScope: VariantConfig = VariantConfig("\$global")

    internal val globalFields: List<FieldConfig<*>> get() = globalScope.fields

    // ─── Dimension DSL ────────────────────────────────────────────────────────

    fun dimension(
        name: String,
        objectNameOverride: String? = null,
        defaultTo: String? = null,
        config: DimensionConfig.() -> Unit
    ) {
        require(dimensions.none { it.dimensionName == name }) {
            "konfig: dimension '$name' is already declared"
        }
        val d = DimensionConfig(name, objectNameOverride, defaultTo)
        config(d)
        dimensions.add(d)
    }

    // ─── Global field DSL — delegates to globalScope ──────────────────────────

    inline fun <reified T : Any> field(
        name: String,
        default: T,
        noinline config: (FieldModifierScope<T>.() -> Unit)? = null
    ) = globalScope.field(name, default, config)

    inline fun <reified T : Any> field(
        name: String,
        default: Provider<T>,
        noinline config: (FieldModifierScope<T>.() -> Unit)? = null
    ) = globalScope.field(name, default, config)

    fun debug(block: BuildTypedFieldDeclScope.() -> Unit)   = globalScope.debug(block)
    fun release(block: BuildTypedFieldDeclScope.() -> Unit) = globalScope.release(block)
}
