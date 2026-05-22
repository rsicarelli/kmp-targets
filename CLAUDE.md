# CLAUDE.md

`kmp-targets` is a multi-module Gradle plugin project. The plugin lives under `:gradle-plugin`; future siblings (e.g. `:cli`) sit at the root level. The sample at `samples/hello-world/` is a **standalone** build that consumes the plugin from `mavenLocal()` — always run `task publish-local` (or `task ci`) before iterating on a sample-affecting change. JDK is pinned via mise (`.mise.toml`, Temurin 23.0.2+7); Gradle via the wrapper (9.5.1). See [`.claude/CLAUDE.md`](./.claude/CLAUDE.md) for Claude-specific working notes.
