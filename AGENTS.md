# AGENTS.md

This file provides guidance to AI agents (Claude, Copilot, Codex, etc.) working in this repository.

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
- Use `Class<T>` (`.javaObjectType`) instead of `KClass<T>` — Kotlin's `KClass` uses `SoftReference` internally which Gradle can't serialize.
- Use `gradlePropertiesPrefixedBy()` to read groups of properties — **note:** in Gradle 9.x this returns full property names as keys (prefix is NOT stripped), so always check both `dimProps["env"]` and `dimProps["konfig.dimension.env"]`.
- All DSL field values are wrapped in `Provider<T>` from the start — literals via `constantProvider(value)` (a hand-written `ConstantProvider<T>`), external values via `providers.gradleProperty()` / `providers.environmentVariable()` etc. **Never store `ProviderFactory` anywhere in the DSL object graph** — it is not config-cache serializable.
- `forceRegen` (`konfig.force` property) is evaluated eagerly at configuration time as a plain `Boolean` via `providers.gradleProperty("konfig.force").isPresent` — not inside a provider lambda — so the value is captured by value and is config-cache safe.

**Dimension data in task inputs** uses flat-map encoding (`"<dimName>|<fieldName>"` as map keys) in `MapProperty<String, String>` rather than a managed-type `ListProperty`. Values are type-encoded as `"TYPE:rawValue"` (e.g. `"String:hello"`, `"Int:42"`). This collapses 6 separate per-type maps down to one per scope and avoids Gradle's `@Nested` managed-type restrictions.

### DSL design

- **`@KonfigDsl` / `@DslMarker`** is applied to all DSL scope classes to prevent accidental scope leakage.
- **`ConstantProvider<T>`** wraps literal values — no `ProviderFactory` anywhere in the DSL object graph.
- **`field()` at the top level** returns `FieldHandle<T>` which exposes `.debug(value)` and `.release(value)`, both returning `Unit` — chaining beyond the first call is intentionally impossible.
- **`debug {}` / `release {}` scope blocks** use `BuildTypedFieldDeclScope` as receiver — `field()` inside these returns `Unit`, since the build type is already fixed by the enclosing scope.
- **`common {}` block in `DimensionConfig`** — shared fallback fields for all variants; merged in plugin with variant fields taking precedence.
- **Plain `var` properties on `KonfigExtension`** — `objectPackage`, `objectName`, `objectVisibility` are user-facing `var` properties backed by internal `Property<T>` (`objectPackageProp`, `objectNameProp`, `objectVisibilityProp`) used for lazy task wiring.

### Resolution priority for dimensions

1. Explicit Gradle property: `-Pkonfig.dimension.<name>=<variant>`
2. `konfig.properties` file in the project directory: `konfig.dimension.<name>=<variant>`
3. Task-name detection (variant name substring match, if not disabled by `konfig.android.flavordetection=false`)
4. `defaultTo` fallback declared in DSL
5. **Omitted silently** if none of the above — no crash, dimension object not generated

`resolveWithSource()` in `KonfigPlugin` returns a tab-separated `"<TAG>\t<variant>\t<reason>"` string for every dimension. This is stored as a task input (`dimensionResolutionLog`) so the task action can emit structured lifecycle/warning/error log messages without re-running resolution logic.

### Gradle properties understood by the plugin

| Property                              | Effect                                                                           |
|---------------------------------------|----------------------------------------------------------------------------------|
| `-Pkonfig.buildtype=DEBUG\|RELEASE`   | Forces build type; falls back to task-name detection then RELEASE                |
| `-Pkonfig.dimension.<name>=<variant>` | Selects a dimension variant explicitly                                           |
| `-Pkonfig.force`                      | Disables UP-TO-DATE checks — task always re-runs (any value or bare flag works)  |
| `-Pkonfig.android.buildtypedetection=false` | Disables task-name build-type detection                                    |
| `-Pkonfig.android.flavordetection=false`    | Disables task-name dimension variant detection                             |

### File map

| File                    | Role                                                                                                            |
|-------------------------|-----------------------------------------------------------------------------------------------------------------|
| `KonfigPlugin.kt`       | Entry point — wires providers, registers task, auto-wires source sets, hooks compile tasks                      |
| `KonfigExtension.kt`    | DSL (`konfig { }`) — top-level `field()`, `debug {}`, `release {}`, and `dimension()` functions                 |
| `DimensionConfig.kt`    | DSL node for a dimension — holds variants, `common {}` block, `objectNameOverride`, `defaultVariant`            |
| `VariantConfig.kt`      | DSL node for a variant or common block — `field()` returns `FieldHandle<T>`; `debug {}`/`release {}` supported  |
| `FieldHandle.kt`        | Fluent handle returned by top-level `field()` — `.debug(value)` / `.release(value)` return `Unit`              |
| `BuildTypedFieldDeclScope.kt` | Receiver for `debug {}`/`release {}` blocks — `field()` returns `Unit`, no chaining possible            |
| `FieldConfig.kt`        | Single typed field — holds default `Provider<T>?` and per-`BuildType` overrides; `resolve()` returns `Provider<T>?` |
| `GenerateKonfigTask.kt` | `@CacheableTask` — validates inputs, logs detection results, writes the `.kt` file                              |
| `BuildType.kt`          | `enum` with regex-based task-name detection                                                                     |
| `Visibility.kt`         | `PUBLIC` / `INTERNAL` enum                                                                                      |
| `KonfigDsl.kt`          | `@DslMarker` annotation applied to all DSL scope classes                                                        |

> Note: `FieldHandle` and `BuildTypedFieldDeclScope` are defined inside `VariantConfig.kt`, not separate files.

### Plugin metadata

- **Plugin ID:** `com.bitsycore.konfig`
- **Group:** `com.bitsycore`
- **Version:** set via `konfig.version` in `gradle.properties` (currently `0.2.0`)
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
