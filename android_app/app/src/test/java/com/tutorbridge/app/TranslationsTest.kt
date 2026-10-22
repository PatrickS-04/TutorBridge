package com.tutorbridge.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

// Reference: Android Developers. 2026. Localize your app. [Online].
//   Available at: https://developer.android.com/guide/topics/resources/localization [Accessed 21 September 2026].
/**
 * The app is offered in English, Afrikaans and isiZulu. Text added in English and
 * missing from the other two shows up in English inside a translated screen, which
 * looks like a bug to the person reading it.
 *
 * Translations are being added feature by feature, so for now some English text is
 * knowingly untranslated. [knownUntranslated] lists it, and the test insists the list
 * is exactly right in both directions:
 *
 *  - new English text that is not translated and not on the list fails - either
 *    translate it, or add it to the list on purpose;
 *  - text that has been translated but is still on the list fails - take it off.
 *
 * So the list can only shrink without somebody choosing otherwise, and an empty list
 * means the app is fully translated.
 *
 * Strings live in one file per feature (strings.xml for the app itself, then
 * strings_settings.xml, strings_offline.xml, strings_time.xml...) in each of values,
 * values-af and values-zu. Android merges them; this test reads them all.
 */
class TranslationsTest {

    /** Unit tests run from the module folder, `android_app/app`. */
    private val res = File("src/main/res")

    /** Configuration, not text anyone reads. */
    private val notTranslatable = setOf("google_web_client_id")

    /** English text with no Afrikaans or isiZulu yet. Shrinks as each feature is translated. */
    private val knownUntranslated = setOf(
        // strings.xml
        "notification_channel_name", "notification_channel_description",
        // strings_time.xml
        "time_just_now", "time_yesterday", "time_minutes_ago", "time_hours_ago", "time_days_ago",
        // strings_offline.xml
        "force_offline_label", "offline_banner_network", "offline_banner_forced", "offline_showing_saved",
        "pending_sync", "failed_sync", "synced_changes", "needs_connection", "saved_offline",
        "waiting_to_send", "sending", "waiting_to_sync", "sync_status_title", "sync_now", "retry_failed",
        "discard", "sync_all_clear", "sync_kind_message", "sync_kind_request", "sync_kind_notes",
        "sync_kind_settings", "sync_state_failed", "unsent_warning_title", "sign_out_anyway",
        "cancel_action", "error_offline", "error_unreachable", "unsent_warning_body", "delete_unsent_warning"
    )

    private data class Entry(val name: String, val text: String)

    /** Every string and plural in every `strings*.xml` of one resource folder. */
    private fun load(folder: String): Map<String, Entry> {
        val entries = mutableMapOf<String, Entry>()
        val files = File(res, folder).listFiles { file -> file.name.startsWith("strings") && file.extension == "xml" }
            ?: emptyArray()
        for (file in files.sortedBy { it.name }) {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val strings = doc.getElementsByTagName("string")
            for (i in 0 until strings.length) {
                val node = strings.item(i)
                val name = node.attributes.getNamedItem("name").nodeValue
                entries[name] = Entry(name, node.textContent)
            }
            val plurals = doc.getElementsByTagName("plurals")
            for (i in 0 until plurals.length) {
                val node = plurals.item(i)
                val name = node.attributes.getNamedItem("name").nodeValue
                val items = node.childNodes
                val quantities = (0 until items.length).map { items.item(it) }.filter { it.nodeName == "item" }
                entries[name] = Entry(name, quantities.joinToString(" | ") { it.textContent })
            }
        }
        return entries
    }

    private val english = load("values")
    private val afrikaans = load("values-af")
    private val isizulu = load("values-zu")

    private fun placeholders(text: String) =
        Regex("%(\\d+\\$)?[ds]").findAll(text).map { it.value }.toSortedSet()

    private fun untranslated(translation: Map<String, Entry>) = english.keys - translation.keys - notTranslatable

    @Test
    fun `the resource folders are where the test expects them`() {
        assertTrue("run from the app module", english.isNotEmpty() && afrikaans.isNotEmpty() && isizulu.isNotEmpty())
    }

    @Test
    fun `the Afrikaans gaps are exactly the ones on the list`() {
        val actual = untranslated(afrikaans)
        assertEquals("not translated and not on the list: ${actual - knownUntranslated}", emptySet<String>(), actual - knownUntranslated)
        assertEquals("on the list but now translated, remove them: ${knownUntranslated - actual}", emptySet<String>(), knownUntranslated - actual)
    }

    @Test
    fun `the isiZulu gaps are exactly the ones on the list`() {
        val actual = untranslated(isizulu)
        assertEquals("not translated and not on the list: ${actual - knownUntranslated}", emptySet<String>(), actual - knownUntranslated)
        assertEquals("on the list but now translated, remove them: ${knownUntranslated - actual}", emptySet<String>(), knownUntranslated - actual)
    }

    @Test
    fun `every string on the list really exists in English`() {
        assertEquals(emptySet<String>(), knownUntranslated - english.keys)
    }

    @Test
    fun `no translation is left over for a string that no longer exists`() {
        assertEquals(emptySet<String>(), afrikaans.keys - english.keys)
        assertEquals(emptySet<String>(), isizulu.keys - english.keys)
    }

    @Test
    fun `translations keep the placeholders the English has`() {
        for ((language, strings) in listOf("af" to afrikaans, "zu" to isizulu)) {
            for ((name, entry) in strings) {
                val expected = english[name]?.let { placeholders(it.text) } ?: continue
                assertEquals("$name ($language) must keep $expected", expected, placeholders(entry.text))
            }
        }
    }

    @Test
    fun `no translation is empty`() {
        for ((language, strings) in listOf("af" to afrikaans, "zu" to isizulu)) {
            for ((name, entry) in strings) {
                assertTrue("$name ($language) is empty", entry.text.isNotBlank())
            }
        }
    }
}
