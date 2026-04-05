# buildkonfig-gradle-plugin

A Gradle plugin that generates a `BuildKonfig` Kotlin object at build time — like Android's `BuildConfig`, but for any Kotlin project (JVM, Multiplatform, Android).

Fields can be constant, overridden per build type (debug/release), or scoped to named **dimensions** (e.g. environment, region) with their own variants.

**Plugin ID:** `com.bitsycore.konfig`  
**Version:** `0.2.0`  
**JVM target:** 17

---

## Setup

### 1. Publish to local Maven (until published to a registry)

```bash
./gradlew publishToMavenLocal
```

### 2. Add to your project

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}
```

`build.gradle.kts`:
```kotlin
plugins {
    id("com.bitsycore.konfig") version "0.2.0"
}
```

---

## Basic usage

The plugin auto-detects your project's `group` and `name` to set a default package. The generated file is placed in `build/generated/konfig/` and wired into your source sets automatically.

```kotlin
konfig {
    field("APP_NAME", "My App")
    field("VERSION_CODE", 42)
    field("ENABLE_LOGGING", false).debug(true)
}
```

Generated output (`BuildKonfig.kt`):

```kotlin
public object BuildKonfig {
    const val BUILD_TYPE: String = "release"
    const val MODULE_NAME: String = "my-app"
    const val IS_DEBUG: Boolean = false

    const val APP_NAME: String = "My App"
    const val VERSION_CODE: Int = 42
    const val ENABLE_LOGGING: Boolean = false
}
```

In debug builds (`-Pkonfig.buildtype=DEBUG`), `ENABLE_LOGGING` becomes `inline val ENABLE_LOGGING: Boolean get() = true`.

---

## Configuration reference

### Object settings

```kotlin
konfig {
    objectPackage    = "com.example.app"        // default: derived from group + name or projectName + moduleName
    objectName       = "BuildKonfig"             // default: "BuildKonfig"
    objectVisibility = Visibility.INTERNAL       // default: Visibility.PUBLIC
}
```

### Supported field types

| Kotlin type | Example                                    |
|-------------|--------------------------------------------|
| `String`    | `field("BASE_URL", "https://example.com")` |
| `Boolean`   | `field("FEATURE_X", false)`                |
| `Int`       | `field("TIMEOUT", 30)`                     |
| `Long`      | `field("MAX_SIZE", 1_000_000L)`            |
| `Float`     | `field("RATIO", 1.5f)`                     |
| `Double`    | `field("PI", 3.14159)`                     |

### Build-type overrides

Three equivalent forms for overriding per build type:

```kotlin
konfig {
    // Fluent handle (default + one or both overrides)
    field("BASE_URL", "https://prod.example.com").debug("https://dev.example.com")

    // Scope blocks (build type is fixed — field() returns Unit, no chaining)
    debug   { field("MOCK_API", true) }
    release { field("MOCK_API", false) }
}
```

> `field()` inside `debug {}` / `release {}` blocks intentionally returns `Unit` — the build type is already fixed by the enclosing scope, so `.debug()` / `.release()` chaining is impossible by design.

---

## Dimensions

Dimensions let you select a named variant at build time (e.g. `env=prod`, `env=dev`).  
Each active dimension generates a nested object inside `BuildKonfig`.

```kotlin
konfig {
    dimension("env", defaultTo = "prod") {
        common {
            field("TIMEOUT", 30)
            debug { field("TIMEOUT", 5) }
        }
        variant("prod") {
            field("BASE_URL", "https://prod.example.com")
            field("ANALYTICS", true)
        }
        variant("dev") {
            field("BASE_URL", "https://dev.example.com")
            field("ANALYTICS", false)
        }
    }
}
```

Generated output (with `env=dev`, debug build):

```kotlin
public object BuildKonfig {
    const val BUILD_TYPE: String = "debug"
    // ...

