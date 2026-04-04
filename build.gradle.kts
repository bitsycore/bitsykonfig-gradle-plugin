plugins {
	`kotlin-dsl`
	`maven-publish`
}

group = "com.bitsycore"
version = providers.gradleProperty("konfig.version").get()

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
	compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

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
		register("konfig") {
			id = "com.bitsycore.konfig"
			implementationClass = "com.bitsycore.konfig.KonfigPlugin"
			displayName = "BuildKonfig"
			description = "A Gradle plugin that generates a BuildConfig-like object for Kotlin Multiplatform projects with build-type variant support."
		}
	}
}

configurations[functionalTest.implementationConfigurationName]
	.extendsFrom(configurations.testImplementation.get())

dependencies {
	compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:$embeddedKotlinVersion")
	compileOnly("com.android.tools.build:gradle:8.0.0")
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
