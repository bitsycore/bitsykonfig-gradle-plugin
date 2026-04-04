plugins {
	`kotlin-dsl`
	`maven-publish`
}

group = "com.bitsycore"
version = "0.1.0"

repositories {
	mavenCentral()
	google {
		mavenContent {
			includeGroupByRegex(".*google.*")
			includeGroupByRegex(".*android.*")
		}
	}
	gradlePluginPortal()
}

val functionalTest by sourceSets.creating

gradlePlugin {
	testSourceSets(functionalTest)
	plugins {
		register("buildkonfig") {
			id = "com.bitsycore.buildkonfig"
			implementationClass = "com.bitsycore.buildkonfig.BuildKonfigPlugin"
			displayName = "BuildKonfig"
			description = "A Gradle plugin that generates a BuildConfig-like object for Kotlin Multiplatform projects with build-type variant support."
		}
	}
}

configurations[functionalTest.implementationConfigurationName]
	.extendsFrom(configurations.testImplementation.get())

dependencies {
	testImplementation(kotlin("test"))
	"functionalTestImplementation"(gradleTestKit())
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
	description = "Runs functional tests."
	group = "verification"
	testClassesDirs = functionalTest.output.classesDirs
	classpath = functionalTest.runtimeClasspath
}

tasks.check { dependsOn(functionalTestTask) }

publishing {
	repositories {
		mavenLocal()
	}
}
