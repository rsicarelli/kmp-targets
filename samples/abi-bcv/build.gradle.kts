// Sample: kmp-targets × the classic kotlinx binary-compatibility-validator (#81).
//
// A standalone, single-module library that supports jvm + linuxX64 + js and commits BCV's ABI dumps
// under api/. Its point is the selection × ABI interaction: BCV only ever sees the targets the
// current selection registered, so running its tasks under a narrowed lane under-covers. The plugin
// hooks the ABI tasks by name and warns at that moment (strict → fails) — see the README. The
// plugin
// itself never depends on BCV.
plugins {
    kotlin("multiplatform") version libs.versions.kotlin.get()
    alias(libs.plugins.kmpTargets)
    alias(libs.plugins.binaryCompatibilityValidator)
    id("com.ncorti.ktfmt.gradle") version libs.versions.ktfmt.get()
}

// Targets are explicit: this library can build the JVM, a Linux native, and JS. The committed dumps
// under api/ are generated under THIS full selection (see README) — that is the lane that owns
// every
// target's ABI.
kmpTargets { supports { jvm + linuxX64 + js } }

// KLIB ABI validation is opt-in in kotlinx BCV: without it apiDump emits only the JVM
// api/<name>.api
// and the native/js targets get no committed dump at all — so the merged klib dump that makes the
// selection blind spot visible only exists once this is on.
apiValidation { @OptIn(kotlinx.validation.ExperimentalBCVApi::class) klib { enabled = true } }

ktfmt { kotlinLangStyle() }
