# Contributing to kmp-targets

Thanks for your interest in `kmp-targets`. The project is in early bootstrap — small, focused contributions are welcome.

## Development setup

Install the tool chain:

- [mise](https://mise.jdx.dev) — pins JDK via `.mise.toml`
- [Task](https://taskfile.dev) — developer task runner

Then provision and verify:

```bash
mise install            # installs Temurin 23.0.2+7
java -version           # should report 23.0.2
task ci                 # runs check + publish-local + sample build
```

Gradle is pinned via the wrapper (9.5.1) — no need to install it yourself.

## Project layout

```
.
├── gradle-plugin/             # the Gradle plugin (com.rsicarelli.kmptargets)
│   ├── src/main/kotlin/       # plugin source
│   └── src/test/kotlin/       # TestKit functional tests
├── samples/
│   └── hello-world/           # standalone sample, consumes plugin via mavenLocal
├── gradle/libs.versions.toml  # version catalog
├── Taskfile.yml               # developer commands
└── .github/workflows/ci.yml   # CI on push/PR
```

This is a **multi-module** Gradle build. The plugin lives under `:gradle-plugin`. Future modules (e.g. `:cli`) sit as siblings under the root.

## Iterating on the plugin against the sample

The sample is **standalone** (it does not `includeBuild` the plugin source). It consumes the plugin from `~/.m2/repository`. So the dev loop is:

```bash
task publish-local           # publishes :gradle-plugin to mavenLocal
task sample                  # runs the sample against the just-published version
```

`task ci` does both for you.

## Testing convention

All tests use **GIVEN-WHEN-THEN** naming:

```kotlin
@Test
fun `given plugin applied when running kmpTargetsHello then prints message`() { ... }
```

This is enforced by convention, not tooling (yet). It makes test failures self-documenting and matches the testing standard in the broader `rsicarelli` open-source ecosystem.

## Pull requests

- Small, single-purpose PRs preferred.
- Include a Test plan checklist describing how you verified the change.
- CI must be green before merge.
- Link related issues.

## Status / roadmap

The bootstrap intentionally ships only a hello-world plugin to validate the build, sample, CI, and publish-local loop. Real selector logic, Maven Central publishing, mkdocs site, and compat matrices land in subsequent PRs. See the `Explicitly NOT in Bootstrap` section of the plan, and the issue tracker.

## License

By contributing, you agree your contributions are licensed under Apache-2.0 (see [LICENSE](./LICENSE)).
