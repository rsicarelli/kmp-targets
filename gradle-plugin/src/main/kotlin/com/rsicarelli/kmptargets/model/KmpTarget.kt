package com.rsicarelli.kmptargets.model

/**
 * Every Kotlin Multiplatform target the plugin knows about is a leaf in this sealed hierarchy.
 *
 * Branches ([Jvm], [Native], [Native.Apple], [Native.Apple.Ios], [Web], ...) are themselves sealed,
 * so a `when` over a branch type is exhaustive — adding a new leaf to a branch forces every call
 * site that pattern-matches on that branch to handle it.
 *
 * [id] is the canonical KGP preset name (e.g. `iosArm64`, `androidTarget`) and is the single source
 * of truth for property strings, target registration, and serialization.
 *
 * [aliases] are case-insensitive alternate spellings accepted by the property parser. They must
 * never collide with another leaf's [id].
 */
public sealed interface KmpTarget {

    public val id: String

    public val aliases: Set<String>

    public sealed interface Jvm : KmpTarget {

        public data object Android : Jvm {
            override val id: String = "androidTarget"
            override val aliases: Set<String> = setOf("android")
        }

        public data object Desktop : Jvm {
            override val id: String = "jvm"
            override val aliases: Set<String> = setOf("desktop")
        }
    }

    public sealed interface Native : KmpTarget {

        public sealed interface Apple : Native {

            public sealed interface Ios : Apple {

                public data object Arm64 : Ios {
                    override val id: String = "iosArm64"
                    override val aliases: Set<String> = setOf("ios-arm64")
                }

                public data object SimulatorArm64 : Ios {
                    override val id: String = "iosSimulatorArm64"
                    override val aliases: Set<String> =
                        setOf("ios-sim-arm64", "ios-simulator-arm64")
                }
            }

            public sealed interface Macos : Apple {

                public data object Arm64 : Macos {
                    override val id: String = "macosArm64"
                    override val aliases: Set<String> = setOf("macos-arm64")
                }

                public data object X64 : Macos {
                    override val id: String = "macosX64"
                    override val aliases: Set<String> = setOf("macos-x64")
                }
            }

            public sealed interface Watchos : Apple

            public sealed interface Tvos : Apple
        }

        public sealed interface Linux : Native

        public sealed interface Mingw : Native

        public sealed interface AndroidNative : Native
    }

    public sealed interface Web : KmpTarget {

        public data object Js : Web {
            override val id: String = "js"
            override val aliases: Set<String> = emptySet()
        }

        public data object WasmJs : Web {
            override val id: String = "wasmJs"
            override val aliases: Set<String> = setOf("wasm-js")
        }

        public data object WasmWasi : Web {
            override val id: String = "wasmWasi"
            override val aliases: Set<String> = setOf("wasm-wasi")
        }
    }

    public companion object {

        public val all: Set<KmpTarget> =
            setOf(
                Jvm.Android,
                Jvm.Desktop,
                Native.Apple.Ios.Arm64,
                Native.Apple.Ios.SimulatorArm64,
                Native.Apple.Macos.Arm64,
                Native.Apple.Macos.X64,
                Web.Js,
                Web.WasmJs,
                Web.WasmWasi,
            )
    }
}
