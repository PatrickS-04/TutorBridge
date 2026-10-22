package com.tutorbridge.app

import com.tutorbridge.app.data.model.Language
import com.tutorbridge.app.data.model.LanguagePlan
import com.tutorbridge.app.data.model.RelativeTime
import com.tutorbridge.app.data.model.reconcileLanguage
import com.tutorbridge.app.data.model.relativeTimeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

// Reference: Android Developers. 2026. Build local unit tests. [Online].
//   Available at: https://developer.android.com/training/testing/local-tests [Accessed 21 September 2026].
/** The three ways the app names a language agree, and what happens when the account and the phone disagree. */
class LanguageTest {

    @Test
    fun `the names the API, Android and the picker use for a language all point at the same one`() {
        for (language in Language.entries) {
            assertEquals(language, Language.fromServerCode(language.serverCode))
            assertEquals(language, Language.fromTag(language.tag))
            assertEquals(language, Language.fromDisplayName(language.displayName))
        }
    }

    @Test
    fun `an unknown server code is English, the default`() {
        assertEquals(Language.ENGLISH, Language.fromServerCode(null))
        assertEquals(Language.ENGLISH, Language.fromServerCode("XX"))
        assertEquals(Language.ZULU, Language.fromServerCode("zu"))
    }

    @Test
    fun `a regional tag is read as its language, and a language the app lacks is not guessed`() {
        assertEquals(Language.AFRIKAANS, Language.fromTag("af-ZA"))
        assertEquals(Language.ZULU, Language.fromTag("zu_ZA"))
        assertNull(Language.fromTag("fr"))
        assertNull(Language.fromTag(null))
    }

    @Test
    fun `the picker offers each language by its own name`() {
        assertEquals(listOf("English", "isiZulu", "Afrikaans"), Language.entries.map { it.displayName })
    }

    @Test
    fun `a language chosen on this phone wins, and the account is brought into line`() {
        val plan = reconcileLanguage(account = Language.ENGLISH, chosenOnPhone = Language.ZULU)
        assertEquals(LanguagePlan(applyOnThisPhone = null, saveToAccount = Language.ZULU), plan)
    }

    @Test
    fun `a choice already matching the account changes nothing`() {
        val plan = reconcileLanguage(account = Language.ZULU, chosenOnPhone = Language.ZULU)
        assertEquals(LanguagePlan(null, null), plan)
    }

    @Test
    fun `a new phone takes the language the account remembers`() {
        val plan = reconcileLanguage(account = Language.AFRIKAANS, chosenOnPhone = null)
        assertEquals(LanguagePlan(applyOnThisPhone = Language.AFRIKAANS, saveToAccount = null), plan)
    }

    @Test
    fun `an English account with nothing chosen leaves the phone's own language alone`() {
        // English is only the default for someone who never picked; applying it would
        // override a phone set to Afrikaans, which Android already honours.
        assertEquals(LanguagePlan(null, null), reconcileLanguage(account = Language.ENGLISH, chosenOnPhone = null))
    }

    @Test
    fun `someone who deliberately chose English here keeps it over an old isiZulu account`() {
        val plan = reconcileLanguage(account = Language.ZULU, chosenOnPhone = Language.ENGLISH)
        assertEquals(LanguagePlan(applyOnThisPhone = null, saveToAccount = Language.ENGLISH), plan)
    }

    // ---- how long ago ----

    private val now = Instant.parse("2026-10-22T12:00:00Z")

    private fun ago(minutes: Long) = relativeTimeOf(now.minusSeconds(minutes * 60).toString(), now)

    @Test
    fun `how long ago is a fact, left for the screen to word`() {
        assertEquals(RelativeTime.JustNow, ago(0))
        assertEquals(RelativeTime.Minutes(5), ago(5))
        assertEquals(RelativeTime.Minutes(59), ago(59))
        assertEquals(RelativeTime.Hours(1), ago(60))
        assertEquals(RelativeTime.Hours(23), ago(23 * 60 + 59))
        assertEquals(RelativeTime.Yesterday, ago(24 * 60))
        assertEquals(RelativeTime.Yesterday, ago(47 * 60))
        assertEquals(RelativeTime.Days(2), ago(48 * 60))
        assertEquals(RelativeTime.Days(10), ago(10 * 24 * 60 + 5))
    }

    @Test
    fun `a time in the future reads as just now, and a missing or broken one as nothing`() {
        assertEquals(RelativeTime.JustNow, relativeTimeOf(now.plusSeconds(300).toString(), now))
        assertNull(relativeTimeOf(null, now))
        assertNull(relativeTimeOf("yesterday-ish", now))
    }
}
