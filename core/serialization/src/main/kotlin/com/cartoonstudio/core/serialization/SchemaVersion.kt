package com.cartoonstudio.core.serialization

import kotlinx.serialization.Serializable

/**
 * Explicit, testable schema versioning.
 *
 * Every project carries a schema version from the first release. Migrations
 * are registered here and run in order when an older document is opened.
 */
@Serializable
data class SchemaVersion(val major: Int, val minor: Int) : Comparable<SchemaVersion> {

    override fun compareTo(other: SchemaVersion): Int =
        if (major != other.major) major.compareTo(other.major) else minor.compareTo(other.minor)

    override fun toString(): String = "$major.$minor"

    companion object {
        /** Version produced by this build. */
        val CURRENT = SchemaVersion(1, 0)

        /** Oldest version this build can still read. */
        val MINIMUM_SUPPORTED = SchemaVersion(1, 0)

        fun parse(raw: String?): SchemaVersion? {
            val parts = raw?.split('.') ?: return null
            if (parts.size != 2) return null
            val major = parts[0].toIntOrNull() ?: return null
            val minor = parts[1].toIntOrNull() ?: return null
            return SchemaVersion(major, minor)
        }
    }
}

/** Result of inspecting a document's schema version before loading it. */
sealed interface Compatibility {
    data object Current : Compatibility
    data class NeedsMigration(val from: SchemaVersion) : Compatibility
    data class TooNew(val found: SchemaVersion) : Compatibility
    data class TooOld(val found: SchemaVersion) : Compatibility

    companion object {
        fun of(found: SchemaVersion): Compatibility = when {
            found == SchemaVersion.CURRENT -> Current
            found > SchemaVersion.CURRENT -> TooNew(found)
            found < SchemaVersion.MINIMUM_SUPPORTED -> TooOld(found)
            else -> NeedsMigration(found)
        }
    }
}

/** A single, explicit and independently testable document migration step. */
interface Migration {
    val from: SchemaVersion
    val to: SchemaVersion
    fun migrate(document: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject
}

/** Applies registered migrations in sequence until the document is current. */
class MigrationPipeline(private val migrations: List<Migration>) {

    fun migrate(
        document: kotlinx.serialization.json.JsonObject,
        from: SchemaVersion,
    ): kotlinx.serialization.json.JsonObject {
        var current = document
        var version = from
        var guard = 0
        while (version < SchemaVersion.CURRENT && guard++ < MAX_STEPS) {
            val step = migrations.firstOrNull { it.from == version } ?: break
            current = step.migrate(current)
            version = step.to
        }
        return current
    }

    companion object {
        private const val MAX_STEPS = 32

        /** No migrations are needed yet; the pipeline exists so v2 is cheap. */
        val Default = MigrationPipeline(emptyList())
    }
}
