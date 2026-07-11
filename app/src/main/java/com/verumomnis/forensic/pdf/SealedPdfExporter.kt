package com.verumomnis.forensic.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import com.verumomnis.forensic.R
import com.verumomnis.forensic.model.ForensicReport
import com.verumomnis.forensic.model.SealedEmail
import java.io.File

/**
 * Produces the actual sealed PDF files the app emits (reports and emailed
 * evidence), each with the portrait watermark painted in the background, and
 * stores them in the Evidence Vault. Also builds a share intent so a sealed PDF
 * can be distributed.
 */
class SealedPdfExporter(private val context: Context) {

    private val watermark: Bitmap? by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.watermark_portrait)
    }
    private val logo: Bitmap? by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.vo_badge)
    }

    private fun sealedDir(): File =
        File(context.filesDir, "vault/reports/sealed").apply { mkdirs() }

    fun exportReport(report: ForensicReport): File {
        val bytes = SealedPdfGenerator.render(SealedPdfContent.fromReport(report), watermark, logo)
        val file = File(sealedDir(), "${report.reference}.pdf")
        file.writeBytes(bytes)
        return file
    }

    fun exportEmail(email: SealedEmail): File {
        val bytes = SealedPdfGenerator.render(SealedPdfContent.fromEmail(email), watermark, logo)
        val file = File(sealedDir(), email.sealedPdfFile)
        file.writeBytes(bytes)
        return file
    }

    fun share(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share sealed PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