    public object Env /*env*/ {
        const val VARIANT: String = "dev"
        inline val TIMEOUT: Boolean get() = 5   // from common {}, debug override
        const val BASE_URL: String = "https://dev.example.com"
        const val ANALYTICS: Boolean = false
    }
}
```

### `common {}` block

Fields declared in `common {}` act as fallbacks for all variants. A variant field with the same name takes precedence over the common field.

### Custom object name

```kotlin
dimension("env", objectNameOverride = "Environment", defaultTo = "prod") { ... }
// generates: object Environment { ... }
```

If no override is given, the object name is derived from the dimension name via CamelCase conversion (`my-env` → `MyEnv`).

---

## Variant selection

Variants are resolved in priority order:

| Priority | Source                   | Example                                  |
|----------|--------------------------|------------------------------------------|
| 1        | Gradle property          | `-Pkonfig.dimension.env=dev`             |
| 2        | `konfig.properties` file | `konfig.dimension.env=dev`               |
| 3        | Task-name detection      | Running `assembleDevDebug` matches `dev` |
| 4        | `defaultTo` in DSL       | `dimension("env", defaultTo = "prod")`   |
| —        | Omitted silently         | No variant → no nested object generated  |

### `konfig.properties` file

Place a `konfig.properties` file in your project directory:

```properties
konfig.dimension.env=dev
```

This file is tracked as a task input — changing it invalidates the build cache.

---

## Build-type detection

Build type is resolved in priority order:

| Priority | Source              | Example                           |
|----------|---------------------|-----------------------------------|
| 1        | Explicit property   | `-Pkonfig.buildtype=DEBUG`        |
| 2        | Task-name detection | Running `assembleDebug` → `DEBUG` |
| —        | Default             | `RELEASE`                         |

---

## Gradle properties

| Property                                    | Effect                                           |
|---------------------------------------------|--------------------------------------------------|
| `-Pkonfig.buildtype=DEBUG\|RELEASE`         | Forces build type                                |
| `-Pkonfig.dimension.<name>=<variant>`       | Selects a dimension variant                      |
| `-Pkonfig.force`                            | Disables UP-TO-DATE checks — task always re-runs |
| `-Pkonfig.android.buildtypedetection=false` | Disables task-name build-type detection          |
| `-Pkonfig.android.flavordetection=false`    | Disables task-name dimension-variant detection   |

### `konfig.force`

Forces the `generateKonfig` task to re-run on every build, bypassing Gradle's UP-TO-DATE and build-cache checks. Useful when generating a release build for a client or diagnosing cache issues.

```bash
./gradlew generateKonfig -Pkonfig.force
./gradlew assembleRelease -Pkonfig.force
```

The flag is presence-based — any value (or no value) enables it.

---

## Accessing generated values

```kotlin
import com.example.app.BuildKonfig

println(BuildKonfig.BUILD_TYPE)       // "debug" or "release"
println(BuildKonfig.IS_DEBUG)         // true (debug) or false (release)
println(BuildKonfig.Env.BASE_URL)     // dimension field
println(BuildKonfig.Env.VARIANT)      // "dev" or "prod"
```

---

## Using Gradle providers as field values

Lazy `Provider<T>` values are supported — useful for reading Gradle properties or environment variables:

```kotlin
konfig {
    field("API_KEY", providers.gradleProperty("myapp.apiKey"))
    field("CI_BUILD", providers.environmentVariable("CI").map { it.toBoolean() })
}
```

> Do not call `System.getenv()` or `project.findProperty()` directly inside `field()` — these bypass the Provider API and break configuration cache.

---

## Auto source-set wiring

The generated directory (`build/generated/konfig/`) is automatically added as a Kotlin source set for:

- `org.jetbrains.kotlin.multiplatform` → `commonMain`
- `org.jetbrains.kotlin.jvm` → `main`
- `org.jetbrains.kotlin.android` → `main`
- `com.android.application` / `com.android.library` → `main`

The `generateKonfig` task is automatically wired as a dependency of all `compileKotlin*` and `sourcesJar` tasks.

---

## Build

```bash
# Build and publish to local Maven
./gradlew publishToMavenLocal

# Run all tests
./gradlew check

# Run only functional tests
./gradlew functionalTest

# Run a specific functional test
./gradlew functionalTest --tests "*dimension with defaultTo*"
```
