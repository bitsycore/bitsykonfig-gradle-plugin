package com.bitsycore.konfig.types

import kotlin.enums.enumEntries

enum class BuildType(val value: String) {
	DEBUG("debug"),
	RELEASE("release");

	companion object {
		private val DEBUG_REGEX = Regex("""(?<![a-z])debug(?![a-z])|(?<![A-Z])Debug(?![a-z])""")
		private val RELEASE_REGEX = Regex("""(?<![a-z])release(?![a-z])|(?<![A-Z])Release(?![a-z])""")

		fun resolve(value: String): BuildType? {
			enumEntries<BuildType>().firstOrNull { it.name == value }?.let {
				return it
			}

			val debugResult = DEBUG_REGEX.containsMatchIn(value)
			val releaseResult = RELEASE_REGEX.containsMatchIn(value)

			return when {
				debugResult && releaseResult.not() -> DEBUG
				releaseResult && debugResult.not() -> RELEASE
				else -> null
			}
		}
	}
}
