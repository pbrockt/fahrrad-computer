package com.pbrockt.fahrradcomputer.update

/**
 * Versionsnummer nach SemVer, soweit wir sie brauchen: `1.2.3` oder `1.2.3-alpha.4`.
 * Eine Alpha ist kleiner als das Release mit derselben Nummer: 1.2.0-alpha.3 < 1.2.0.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** null = Release. */
    val alpha: Int? = null,
) : Comparable<AppVersion> {

    val isAlpha: Boolean get() = alpha != null

    override fun compareTo(other: AppVersion): Int = compareValuesBy(
        this, other,
        { it.major }, { it.minor }, { it.patch },
        // Release (kein Alpha-Zaehler) sortiert hinter jede Alpha derselben Nummer.
        { it.alpha ?: Int.MAX_VALUE },
    )

    override fun toString() = "$major.$minor.$patch" + (alpha?.let { "-alpha.$it" } ?: "")

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-alpha\.(\d+))?$""")

        fun parse(text: String): AppVersion? {
            val m = PATTERN.matchEntire(text.trim()) ?: return null
            val (a, b, c, d) = m.destructured
            return AppVersion(a.toInt(), b.toInt(), c.toInt(), d.takeIf { it.isNotEmpty() }?.toInt())
        }
    }
}

enum class UpdateChannel(val label: String) {
    RELEASE("Release"),
    ALPHA("Alpha"),
}
