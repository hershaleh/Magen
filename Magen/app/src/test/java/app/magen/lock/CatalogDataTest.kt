package app.magen.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Consistency checks for the data in [Catalog] that blocking relies on. They pin down properties the
 * enforcement code assumes (normalised strings, unique ids, disjoint package sets). Pure JVM, no device.
 */
class CatalogDataTest {

    @Test
    fun categoryIdsAreUniqueAndIndexedById() {
        val ids = Catalog.settings.map { it.id }
        assertEquals("duplicate category ids", ids.size, ids.toSet().size)
        assertEquals(ids.size, Catalog.byId.size)
        for (cat in Catalog.settings) {
            assertTrue(Catalog.byId[cat.id] === cat)
        }
    }

    @Test
    fun everyCategoryHasAnIdAndKeywords() {
        for (cat in Catalog.settings) {
            assertTrue("blank id", cat.id.isNotBlank())
            assertTrue("no keywords for ${cat.id}", cat.keywords.isNotEmpty())
            for (k in cat.keywords) assertTrue("blank keyword in ${cat.id}", k.isNotBlank())
        }
    }

    @Test
    fun everyGroupHasAtLeastOneCategory() {
        val used = Catalog.settings.map { it.group }.toSet()
        for (g in SGroup.values()) assertTrue("empty group $g", g in used)
    }

    @Test
    fun systemRestrictionsLookLikeUserManagerKeys() {
        for (cat in Catalog.settings) {
            val r = cat.restriction ?: continue
            assertTrue("bad restriction '$r' for ${cat.id}", r.startsWith("no_"))
        }
    }

    @Test
    fun adminPagesAreInitialisedBeforeSettingsAndUsedByTheAdminCategory() {
        // Catalog.settings reads adminPages while initialising; if the declaration order broke, this would be empty.
        assertTrue(Catalog.adminPages.isNotEmpty())
        val admin = Catalog.byId["admin"]
        assertNotNull(admin)
        assertTrue(admin!!.keywords.containsAll(Catalog.adminPages))
    }

    @Test
    fun unknownSourcesAreUsedByTheUnknownAppsCategory() {
        assertTrue(Catalog.unknownSources.isNotEmpty())
        val cat = Catalog.byId["unknownapps"]
        assertNotNull(cat)
        assertTrue(cat!!.keywords.containsAll(Catalog.unknownSources))
    }

    @Test
    fun matchSetsAreAlreadyNormalised() {
        // GuardService compares normalised on-screen titles against these, so they must be normalised themselves.
        for (s in Catalog.adminPages) assertEquals(s, Catalog.norm(s))
        for (s in Catalog.unknownSources) assertEquals(s, Catalog.norm(s))
        for (s in Catalog.installWords) assertEquals(s, Catalog.norm(s))
        for (s in Catalog.ownNames) assertEquals(s, Catalog.norm(s))
    }

    @Test
    fun installWordsMeanInstallAndNeverUpdate() {
        assertTrue("install" in Catalog.installWords)
        assertTrue("התקן" in Catalog.installWords)
        assertTrue("התקנה" in Catalog.installWords)
        assertFalse("update" in Catalog.installWords)
        assertFalse("עדכון" in Catalog.installWords)
    }

    @Test
    fun ownNamesCoverEnglishAndHebrew() {
        assertTrue("magen" in Catalog.ownNames)
        assertTrue("מגן" in Catalog.ownNames)
    }

    @Test
    fun packageSetsDoNotOverlap() {
        val sets = listOf(
            "SETTINGS" to Catalog.SETTINGS,
            "INSTALLERS" to Catalog.INSTALLERS,
            "TRANSIENT" to Catalog.TRANSIENT,
            "PLAY" to setOf(Catalog.PLAY),
        )
        for (i in sets.indices) for (j in i + 1 until sets.size) {
            val both = sets[i].second.intersect(sets[j].second)
            assertTrue("${sets[i].first} and ${sets[j].first} share $both", both.isEmpty())
        }
    }

    @Test
    fun catalogPackageSetsDoNotContainMagenItself() {
        val all = Catalog.SETTINGS + Catalog.INSTALLERS + Catalog.TRANSIENT + Catalog.PLAY
        assertFalse("app.magen.lock" in all)
    }
}
