plugins {
	`kotlin-dsl`
	`maven-publish`
}

group = "com.bitsycore"
version = providers.gradleProperty("konfig.version").get()

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
	withSourcesJar()
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
    add("functionalTestImplementation", gradleTestKit())
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
	description = "Runs functional tests."
	group = "verification"
	testClassesDirs = functionalTest.output.classesDirs
	classpath = functionalTest.runtimeClasspath
}

tasks.check { dependsOn(functionalTestTask) }

// ========================================================
// MARK: Publishing
// ========================================================

fun prop(name: String): String? =
    providers.gradleProperty(name).orNull
        ?: System.getenv(name.replace('.', '_').uppercase())

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            groupId    = project.group.toString()
            artifactId = providers.gradleProperty("konfig.artifactId").get()
            version    = project.version.toString()

            pom {
                name        = providers.gradleProperty("konfig.pom.name").get()
                description = providers.gradleProperty("konfig.pom.description").get()
                url         = providers.gradleProperty("konfig.pom.url").get()

                licenses {
                    license {
                        name = "MIT License"
                        url  = "https://opensource.org/licenses/MIT"
                    }
                }

                developers {
                    developer {
                        id   = providers.gradleProperty("konfig.pom.developer.id").get()
                        name = providers.gradleProperty("konfig.pom.developer.name").get()
                        url  = providers.gradleProperty("konfig.pom.developer.url").get()
                    }
                }

                scm {
                    connection          = providers.gradleProperty("konfig.pom.scm.connection").get()
                    developerConnection = providers.gradleProperty("konfig.pom.scm.developerConnection").get()
                    url                 = providers.gradleProperty("konfig.pom.scm.url").get()
                }
            }
        }
    }

    repositories {
        mavenLocal()

        maven {
            name = "GitHubPackages"
            url  = uri(providers.gradleProperty("konfig.publish.url").get())
            credentials {
                username = prop("gpr.user")
                password = prop("gpr.key")
            }
        }
    }
}
