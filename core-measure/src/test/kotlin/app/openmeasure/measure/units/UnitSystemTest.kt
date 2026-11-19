package app.openmeasure.measure.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun toggled_fixedUnitsFollowTheirFamily() {
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.CENTIMETERS.toggled())
        assertEquals(UnitSystem.METRIC, UnitSystem.INCHES.toggled())
        assertEquals(UnitSystem.METRIC, UnitSystem.FEET.toggled())
    }

    @Test
    fun family_and_isMetric() {
        assertEquals(UnitSystem.METRIC, UnitSystem.MILLIMETERS.family)
        assertEquals(UnitSystem.METRIC, UnitSystem.METERS.family)
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.INCHES.family)
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.IMPERIAL.family)
        assertTrue(UnitSystem.CENTIMETERS.isMetric)
        assertFalse(UnitSystem.FEET.isMetric)
    }

    @Test
    fun persistedNamesStillParse() {
        assertEquals(UnitSystem.METRIC, UnitSystem.valueOf("METRIC"))
        assertEquals(UnitSystem.IMPERIAL, UnitSystem.valueOf("IMPERIAL"))
    }
}
