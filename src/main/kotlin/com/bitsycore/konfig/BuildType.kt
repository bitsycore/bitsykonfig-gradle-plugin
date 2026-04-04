package com.bitsycore.konfig

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import kotlin.enums.enumEntries

enum class BuildType(val value: String) {
	DEBUG("debug"),
	RELEASE("release");

	companion object {
		val ATTRIBUTE: Attribute<BuildType> = Attribute.of("com.bitsycore.buildtype", BuildType::class.java)
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

	class CompatibilityRule : AttributeCompatibilityRule<BuildType> {
		override fun execute(details: CompatibilityCheckDetails<BuildType>) {
			details.compatible()
		}
	}

	class DisambiguationRule : AttributeDisambiguationRule<BuildType> {
		override fun execute(details: MultipleCandidatesDetails<BuildType>) {
			val consumerValue = details.consumerValue
			if (consumerValue != null && details.candidateValues.contains(consumerValue)) {
				details.closestMatch(consumerValue)
			} else if (details.candidateValues.contains(RELEASE)) {
				details.closestMatch(RELEASE)
			}
		}
	}
}
