# isolated-projects sample

A standalone, multi-module build that proves `kmp-targets` is **Gradle [Isolated
Projects](https://docs.gradle.org/current/userguide/isolated_projects.html)** compatible.

Isolated Projects is the step beyond the configuration cache: it isolates each project's
configuration and **forbids cross-project access** at configuration time (and capturing a `Project`
into task actions). The plugin already follows config-cache-safe patterns — it only ever touches the
project it is applied to. This sample turns that into an **executable guard**: with
`org.gradle.unsafe.isolated-projects=true` (see [`gradle.properties`](./gradle.properties)), if the
plugin ever reaches across projects or captures a `Project`, this build *fails to configure* and CI
goes red.

## Layout

| Module | Convention plugin | Supported | Registered with `KMP_TARGETS=jvm` |
|---|---|---|---|
| `:lib` | `com.rsicarelli.kmptargets.library` | all targets | `jvm` |
| `:app` | `com.rsicarelli.kmptargets.jvm` | `jvm` | `jvm` |

`:app` depends on `:lib` via a `project(":lib")` dependency — a real cross-project edge that Isolated
Projects must wire while keeping each project's configuration isolated. Only the JVM target is
selected, so no Android SDK or Apple toolchain is needed in CI.

Like the other samples this is a **standalone** build that consumes the plugin from `mavenLocal()`,
so publish the plugin first.

## Run

```bash
# from the repo root
task sample:isolated
# or, equivalently:
task publish-local
./gradlew -p samples/isolated-projects build
```
