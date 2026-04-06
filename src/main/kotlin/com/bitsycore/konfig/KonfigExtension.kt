package com.bitsycore.konfig

import com.bitsycore.konfig.configs.BuildTypedFieldDeclScope
import com.bitsycore.konfig.configs.DimensionConfig
import com.bitsycore.konfig.configs.FieldConfig
import com.bitsycore.konfig.configs.VariantConfig
import com.bitsycore.konfig.types.KonfigDsl
import com.bitsycore.konfig.types.Visibility
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import javax.inject.Inject

@KonfigDsl
abstract class KonfigExtension @Inject constructor(
    objects: ObjectFactory,
) {

    // ==============================================================================
    // MARK: Settings Internal
    // ==============================================================================

    internal val objectPackageProp:      Property<String>     = objects.property(String::class.java)
    internal val objectNameProp:       Property<String>     = objects.property(String::class.java)
    internal val objectVisibilityProp: Property<Visibility> = objects.property(Visibility::class.java)

    // ==============================================================================
    // MARK: User facing Settings
    // ==============================================================================

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

    /** Output directory — kept as [DirectoryProperty] for full Gradle lazy semantics. */
    val outputDir: DirectoryProperty = objects.directoryProperty()

    // ==============================================================================
    // MARK: DSL Internal
    // ==============================================================================

    @PublishedApi
    internal val dimensions: MutableList<DimensionConfig> = mutableListOf()

    /** Backing store for global fields — reuses [com.bitsycore.konfig.configs.VariantConfig] for its field/debug/release logic. */
    @PublishedApi
    internal val globalScope: VariantConfig = VariantConfig("\$global")

    internal val globalFields: List<FieldConfig<*>> get() = globalScope.fields

    // ==============================================================================
    // MARK: Top Level DSL
    // ==============================================================================

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

    inline fun <reified T : Any> field(
        name: String,
        default: T,
    ) = globalScope.field(name, default)

    inline fun <reified T : Any> field(
        name: String,
        default: Provider<T>,
    ) = globalScope.field(name, default)

    fun debug(block: BuildTypedFieldDeclScope.() -> Unit)   = globalScope.debug(block)
    fun release(block: BuildTypedFieldDeclScope.() -> Unit) = globalScope.release(block)
}
