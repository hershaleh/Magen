package app.magen.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for [Catalog.norm]. They need neither a device nor an emulator. */
class CatalogNormTest {

    @Test
    fun lowercasesLatinText() {
        assertEquals("wi-fi", Catalog.norm("Wi-Fi"))
        assertEquals("device admin apps", Catalog.norm("Device Admin Apps"))
    }

    @Test
    fun leavesHebrewUnchanged() {
        assertEquals("אפליקציות לניהול המכשיר", Catalog.norm("אפליקציות לניהול המכשיר"))
        assertEquals("מגן", Catalog.norm("מגן"))
    }

    @Test
    fun trimsAndCollapsesWhitespace() {
        assertEquals("a b", Catalog.norm("  a   b  "))
        assertEquals("a b", Catalog.norm("a\t\nb"))
    }

    @Test
    fun emptyAndBlankInputGiveEmptyString() {
        assertEquals("", Catalog.norm(""))
        assertEquals("", Catalog.norm("   "))
        assertEquals("", Catalog.norm(" ‎‏"))
    }

    @Test
    fun nonBreakingSpaceBecomesNormalSpace() {
        assertEquals("a b", Catalog.norm("a b"))
    }

    @Test
    fun bidiMarksAtTheEdgesAreRemoved() {
        assertEquals("מגן", Catalog.norm("‏מגן‎"))
        assertEquals("magen", Catalog.norm("‪magen‬"))
        assertEquals("wi-fi", Catalog.norm("⁦Wi-Fi⁩"))
    }

    @Test
    fun bidiMarkInsideTextActsAsASpace() {
        assertEquals("a b", Catalog.norm("a‎b"))
    }

    @Test
    fun unusualHyphensBecomeAsciiHyphen() {
        assertEquals("wi-fi", Catalog.norm("Wi‑Fi"))
        assertEquals("wi-fi", Catalog.norm("Wi‐Fi"))
    }

    @Test
    fun isIdempotent() {
        val samples = listOf("  Wi‑Fi  ", "‏מגן‎", "A  B", "")
        for (s in samples) {
            assertEquals(Catalog.norm(s), Catalog.norm(Catalog.norm(s)))
        }
    }

    @Test
    fun everyCatalogKeywordSurvivesNormalisationAsNonEmptyAndStable() {
        for (cat in Catalog.settings) {
            for (k in cat.keywords) {
                val n = Catalog.norm(k)
                assertTrue("keyword '$k' of ${cat.id} normalises to empty", n.isNotEmpty())
                assertEquals("keyword '$k' of ${cat.id} is not stable", n, Catalog.norm(n))
            }
        }
    }
}
