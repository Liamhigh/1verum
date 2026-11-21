package com.verumomnis.forensic.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import com.verumomnis.forensic.R
import com.verumomnis.forensic.model.ForensicReport
import com.verumomnis.forensic.model.SealedEmail
import com.verumomnis.forensic.vault.EvidenceVault
import java.io.File

/**
 * Produces the actual sealed PDF files the app emits (reports and emailed
 * evidence), each with the portrait watermark painted in the background, and
 * stores them in the Evidence Vault. Also builds a share intent so a sealed PDF
 * can be distributed.
 */
class SealedPdfExporter(
    private val context: Context,
    private val reportRenderer: (
        SealedPdfContent,
        Bitmap?,
        Bitmap?,
        Map<String, Bitmap>
    ) -> ByteArray = { content, mark, badge, exhibits ->
        SealedPdfGenerator.render(content, mark, badge, exhibits)
    }
) {

    private val watermark: Bitmap? by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.watermark_portrait)
    }
    private val logo: Bitmap? by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.vo_badge)
    }

    fun exportReport(report: ForensicReport): File {
        val reportWatermark = requireNotNull(watermark) {
            "The report watermark asset is missing or could not be decoded."
        }
        // Load preserved originals for image exhibits so they render on the exhibit pages.
        val rawDir = File(context.filesDir, "vault/evidence/raw")
        val vault = EvidenceVault(context)
        val exhibitImages = report.mediaExhibits
            .filter { it.kind == com.verumomnis.forensic.model.MediaKind.IMAGE }
            .mapNotNull { ex ->
                val f = File(rawDir, ex.fileName)
                if (!f.exists()) return@mapNotNull null
                val plaintext = File.createTempFile("pdf-exhibit-", ".tmp", context.cacheDir)
                try {
                    vault.materializeEvidence(ex.fileName, plaintext)
                    BitmapFactory.decodeFile(plaintext.path)?.let { ex.fileName to it }
                } finally {
                    plaintext.delete()
                }
            }.toMap()
        val bytes = reportRenderer(
            SealedPdfContent.fromReport(report),
            reportWatermark,
            logo,
            exhibitImages
        )
        val safeReference = report.reference.replace(Regex("[^A-Za-z0-9._-]"), "_")
        var suffix = 1
        while (true) {
            val suffixPart = if (suffix == 1) "" else "_$suffix"
            val fileName = "${safeReference}_report$suffixPart.pdf"
            val file = File(vault.reportsSealed, fileName)
            try {
                vault.storeSealedReport(fileName, bytes)
                return file
            } catch (_: java.nio.file.FileAlreadyExistsException) {
                suffix++
            }
        }
    }

    fun exportEmail(email: SealedEmail): File {
        val bytes = SealedPdfGenerator.render(SealedPdfContent.fromEmail(email), watermark, logo)
        val file = File(context.filesDir, "vault/reports/sealed").apply { mkdirs() }
            .resolve(email.sealedPdfFile)
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
