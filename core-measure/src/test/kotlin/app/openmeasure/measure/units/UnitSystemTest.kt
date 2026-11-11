package app.openmeasure.measure.units

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitSystemTest {

    @Test
    fun defaultSystemForRegion_imperialCountries() {
        assertEquals(UnitSystem.IMPERIAL, defaultSystemForRegion("US"))
        assertEquals(UnitSystem.IMPERIAL, defaultSystemForRegion("lr"))
        assertEquals(UnitSystem.IMPERIAL, defaultSystemForRegion(" MM "))
    }

    @Test
    fun defaultSystemForRegion_otherCountriesAreMetric() {
        assertEquals(UnitSystem.METRIC, defaultSystemForRegion("GB"))
        assertEquals(UnitSystem.METRIC, defaultSystemForRegion("DE"))
        assertEquals(UnitSystem.METRIC, defaultSystemForRegion(""))
        assertEquals(UnitSystem.METRIC, defaultSystemForRegion(null))
    }

    @Test
    fun toggled_switchesBothWays() {
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.METRIC.toggled())
        assertEquals(UnitSystem.METRIC, UnitSystem.IMPERIAL.toggled())
        assertEquals(UnitSystem.METRIC, UnitSystem.METRIC.toggled().toggled())
    }
}
