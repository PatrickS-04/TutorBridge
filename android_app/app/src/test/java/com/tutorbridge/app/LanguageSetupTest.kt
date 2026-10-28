package com.tutorbridge.app

import com.tutorbridge.app.data.model.Language
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The places a language has to be listed must agree. Adding a language in one of them
 * and forgetting another leaves a language that is offered but not installed, or
 * installed but never offered.
 */
class LanguageSetupTest {

    /** Unit tests run from the module folder, `android_app/app`. */
    private val res = File("src/main/res")

    private val appLanguages = Language.entries.map { it.tag }.toSet()

    @Test
    fun `the language list the system settings show matches the languages in the app`() {
        val declared = Regex("""<locale android:name="([\w-]+)"""")
            .findAll(File(res, "xml/locales_config.xml").readText())
            .map { it.groupValues[1] }.toSet()
        assertEquals(appLanguages, declared)
    }

    @Test
    fun `every language has its own resource folder, and no folder is for a language the app lacks`() {
        val folders = res.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }!!
            .map { it.name.removePrefix("values-") }
            .filter { it.length == 2 && it.all(Char::isLowerCase) }
            .toSet()
        assertEquals(appLanguages - "en", folders)
    }

    @Test
    fun `the codes sent to the server are the ones the server accepts`() {
        // backend_api/src/models/enums.ts: enum Locale { EN, ZU, AF }. Keep the two in step.
        assertEquals(setOf("EN", "ZU", "AF"), Language.entries.map { it.serverCode }.toSet())
    }
}
