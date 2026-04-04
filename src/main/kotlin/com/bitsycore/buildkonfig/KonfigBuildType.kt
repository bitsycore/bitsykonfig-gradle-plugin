package com.bitsycore.buildkonfig

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.MultipleCandidatesDetails
import kotlin.enums.enumEntries

enum class KonfigBuildType(val value: String) {
	DEBUG("debug"),
	RELEASE("release");

	companion object {
		val ATTRIBUTE: Attribute<KonfigBuildType> = Attribute.of("com.bitsycore.buildtype", KonfigBuildType::class.java)
		private val DEBUG_REGEX = Regex("""(?<![a-z])debug(?![a-z])|(?<![A-Z])Debug(?![a-z])""")
		private val RELEASE_REGEX = Regex("""(?<![a-z])release(?![a-z])|(?<![A-Z])Release(?![a-z])""")

		fun resolve(value: String): KonfigBuildType? {
			enumEntries<KonfigBuildType>().firstOrNull { it.name == value }?.let {
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

	class CompatibilityRule : AttributeCompatibilityRule<KonfigBuildType> {
		override fun execute(details: CompatibilityCheckDetails<KonfigBuildType>) {
			details.compatible()
		}
	}

	class DisambiguationRule : AttributeDisambiguationRule<KonfigBuildType> {
		override fun execute(details: MultipleCandidatesDetails<KonfigBuildType>) {
			val consumerValue = details.consumerValue
			if (consumerValue != null && details.candidateValues.contains(consumerValue)) {
				details.closestMatch(consumerValue)
			} else if (details.candidateValues.contains(RELEASE)) {
				details.closestMatch(RELEASE)
			}
		}
	}
}
