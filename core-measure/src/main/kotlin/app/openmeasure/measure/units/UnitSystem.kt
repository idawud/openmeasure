package app.openmeasure.measure.units

/**
 * Display unit for lengths. Pure Kotlin, no Android types.
 *
 * METRIC and IMPERIAL are the automatic choices (cm/m, ft+in). The others pin one unit.
 * Persisted by enum name, so METRIC and IMPERIAL keep their existing stored values.
 */
enum class UnitSystem(val isMetric: Boolean) {
    METRIC(isMetric = true),
    IMPERIAL(isMetric = false),
    MILLIMETERS(isMetric = true),
    CENTIMETERS(isMetric = true),
    METERS(isMetric = true),
    INCHES(isMetric = false),
    FEET(isMetric = false);

    /** The automatic system this unit belongs to: METRIC or IMPERIAL. Drives area, volume and spoken forms. */
    val family: UnitSystem get() = if (isMetric) METRIC else IMPERIAL

    /** The other unit family: metric-family -> IMPERIAL, imperial-family -> METRIC. */
    fun toggled(): UnitSystem = if (isMetric) IMPERIAL else METRIC
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
