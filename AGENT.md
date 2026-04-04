# CLAUDE.md

This file provides guidance to Agent like Claude Code, Copilit, Codex when working with code in this repository.

## Commands

```bash
# Build & publish to local Maven (primary development loop)
./gradlew publishToMavenLocal

# Run unit tests only
./gradlew test

# Run functional tests (Gradle TestKit — starts real Gradle builds)
./gradlew functionalTest

# Run a single functional test
./gradlew functionalTest --tests "*dimension with defaultTo*"

# Run all checks (test + functionalTest)
./gradlew check

# Force re-run (skip UP-TO-DATE / cache)
./gradlew functionalTest --rerun-tasks
```

> Functional tests are the primary test suite. The unit test file (`KonfigTypeTest.kt`) only covers `BuildType` resolution regex.

## Architecture

### What this plugin does

Generates a Kotlin `object BuildKonfig { ... }` at build time, placed in `build/generated/konfig/`, automatically wired into the consuming project's source sets. Fields can be constant across builds, overridden per build type (debug/release), or scoped to a named **dimension** (e.g. environment, region) each with their own variants.

### Key design constraints

**Configuration cache compatibility** is a hard requirement throughout. This means:
- Never capture `project` inside a `Provider.map {}` or `Provider.zip {}` lambda, and **never access `project` inside a `@TaskAction`** — both break caching. Use only declared `@Input`/`@OutputDirectory` properties inside task actions.
- Use `Class<T>` (`.javaObjectType`) instead of `KClass<T>` — Kotlin's `KClass` uses `SoftReference` internally which Gradle can't serialize
- Use `gradlePropertiesPrefixedBy()` to read groups of properties — **note:** in Gradle 9.x this returns full property names as keys (prefix is NOT stripped), so always check both `dimProps["env"]` and `dimProps["konfig.dimension.env"]`
- All `Provider` chains are wired at configuration time; the task action only reads already-resolved values

**Dimension data in task inputs** uses flat-map encoding (`"<dimName>|<fieldName>"` as map keys) in `MapProperty<String, T>` rather than a managed-type `ListProperty`. This avoids Gradle's `@Nested` managed-type restrictions.

### Resolution priority for dimensions

1. Explicit Gradle property: `-Pkonfig.dimension.<name>=<variant>`
2. Task-name detection (variant name substring match, if not disabled by `konfig.android.flavordetection=false`)
3. `defaultTo` fallback declared in DSL
4. **Omitted silently** if none of the above — no crash, dimension object not generated

`resolveWithSource()` in `KonfigPlugin` returns a tab-separated `"<TAG>\t<variant>\t<reason>"` string for every dimension. This is stored as a task input (`dimensionResolutionLog`) so the task action can emit structured lifecycle/warning/error log messages without re-running resolution logic.

### File map

| File                    | Role                                                                                                           |
|-------------------------|----------------------------------------------------------------------------------------------------------------|
| `KonfigPlugin.kt`       | Entry point — wires providers, registers task, auto-wires source sets, hooks compile tasks                     |
| `KonfigExtension.kt`    | DSL (`konfig { }`) — top-level `field()` and `dimension()` functions                                           |
| `DimensionConfig.kt`    | DSL node for a dimension — holds variants, `objectNameOverride`, `defaultVariant`, computes Kotlin object name |
| `VariantConfig.kt`      | DSL node for a variant — holds `List<FieldConfig<*>>`                                                          |
| `FieldConfig.kt`        | Single typed field with optional `debug()`/`release()` overrides                                               |
| `GenerateKonfigTask.kt` | `@CacheableTask` — validates inputs, logs detection results, writes the `.kt` file                             |
| `BuildType.kt`          | `enum` with regex-based task-name detection and Gradle attribute rules                                         |
| `Visibility.kt`         | `PUBLIC` / `INTERNAL` enum                                                                                     |

Stub files (`VariantFieldConfig.kt`, `VariantDimensionConfig.kt`, `VariantValue.kt`, `DimensionTaskInput.kt`) are empty package declarations left from a previous design — they can be deleted.

### Plugin metadata

- **Plugin ID:** `com.bitsycore.konfig`
- **Group:** `com.bitsycore`
- **Version:** set via `konfig.version` in `gradle.properties`
- **JVM target:** 17 (set via `sourceCompatibility` + `KotlinCompile.compilerOptions.jvmTarget`, no toolchain — avoids requiring a specific JDK installation)
- **AGP dependency:** `compileOnly("com.android.tools.build:gradle:8.0.0")` — never leaked to consumers

### Logging levels used in GenerateKonfigTask

| Output                                              | Level             | When                                    |
|-----------------------------------------------------|-------------------|-----------------------------------------|
| `BUILD_TYPE = release (…reason…)`                   | `lifecycle`       | Always                                  |
| `dim 'env' -> 'dev'  (…reason…)`                    | `lifecycle`       | Active dimension                        |
| `dim 'env' -> skipped (…reason…)`                   | `lifecycle`       | No variant resolved                     |
| `generated BuildKonfig.kt (…)`                      | `lifecycle`       | Always (summary)                        |
| Unknown/ambiguous variant                           | `warn`            | Bad `-P` value or multiple task matches |
| Field/dim details                                   | `info`            | With `--info`                           |
| Config errors (bad `defaultTo`, invalid identifier) | `GradleException` | Fail fast                               |
