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

                public data object X64 : Ios {
                    override val id: String = "iosX64"
                    override val aliases: Set<String> = setOf("ios-x64")
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

            public sealed interface Watchos : Apple {

                public data object Arm64 : Watchos {
                    override val id: String = "watchosArm64"
                    override val aliases: Set<String> = setOf("watchos-arm64")
                }

                public data object Arm32 : Watchos {
                    override val id: String = "watchosArm32"
                    override val aliases: Set<String> = setOf("watchos-arm32")
                }

                public data object X64 : Watchos {
                    override val id: String = "watchosX64"
                    override val aliases: Set<String> = setOf("watchos-x64")
                }

                public data object SimulatorArm64 : Watchos {
                    override val id: String = "watchosSimulatorArm64"
                    override val aliases: Set<String> =
                        setOf("watchos-sim-arm64", "watchos-simulator-arm64")
                }

                public data object DeviceArm64 : Watchos {
                    override val id: String = "watchosDeviceArm64"
                    override val aliases: Set<String> = setOf("watchos-device-arm64")
                }
            }

            public sealed interface Tvos : Apple {

                public data object Arm64 : Tvos {
                    override val id: String = "tvosArm64"
                    override val aliases: Set<String> = setOf("tvos-arm64")
                }

                public data object X64 : Tvos {
                    override val id: String = "tvosX64"
                    override val aliases: Set<String> = setOf("tvos-x64")
                }

                public data object SimulatorArm64 : Tvos {
                    override val id: String = "tvosSimulatorArm64"
                    override val aliases: Set<String> =
                        setOf("tvos-sim-arm64", "tvos-simulator-arm64")
                }
            }
        }

        public sealed interface Linux : Native {

            public data object X64 : Linux {
                override val id: String = "linuxX64"
                override val aliases: Set<String> = setOf("linux-x64")
            }

            public data object Arm64 : Linux {
                override val id: String = "linuxArm64"
                override val aliases: Set<String> = setOf("linux-arm64")
            }
        }

        public sealed interface Mingw : Native {

            public data object X64 : Mingw {
                override val id: String = "mingwX64"
                override val aliases: Set<String> = setOf("mingw-x64", "windows-x64")
            }
        }

        public sealed interface AndroidNative : Native {

            public data object Arm32 : AndroidNative {
                override val id: String = "androidNativeArm32"
                override val aliases: Set<String> = setOf("android-native-arm32")
            }

            public data object Arm64 : AndroidNative {
                override val id: String = "androidNativeArm64"
                override val aliases: Set<String> = setOf("android-native-arm64")
            }

            public data object X86 : AndroidNative {
                override val id: String = "androidNativeX86"
                override val aliases: Set<String> = setOf("android-native-x86")
            }

            public data object X64 : AndroidNative {
                override val id: String = "androidNativeX64"
                override val aliases: Set<String> = setOf("android-native-x64")
            }
        }
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
                Native.Apple.Ios.X64,
                Native.Apple.Macos.Arm64,
                Native.Apple.Macos.X64,
                Native.Apple.Watchos.Arm64,
                Native.Apple.Watchos.Arm32,
                Native.Apple.Watchos.X64,
                Native.Apple.Watchos.SimulatorArm64,
                Native.Apple.Watchos.DeviceArm64,
                Native.Apple.Tvos.Arm64,
                Native.Apple.Tvos.X64,
                Native.Apple.Tvos.SimulatorArm64,
                Native.Linux.X64,
                Native.Linux.Arm64,
                Native.Mingw.X64,
                Native.AndroidNative.Arm32,
                Native.AndroidNative.Arm64,
                Native.AndroidNative.X86,
                Native.AndroidNative.X64,
                Web.Js,
                Web.WasmJs,
                Web.WasmWasi,
            )
    }
}
