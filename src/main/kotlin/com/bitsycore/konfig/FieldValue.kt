package com.bitsycore.konfig

import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

sealed class FieldValue<T : Any>(
    internal open val provider: ProviderFactory,
    internal open val value: Provider<T>,
    internal open val valueOverrideBuildType: Array<Provider<T>?> = arrayOfNulls(BuildType.entries.size)
) {

	fun release(value: Provider<T>) {
        valueOverrideBuildType[BuildType.RELEASE.ordinal] = value
	}

	fun release(value: T) {
		valueOverrideBuildType[BuildType.RELEASE.ordinal] = provider.provider { value }
	}

	fun release(value: () -> T) {
		valueOverrideBuildType[BuildType.RELEASE.ordinal] = provider.provider { value() }
	}

	fun debug(value: Provider<T>) {
		valueOverrideBuildType[BuildType.DEBUG.ordinal] = value
	}

	fun debug(value: T) {
		valueOverrideBuildType[BuildType.DEBUG.ordinal] = provider.provider { value }
	}

	fun debug(value: () -> T) {
		valueOverrideBuildType[BuildType.DEBUG.ordinal] = provider.provider { value() }
	}

	internal fun resolve(buildType: BuildType): Provider<T> {
		return valueOverrideBuildType[buildType.ordinal] ?: value
	}

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BOOL

        if (provider != other.provider) return false
        if (value != other.value) return false
        if (!valueOverrideBuildType.contentEquals(other.valueOverrideBuildType)) return false

        return true
    }
    override fun hashCode(): Int {
        var result = provider.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + valueOverrideBuildType.contentHashCode()
        return result
    }

    @PublishedApi
	internal class STRING(
        override val provider: ProviderFactory,
        override val value: Provider<String>,
        override val valueOverrideBuildType: Array<Provider<String>?> = arrayOfNulls(BuildType.entries.size)
	) : FieldValue<String>(provider, value, valueOverrideBuildType)

    @PublishedApi
    internal class BOOL(
        override val provider: ProviderFactory,
        override val value: Provider<Boolean>,
        override val valueOverrideBuildType: Array<Provider<Boolean>?> = arrayOfNulls(BuildType.entries.size)
    ) : FieldValue<Boolean>(provider, value, valueOverrideBuildType)

    @PublishedApi
	internal class INT(
        override val provider: ProviderFactory,
        override val value: Provider<Int>,
        override val valueOverrideBuildType: Array<Provider<Int>?> = arrayOfNulls(BuildType.entries.size)
	) : FieldValue<Int>(provider, value, valueOverrideBuildType)

    @PublishedApi
    internal class LONG(
        override val provider: ProviderFactory,
        override val value: Provider<Long>,
        override val valueOverrideBuildType: Array<Provider<Long>?> = arrayOfNulls(BuildType.entries.size)
    ) : FieldValue<Long>(provider, value, valueOverrideBuildType)

    @PublishedApi
    internal class FLOAT(
        override val provider: ProviderFactory,
        override val value: Provider<Float>,
        override val valueOverrideBuildType: Array<Provider<Float>?> = arrayOfNulls(BuildType.entries.size)
    ) : FieldValue<Float>(provider, value, valueOverrideBuildType)

    @PublishedApi
    internal class DOUBLE(
        override val provider: ProviderFactory,
        override val value: Provider<Double>,
        override val valueOverrideBuildType: Array<Provider<Double>?> = arrayOfNulls(BuildType.entries.size)
    ) : FieldValue<Double>(provider, value, valueOverrideBuildType)
}
