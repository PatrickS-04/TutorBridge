package com.tutorbridge.app.ui.screens.claims

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import com.tutorbridge.app.data.model.ClaimPeriodUi
import com.tutorbridge.app.data.model.ClaimStatus
import com.tutorbridge.app.data.model.InstitutionUi
import java.io.File

private const val PAGE_W = 595 // A4 at 72dpi
private const val PAGE_H = 842
private const val MARGIN = 40f

/**
 * Renders the claim period as a PDF into the cache and hands it to the share
 * sheet so the tutor can save or email it.
 */
fun exportClaimVoucher(context: Context, institution: InstitutionUi, period: ClaimPeriodUi) {
    val file = try {
        writeVoucherPdf(context, institution, period)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not create PDF: ${e.message}", Toast.LENGTH_LONG).show()
        return
    }

    // Reference: Android Developers. 2025. Setting up file sharing. [Online].
    //   Available at: https://developer.android.com/training/secure-file-sharing/setup-sharing [Accessed 21 September 2026].
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "${institution.shortName} claim - ${period.label}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, "Export claim voucher"))
}

// Reference: Android Developers. 2026. PdfDocument. [Online].
//   Available at: https://developer.android.com/reference/android/graphics/pdf/PdfDocument [Accessed 21 September 2026].
private fun writeVoucherPdf(
    context: Context,
    institution: InstitutionUi,
    period: ClaimPeriodUi
): File {
    val title = Paint().apply { textSize = 18f; isFakeBoldText = true }
    val heading = Paint().apply { textSize = 12f; isFakeBoldText = true }
    val body = Paint().apply { textSize = 10f }
    val muted = Paint().apply { textSize = 9f; color = 0xFF666666.toInt() }

    val doc = PdfDocument()
    var pageNumber = 0
    var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNumber).create())
    var y = MARGIN + 10f

    fun line(text: String = "", paint: Paint = body, gap: Float = 14f) {
        if (y + gap > PAGE_H - MARGIN) {
            doc.finishPage(page)
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNumber).create())
            y = MARGIN + 10f
        }
        if (text.isNotEmpty()) page.canvas.drawText(text, MARGIN, y, paint)
        y += gap
    }

    val rate = institution.rate.toInt()

    line("Tutoring Claim Voucher", title, gap = 26f)
    line(institution.fullName, heading)
    line("${institution.faculty} - Staff #${institution.staffId} - R$rate/hr", muted, gap = 22f)

    line("Claim period: ${period.label}", heading)
    line("Status: ${if (period.status == ClaimStatus.PAID) "Paid to bank" else "Draft (claimable)"}")
    if (period.claimRef.isNotBlank()) line("Claim ref: ${period.claimRef}")
    line("Total hours: ${period.totalHours}")
    line("Sessions: ${period.sessionsCount}")
    line("Claim value: R${period.claimValue.toInt()}", heading, gap = 24f)

    line("Itemized tutoring sessions", heading)
    line("Student attendance check-in: ${period.verifiedCount} of ${period.sessionsCount} sessions verified", muted, gap = 18f)

    period.sessions.forEach { s ->
        line("${s.courseCode}  ${s.date}  ${s.timeRange}", heading, gap = 13f)
        line("${s.studentName} (${s.studentIdTag}) - ${s.hours} hrs @ R$rate - ${s.amount}")
        line(if (s.checkedIn) "Attendance: student checked in" else "Attendance: no check-in recorded", muted, gap = 13f)
        if (s.topic.isNotBlank()) line("Topic: ${s.topic}", muted, gap = 16f) else y += 4f
    }
    if (period.sessions.isEmpty()) line("No sessions recorded for this period.", muted)

    doc.finishPage(page)

    val file = File(context.cacheDir, "vouchers")
        .apply { mkdirs() }
        .resolve("claim-${period.label.replace(Regex("[^A-Za-z0-9]+"), "-")}.pdf")
    file.outputStream().use { doc.writeTo(it) }
    doc.close()
    return file
}
