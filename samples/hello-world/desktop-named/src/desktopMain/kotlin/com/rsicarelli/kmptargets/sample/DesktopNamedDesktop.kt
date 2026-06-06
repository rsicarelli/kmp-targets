package com.rsicarelli.kmptargets.sample

// Lives in `src/desktopMain` — the renamed source-set dir the rename exists to preserve. Compiled
// by `compileKotlinDesktop`, proving the custom-named target wires the legacy layout end to end.
fun desktopOnlyGreeting(): String = "Hello from desktopMain (compiled by compileKotlinDesktop)!"
