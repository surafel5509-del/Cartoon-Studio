package com.cartoonstudio.core.serialization

import kotlinx.serialization.json.Json

/**
 * Canonical JSON configuration for every persisted Cartoon Studio document.
 *
 * - `ignoreUnknownKeys` lets a project written by a newer build still open in
 *   an older one, as required by the project format rules.
 * - `encodeDefaults` keeps files self-describing and diff friendly.
 * - `explicitNulls = false` keeps optional data out of the file entirely.
 */
object StudioJson {

    /** Human readable form used for `project.json` and `manifest.json`. */
    val pretty: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = false
        allowStructuredMapKeys = true
        classDiscriminator = "type"
    }

    /** Compact form used for caches, autosave snapshots and clipboard payloads. */
    val compact: Json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        allowStructuredMapKeys = true
        classDiscriminator = "type"
    }
}
