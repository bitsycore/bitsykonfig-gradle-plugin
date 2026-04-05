package com.bitsycore.konfig

import org.gradle.api.provider.Provider

// ── Modifier scope (receiver of field(...) { ... } lambda) ───────────────────

/**
 * Receiver for the optional lambda on `field(name, default) { ... }`.
 *
 * Provides `debug(value)` and `release(value)` overrides for a single field.
 * Only available when the field is declared at the top level of a [VariantConfig]
 * (not inside a `debug {}` or `release {}` block, where a build type is already fixed).
 */
@KonfigDsl
class FieldModifierScope<T : Any> @PublishedApi internal constructor(
    @PublishedApi internal val field: FieldConfig<T>
) {
    fun debug(value: T)             = field.debug(value)
    fun debug(value: Provider<T>)   = field.debug(value)
    fun release(value: T)           = field.release(value)
    fun release(value: Provider<T>) = field.release(value)
}

// ── Scope for fields declared inside debug {} / release {} blocks ─────────────

/**
 * Receiver of `debug { ... }` and `release { ... }` blocks inside [VariantConfig].
 *
 * Only `field(name, value)` is available here — no nested `debug {}`/`release {}` blocks
 * and no modifier lambda on `field()`, because the build type is already fixed by the
 * enclosing scope.
 */
@KonfigDsl
class BuildTypedFieldDeclScope @PublishedApi internal constructor(
    @PublishedApi internal val buildType: BuildType,
    @PublishedApi internal val owner: VariantConfig
) {
    /** Declares a field visible **only** for this build type. */
    inline fun <reified T : Any> field(name: String, value: T) {
        val fc = owner.getOrCreateField<T>(name)
        fc.buildTypeOverrides[buildType] = constantProvider(value)
    }

    inline fun <reified T : Any> field(name: String, value: Provider<T>) {
        val fc = owner.getOrCreateField<T>(name)
        fc.buildTypeOverrides[buildType] = value
    }
}

// ── Main variant / common scope ───────────────────────────────────────────────

/**
 * DSL scope for a variant (or the `common {}` block inside a dimension).
 *
 * ```kotlin
 * variant("prod") {
 *     field("URL", "https://prod.example.com")
 *     field("TIMEOUT", 30) { debug(5) }
 *     debug   { field("LOG_LEVEL", "verbose") }
 *     release { field("LOG_LEVEL", "error")   }
 * }
 * ```
 */
@KonfigDsl
class VariantConfig @PublishedApi internal constructor(val variantName: String) {

    @PublishedApi
    internal val fields: MutableList<FieldConfig<*>> = mutableListOf()

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Returns the existing [FieldConfig] for [name] if present, or creates a new one
     * with a `null` default (used when a field is first declared inside a scope block).
     */
    @PublishedApi
    internal inline fun <reified T : Any> getOrCreateField(name: String): FieldConfig<T> {
        val existing = fields.firstOrNull { it.fieldName == name }
        if (existing != null) {
            @Suppress("UNCHECKED_CAST")
            return existing as FieldConfig<T>
        }
        val fc = FieldConfig(name, T::class.javaObjectType, null)
        fields.add(fc)
        return fc
    }

    // ── Top-level field declarations ──────────────────────────────────────────

    /**
     * Declares a field with an unconditional default value.
     *
     * The optional [config] lambda receives a [FieldModifierScope] where you can set
     * `debug(value)` / `release(value)` overrides.  This lambda is only available here
     * (not inside `debug {}` / `release {}` blocks) because the build type is not yet fixed.
     */
    inline fun <reified T : Any> field(
        name: String,
        default: T,
        noinline config: (FieldModifierScope<T>.() -> Unit)? = null
    ) {
        require(fields.none { it.fieldName == name }) {
            "konfig: field '$name' is already declared in variant '$variantName'"
        }
        val fc = FieldConfig(name, T::class.javaObjectType, constantProvider(default))
        config?.invoke(FieldModifierScope(fc))
        fields.add(fc)
    }

    inline fun <reified T : Any> field(
        name: String,
        default: Provider<T>,
        noinline config: (FieldModifierScope<T>.() -> Unit)? = null
    ) {
        require(fields.none { it.fieldName == name }) {
            "konfig: field '$name' is already declared in variant '$variantName'"
        }
        val fc = FieldConfig(name, T::class.javaObjectType, default)
        config?.invoke(FieldModifierScope(fc))
        fields.add(fc)
    }

    // ── Build-type scope blocks ───────────────────────────────────────────────

    /**
     * Declares fields that are only present (or override the default) in DEBUG builds.
     *
     * Fields declared here that do **not** already exist at the top level get a `null`
     * default — meaning they are absent in RELEASE builds.
     */
    fun debug(block: BuildTypedFieldDeclScope.() -> Unit) =
        BuildTypedFieldDeclScope(BuildType.DEBUG, this).block()

    /**
     * Declares fields that are only present (or override the default) in RELEASE builds.
     *
     * Fields declared here that do **not** already exist at the top level get a `null`
     * default — meaning they are absent in DEBUG builds.
     */
    fun release(block: BuildTypedFieldDeclScope.() -> Unit) =
        BuildTypedFieldDeclScope(BuildType.RELEASE, this).block()
}
