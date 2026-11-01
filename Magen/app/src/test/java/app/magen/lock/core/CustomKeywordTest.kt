package app.magen.lock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure JVM tests for [CustomKeyword.parse], the logic behind adding a custom blocked Settings page. */
class CustomKeywordTest {

    @Test
    fun plainTitleIsKeptAndTrimmed() {
        assertEquals("Wi-Fi", CustomKeyword.parse("Wi-Fi"))
        assertEquals("Wi-Fi", CustomKeyword.parse("   Wi-Fi  "))
    }

    @Test
    fun pathKeepsOnlyTheLastPart() {
        assertEquals("Internet", CustomKeyword.parse("Network & internet > Internet"))
        assertEquals("c", CustomKeyword.parse("a > b > c"))
    }

    @Test
    fun everySupportedSeparatorWorks() {
        for (sep in listOf(">", "›", "»", "→", "<", "‹")) {
            assertEquals("separator $sep", "page", CustomKeyword.parse("first $sep page"))
        }
    }

    @Test
    fun hebrewPathKeepsTheLastPart() {
        assertEquals("אינטרנט", CustomKeyword.parse("רשת ואינטרנט < אינטרנט"))
    }

    @Test
    fun trailingSeparatorFallsBackToTheLastNonEmptyPart() {
        assertEquals("Internet", CustomKeyword.parse("Network > Internet >"))
        assertEquals("Internet", CustomKeyword.parse("Network > Internet > "))
    }

    @Test
    fun emptyInputOrOnlySeparatorsGiveNull() {
        assertNull(CustomKeyword.parse(""))
        assertNull(CustomKeyword.parse("   "))
        assertNull(CustomKeyword.parse(">"))
        assertNull(CustomKeyword.parse(" > › "))
    }

    @Test
    fun longTitleIsCutToTheMaximumLength() {
        val long = "x".repeat(200)
        assertEquals(CustomKeyword.MAX_LENGTH, CustomKeyword.parse(long)!!.length)
    }

    @Test
    fun titleOfExactlyTheMaximumLengthIsKeptWhole() {
        val exact = "y".repeat(CustomKeyword.MAX_LENGTH)
        assertEquals(exact, CustomKeyword.parse(exact))
    }

    @Test
    fun innerWhitespaceIsPreserved() {
        assertEquals("Wi Fi  settings", CustomKeyword.parse("Wi Fi  settings"))
    }
}
