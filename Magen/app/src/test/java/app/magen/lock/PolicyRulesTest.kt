package app.magen.lock

import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyRulesTest {
    @Test
    fun sharedRestrictionStaysEnabledWhenAnyMappedScreenIsSelected() {
        val result = PolicyRules.groupedRestrictionStates(
            listOf(
                "network" to "no_config_wifi",
                "wifi" to "no_config_wifi",
                "mobile" to "no_config_mobile_networks",
            ),
            setOf("wifi"),
        )

        assertEquals(
            mapOf(
                "no_config_wifi" to true,
                "no_config_mobile_networks" to false,
            ),
            result,
        )
    }

    @Test
    fun noSelectionDisablesEveryMappedRestriction() {
        val result = PolicyRules.groupedRestrictionStates(
            listOf("wifi" to "no_config_wifi", "network" to "no_config_wifi"),
            emptySet(),
        )

        assertEquals(mapOf("no_config_wifi" to false), result)
    }

    @Test
    fun settingsWithoutSystemRestrictionAreIgnored() {
        val result = PolicyRules.groupedRestrictionStates(
            listOf("wifi" to "no_config_wifi", "security" to null),
            setOf("security"),
        )

        assertEquals(mapOf("no_config_wifi" to false), result)
    }

    @Test
    fun unknownSelectedIdsDoNotEnableUnrelatedRestrictions() {
        val result = PolicyRules.groupedRestrictionStates(
            listOf("wifi" to "no_config_wifi"),
            setOf("not-a-real-setting"),
        )

        assertEquals(mapOf("no_config_wifi" to false), result)
    }
}
