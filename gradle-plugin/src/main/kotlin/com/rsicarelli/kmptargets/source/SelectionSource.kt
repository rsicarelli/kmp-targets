package com.rsicarelli.kmptargets.source

/**
 * A source that can produce a raw `kmptargets.targets` string (e.g. from a Gradle property, an
 * environment variable, or a `local.properties` file).
 *
 * Implementations return `null` to mean **absent**; an empty string means **present but blank**
 * (the parser will treat that as "use the default selection" and emit a warning). The distinction
 * matters for composition: a `null` source defers to the next source in priority order; an explicit
 * empty does not.
 *
 * This is the isolation point for future YAML/TOML/JSON sources — add a new implementation and
 * compose it with [composeSelectionSources].
 */
public fun interface SelectionSource {

    public fun read(): String?
}
