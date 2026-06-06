# Samples

Two standalone sample builds live in the repo, consuming the plugin exactly as an external project would. Both share the root version catalog and resolve the plugin by version (mavenLocal in the dev loop, Maven Central for consumers).

## hello-world — the multi-module showcase

[`samples/hello-world`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world) is a heterogeneous multi-module project — each module demonstrates one shape:

| Module | Declares | Demonstrates |
|---|---|---|
| [`shared-core`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/shared-core) | `supports { all }` | the opt-in "build whatever is selected" module |
| [`feature-mobile`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/feature-mobile) | `supports { mobile }` | a mobile-only feature; selections outside `mobile` leave it empty (no AGP in the sample, so iOS leaves are its registrable shape) |
| [`jvm-tools`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/jvm-tools) | `supports { jvm }` | a JVM-only tooling module |
| [`core-and-apple`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/core-and-apple) | `supports { apple + jvm }` | preset + leaf composition |
| [`escape-hatch-dsl`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/escape-hatch-dsl) | `supports { jvm + linuxX64 }` | mixing the DSL with raw `kotlin { }` target configuration |
| [`desktop-named`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/desktop-named) | `targetName(jvm, "desktop")` + `supports { jvm + iosArm64 }` | the [JVM rename](../user-guide/jvm-rename.md): `src/desktopMain`, `compileKotlinDesktop` |
| [`pinned-intermediates`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/pinned-intermediates) | `supports { appleMobile }`, collapse off | [no-collapse mode](../user-guide/no-collapse-mode.md): `iosMain` survives a single-leaf selection |
| [`eager-conventions`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/eager-conventions) | applies `kmptargets.module` | the [convention-plugin pattern](../user-guide/build-logic.md) with an `onRegistered` regression gate (`verifyEagerTargets`) |
| [`legacy-default`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/legacy-default) | `supports { all }`, template off | opting out of the [minimal hierarchy](../user-guide/hierarchy-template.md) — KGP's default tree, side by side |
| [`plain-kmp`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/plain-kmp) | *no kmp-targets* | the non-interference proof: a module without the plugin is untouched |
| [`build-logic`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/hello-world/build-logic) | — | the sample's own convention build: `kmptargets.module` plugin, [`KmpModule.kt`](https://github.com/rsicarelli/kmp-targets/blob/main/samples/hello-world/build-logic/src/main/kotlin/KmpModule.kt) is the canonical `onRegistered` consumer |

Drive it like CI does:

```bash
./gradlew -p samples/hello-world build "-Pkmptargets.targets=jvm,iosArm64"
./gradlew -p samples/hello-world :shared-core:kmpTargetsInfo -q
```

## isolated-projects — the compatibility gate

[`samples/isolated-projects`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/isolated-projects) is a two-module build ([`lib`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/isolated-projects/lib) supports `all`, [`app`](https://github.com/rsicarelli/kmp-targets/tree/main/samples/isolated-projects/app) supports `jvm` and depends on `lib`) running with **Gradle Isolated Projects enabled**. `lib` carries a `verifyIsolatedConfiguration` regression gate, so the plugin's Isolated Projects compatibility is exercised, not claimed.

## CI runs them for real

[`ci.yml`](https://github.com/rsicarelli/kmp-targets/blob/main/.github/workflows/ci.yml) builds both samples on every push; [`sample-matrix.yml`](https://github.com/rsicarelli/kmp-targets/blob/main/.github/workflows/sample-matrix.yml) builds hello-world per host with host-appropriate selections — the [CI matrix](../user-guide/ci-matrix.md) pattern, exercised.
