package app.openmeasure.measure.units

/** Display unit system for lengths. Pure Kotlin, no Android types. */
enum class UnitSystem {
    METRIC,
    IMPERIAL;

    /** The other unit system. */
    fun toggled(): UnitSystem = when (this) {
        METRIC -> IMPERIAL
        IMPERIAL -> METRIC
    }
}

private val IMPERIAL_REGIONS = setOf("US", "LR", "MM")

/**
 * Default unit system for an ISO 3166-1 alpha-2 country code.
 * Returns IMPERIAL for US, LR (Liberia) and MM (Myanmar); METRIC otherwise,
 * including null or blank input.
 */
fun defaultSystemForRegion(countryCode: String?): UnitSystem {
    val code = countryCode?.trim()?.uppercase() ?: return UnitSystem.METRIC
    return if (code in IMPERIAL_REGIONS) UnitSystem.IMPERIAL else UnitSystem.METRIC
}
