package com.tutorbridge.app.ui.screens.claims

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.tutorbridge.app.data.model.ClaimPeriodUi
import com.tutorbridge.app.data.model.InstitutionUi
import java.io.File

/**
 * Writes the CSV the server built for a claim period into the cache and hands
 * it to the share sheet, the same route the PDF voucher takes. The file is
 * the API's own export, so what the tutor shares is exactly what the
 * institution's admin would get from `GET /timesheets`.
 */
fun shareClaimCsv(context: Context, institution: InstitutionUi, period: ClaimPeriodUi, csv: String) {
    val file = try {
        File(context.cacheDir, "vouchers")
            .apply { mkdirs() }
            .resolve("claim-${period.id}.csv")
            .also { it.writeText(csv) }
    } catch (e: Exception) {
        Toast.makeText(context, "Could not create CSV: ${e.message}", Toast.LENGTH_LONG).show()
        return
    }

    // Reference: Android Developers. 2025. Setting up file sharing. [Online].
    //   Available at: https://developer.android.com/training/secure-file-sharing/setup-sharing [Accessed 21 September 2026].
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "${institution.shortName} claim - ${period.label} (CSV)")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, "Export claim CSV"))
}
