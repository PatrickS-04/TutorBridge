package com.tutorbridge.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Reference: Android Developers. 2026. Localize your app. [Online].
//   Available at: https://developer.android.com/guide/topics/resources/localization [Accessed 21 September 2026].
/**
 * Text written straight into a screen can only ever be in English. Translating the app
 * means moving every such line into string resources, and this keeps that from going
 * backwards while it is done: it counts the screen text still written in the code, and
 * fails if the count goes UP. Anyone adding a screen or a label has to use
 * `stringResource(R.string.…)` instead.
 *
 * What counts: a quoted string handed to `Text(`, or given as a `label`, `placeholder`,
 * `title`, `text`, `supportingText` or `contentDescription`, that contains a letter once
 * `$name` and `${…}` placeholders are taken out. A bare number or `"$count"` is not text.
 *
 * It only looks at `ui/`. Messages built in the ViewModel are moved by hand.
 *
 * When the count falls, lower [baseline] to match. The second test insists on it, so the
 * gain cannot be spent on new hard-coded text later.
 */
class HardcodedTextTest {

    /** The most hard-coded screen text allowed. Lower it as screens are translated; never raise it. */
    private val baseline = 0

    /** Unit tests run from the module folder, `android_app/app`. */
    private val ui = File("src/main/java/com/tutorbridge/app/ui")

    private val textCall = Regex("""\bText\(\s*"((?:[^"\\]|\\.)*)"""")
    private val namedArgument = Regex("""\b(?:label|placeholder|title|text|supportingText|contentDescription)\s*=\s*(?:\{\s*)?"((?:[^"\\]|\\.)*)"""")
    private val interpolation = Regex("""\$\{[^}]*}|\$[A-Za-z_][A-Za-z0-9_]*""")

    private fun hasWords(literal: String) = interpolation.replace(literal, "").any { it.isLetter() }

    /** `file:line: "text"` for each literal that counts, so a failure points at the lines to fix. */
    private fun hardcoded(): List<String> {
        val found = mutableListOf<String>()
        ui.walkTopDown().filter { it.isFile && it.extension == "kt" }.sortedBy { it.path }.forEach { file ->
            val source = file.readText()
            // A literal can match both patterns (`label = { Text("x") }`), so count each once, by position.
            val seen = mutableSetOf<Int>()
            for (pattern in listOf(textCall, namedArgument)) {
                for (match in pattern.findAll(source)) {
                    val group = match.groups[1] ?: continue
                    if (!seen.add(group.range.first) || !hasWords(group.value)) continue
                    val line = source.substring(0, group.range.first).count { it == '\n' } + 1
                    found += "${file.name}:$line: \"${group.value}\""
                }
            }
        }
        return found
    }

    @Test
    fun `the folder is where the test expects it`() {
        assertTrue("run from the app module", ui.isDirectory)
    }

    @Test
    fun `no new hard-coded screen text`() {
        val found = hardcoded()
        assertTrue(
            "${found.size} hard-coded strings, allowed $baseline. Use stringResource(R.string.…) for new text. " +
                "The newest ones are at the end:\n" + found.takeLast(15).joinToString("\n"),
            found.size <= baseline
        )
    }

    @Test
    fun `the baseline is lowered when text is moved into resources`() {
        val found = hardcoded().size
        assertTrue(
            "only $found hard-coded strings are left but the baseline still allows $baseline: lower it to $found",
            baseline - found <= SLACK
        )
    }

    private companion object {
        /** A little room, so removing one literal does not force a baseline edit in the same commit. */
        const val SLACK = 5
    }
}
