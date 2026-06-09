package com.rsicarelli.kmptargets.sample

/** A tiny public API surface so the committed ABI dumps under api/ are non-trivial. */
public class Greeting {
    public fun greet(): String = "Hello from the kmp-targets ABI (built-in abiValidation) sample"
}
