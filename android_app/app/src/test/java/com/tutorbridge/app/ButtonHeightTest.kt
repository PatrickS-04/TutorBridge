package com.tutorbridge.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A button label in Afrikaans or isiZulu is often longer than the English one and wraps
 * onto a second line. A fixed `.height(48.dp)` would cut that second line off, so
 * buttons give a minimum height instead: `.heightIn(min = 48.dp)`.
 */
class ButtonHeightTest {

    /** Unit tests run from the module folder, `android_app/app`. */
    private val ui = File("src/main/java/com/tutorbridge/app/ui")

    private val buttonStart = Regex("""\b(Button|OutlinedButton|TextButton)\(""")
    private val fixedHeight = Regex("""\.height\(\d+\.dp\)""")

    @Test
    fun `buttons grow with their label instead of having a fixed height`() {
        val offenders = ui.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            val lines = file.readLines()
            lines.indices.filter { i ->
                !lines[i].trimStart().startsWith("import") && buttonStart.containsMatchIn(lines[i]) &&
                    lines.subList(i, minOf(i + 6, lines.size)).any { !it.contains("Spacer(") && fixedHeight.containsMatchIn(it) }
            }.map { "${file.name}:${it + 1}" }
        }.toList()
        assertEquals("fixed-height buttons: $offenders", emptyList<String>(), offenders)
    }
}
