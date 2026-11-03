package com.tutorbridge.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * A colour written into a screen cannot follow the light or dark theme. Screens take their
 * colours from MaterialTheme.colorScheme or from a named colour in ui/theme. The splash
 * screen is the exception for the navy, which has to match its artwork exactly.
 */
class HardcodedColorsTest {

    private val ui = File("src/main/java/com/tutorbridge/app/ui")
    private val literal = Regex("""\bColor\((0x|\d)|\bColor\.(White|Black|Red|Green|Blue|Gray|Yellow|Cyan|Magenta)\b""")
    private val fixedBrand = Regex("""\b(NavySecondary|NavyTint|CrimsonPrimary)\b""")

    @Test
    fun `screens take their colours from the theme`() {
        val found = ui.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.parentFile.name != "theme" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val brand = file.name != "SplashScreen.kt" && !line.startsWith("import") && fixedBrand.containsMatchIn(line)
                    if (literal.containsMatchIn(line) || brand) "${file.name}:${index + 1}: ${line.trim()}" else null
                }
            }
            .toList()
        assertEquals(found.joinToString("\n"), 0, found.size)
    }
}
