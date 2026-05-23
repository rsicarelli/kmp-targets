# Claude working notes — kmp-targets

## Repo shape

This is a **multi-module Gradle build**:

- `:gradle-plugin` — the core plugin (`com.rsicarelli.kmptargets`) + the `KmpTargets` entry point. Source under `gradle-plugin/src/`.
- `:convention-plugins` — the per-module convention plugins (`com.rsicarelli.kmptargets.{library,mobile,apple,jvm,web}`), thin wrappers over `KmpTargets.applyConvention`. Depends on `:gradle-plugin` (api) + KGP (implementation, so it can apply KGP for consumers).
- Future: `:cli` — companion CLI for driving target selection from outside Gradle. Reserved as a sibling subproject.

Do **not** collapse into a single-module layout. The structure keeps the dependency-light core publishable on its own and makes adding siblings painless.

## Dev loop

The sample at `samples/hello-world/` is **standalone** — it does not `includeBuild` the plugin source. It consumes the plugin from `~/.m2/repository`. So when iterating on plugin code that should be exercised by the sample:

```bash
task publish-local && task sample
# or just:
task ci
```

## Gradle invocations

Now that more than one module exists, the Taskfile uses root-level aggregating invocations
(`./gradlew test` / `build` / `check` / `ktfmtFormat` / `publishToMavenLocal`) which run across every
module. Prefer `task build` / `task test` / `task dod`. Prefix a single module
(`:gradle-plugin:test`) only when you deliberately want to scope to it.

## Tool chain

- JDK: pinned via mise (`.mise.toml`, Temurin 23.0.2+7). Run `mise install` after pulling.
- Gradle: pinned via wrapper (9.5.1).
- Task: pinned via mise (`.mise.toml`, latest `task` from go-task/task).
- Kotlin: 2.3.21 (via `gradle/libs.versions.toml`).

If `mise install` doesn't pick up the config, run `mise trust .` first.

## Testing convention

GIVEN-WHEN-THEN naming for test functions:

```kotlin
@Test
fun `given X when Y then Z`() { ... }
```

This is enforced by convention, not tooling. Keep it.

## Configuration cache

Gradle 9.5.1 runs configuration cache by default (it's also set in `gradle.properties`). When writing tasks, **never capture `Project` references inside task action closures (`doLast` / `doFirst`)** — pull primitive values out at configuration time:

```kotlin
// Bad
target.tasks.register("foo") { task ->
    task.doLast { println(target.path) }  // captures Project — fails config cache
}

// Good
val projectPath = target.path
target.tasks.register("foo") { task ->
    task.doLast { println(projectPath) }
}
```

The hello-world plugin demonstrates the correct pattern.

## Bootstrap deferred items

The README "Status" section and the bootstrap plan list features explicitly out of scope for the initial release (real selector logic, Maven Central publishing, mkdocs, compat matrices, lint/format tooling, etc.). **Do not silently implement them.** Each is a separate PR with its own scope.
