package com.tutorbridge.app.ui.screens.history

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.tutorbridge.app.R
import com.tutorbridge.app.data.model.LearningHistoryEntryUi
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val PAGE_W = 595 // A4 at 72dpi
private const val PAGE_H = 842
private const val MARGIN = 40f

/**
 * Renders the student's completed sessions as a PDF into the cache and hands it
 * to the share sheet so they can save or send it. Text follows the app language.
 */
fun exportLearningHistory(
    context: Context,
    studentName: String,
    institutionName: String,
    entries: List<LearningHistoryEntryUi>
) {
    val file = try {
        writeHistoryPdf(context, studentName, institutionName, entries)
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.history_pdf_error, e.message.orEmpty()), Toast.LENGTH_LONG).show()
        return
    }

    // Reference: Android Developers. 2025. Setting up file sharing. [Online].
    //   Available at: https://developer.android.com/training/secure-file-sharing/setup-sharing [Accessed 21 September 2026].
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "${context.getString(R.string.history_pdf_title)} - $studentName")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, context.getString(R.string.history_pdf_share)))
}

// Reference: Android Developers. 2026. PdfDocument. [Online].
//   Available at: https://developer.android.com/reference/android/graphics/pdf/PdfDocument [Accessed 21 September 2026].
private fun writeHistoryPdf(
    context: Context,
    studentName: String,
    institutionName: String,
    entries: List<LearningHistoryEntryUi>
): File {
    val title = Paint().apply { textSize = 18f; isFakeBoldText = true }
    val heading = Paint().apply { textSize = 12f; isFakeBoldText = true }
    val body = Paint().apply { textSize = 10f }
    val muted = Paint().apply { textSize = 9f; color = 0xFF666666.toInt() }

    val doc = PdfDocument()
    var pageNumber = 0
    var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNumber).create())
    var y = MARGIN + 10f

    // Unlike the voucher, a venue can be long, so text that does not fit runs onto the next line.
    // ponytail: breaks mid-word; wrap on spaces if long venues look bad.
    fun line(text: String = "", paint: Paint = body, gap: Float = 14f) {
        var rest = text
        do {
            if (y + gap > PAGE_H - MARGIN) {
                doc.finishPage(page)
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNumber).create())
                y = MARGIN + 10f
            }
            val fits = paint.breakText(rest, true, PAGE_W - 2 * MARGIN, null)
            if (fits > 0) page.canvas.drawText(rest, 0, fits, MARGIN, y, paint)
            rest = rest.substring(fits)
            y += gap
        } while (rest.isNotEmpty())
    }

    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

    line(context.getString(R.string.history_pdf_title), title, gap = 26f)
    line(context.getString(R.string.history_pdf_student, studentName), heading)
    if (institutionName.isNotBlank()) line(context.getString(R.string.history_pdf_institution, institutionName))
    line(context.getString(R.string.history_pdf_generated, today), muted, gap = 22f)

    line(context.resources.getQuantityString(R.plurals.history_pdf_sessions, entries.size, entries.size), heading, gap = 20f)

    entries.forEach { entry ->
        line("${entry.date}  ${entry.title}", heading, gap = 13f)
        line(entry.summary, muted, gap = 18f)
    }
    if (entries.isEmpty()) line(context.getString(R.string.history_pdf_empty), muted)

    doc.finishPage(page)

    val file = File(context.cacheDir, "history")
        .apply { mkdirs() }
        .resolve("learning-history.pdf")
    file.outputStream().use { doc.writeTo(it) }
    doc.close()
    return file
}
