package com.verumomnis.forensic.pdf

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.verumomnis.forensic.R
import com.verumomnis.forensic.model.DocumentExtractionCoverage
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

    fun exportReport(report: ForensicReport, sourceFileName: String? = null): File {
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
        val fileName = SealedOutputNames.forensicReport(sourceFileName, report.reference)
        var collisionNumber = 1
        while (true) {
            val candidateName = SealedOutputNames.numbered(fileName, collisionNumber)
            try {
                vault.storeSealedReport(candidateName, bytes)
                return File(vault.reportsSealed, candidateName)
            } catch (_: java.nio.file.FileAlreadyExistsException) {
                collisionNumber++
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
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share sealed PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Shares the exact report and its report-linked findings JSON as one pair.
     * Copies only their bytes into cache to provide descriptive share filenames;
     * vaulted evidence and saved reports are never renamed or rewritten.
     */
    fun shareRetryPair(
        report: ForensicReport,
        source: DocumentExtractionCoverage,
        reportPdf: File,
        findingsJson: File,
        launchChooser: (Intent) -> Unit = { chooser ->
            context.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    ) {
        val attemptId = source.coverage.retryAttempt?.id
            ?: throw IllegalArgumentException("The source record is not an OCR retry.")
        val belongsToRetry = report.extractionCoverage.any { saved ->
            saved.sha512.equals(source.sha512, ignoreCase = true) &&
                saved.coverage.retryAttempt?.id == attemptId
        }
        require(belongsToRetry) { "The selected report does not belong to this source PDF and retry attempt." }

        val expectedJson = runCatching { File(report.findingsJsonPath).canonicalFile }.getOrNull()
        require(expectedJson != null && expectedJson == findingsJson.canonicalFile) {
            "The selected findings JSON is not the exact file linked by this report."
        }
        val expectedJsonName = Regex("ocr_retry_${Regex.escape(attemptId)}_findings(?:_\\d+)?\\.json")
        require(expectedJsonName.matches(findingsJson.name)) {
            "The selected findings JSON does not match this retry attempt."
        }
        val expectedPdfName = SealedOutputNames.forensicReport(source.fileName, report.reference)
        val expectedPdfStem = expectedPdfName.removeSuffix(".pdf")
        val numberedPdfName = Regex("${Regex.escape(expectedPdfStem)}_\\d+\\.pdf")
        require(reportPdf.name == expectedPdfName || numberedPdfName.matches(reportPdf.name)) {
            "The selected PDF does not match this retry report."
        }
        requireReadable(reportPdf, "retry PDF")
        requireReadable(findingsJson, "matching findings JSON")

        val shareDirectory = File(context.cacheDir, "retry-share")
        if (!shareDirectory.isDirectory && !shareDirectory.mkdirs()) {
            throw java.io.IOException("Unable to prepare temporary files for sharing.")
        }
        val sourceStem = safeShareName(source.fileName.substringBeforeLast('.', source.fileName))
        val attemptStem = safeShareName(attemptId)
        val shareStem = "VerumOmnis_${sourceStem}_retry_$attemptStem"
        val pdfCopy = File.createTempFile("${shareStem}_report_", ".pdf", shareDirectory)
        var jsonCopy: File? = null
        try {
            val preparedJson = File.createTempFile("${shareStem}_findings_", ".json", shareDirectory)
            jsonCopy = preparedJson
            reportPdf.copyTo(pdfCopy, overwrite = true)
            findingsJson.copyTo(preparedJson, overwrite = true)
            check(pdfCopy.length() == reportPdf.length() && preparedJson.length() == findingsJson.length()) {
                "A complete copy of both retry files could not be prepared."
            }

            val pdfUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", pdfCopy
            )
            val jsonUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", preparedJson
            )
            val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "application/json"))
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf<Uri>(pdfUri, jsonUri))
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Verum Omnis OCR retry · ${source.fileName} · attempt $attemptId"
                )
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Source PDF: ${source.fileName}\nRetry attempt: $attemptId"
                )
                clipData = ClipData.newUri(
                    context.contentResolver, "Verum Omnis retry files", pdfUri
                ).apply {
                    addItem(ClipData.Item(jsonUri))
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            launchChooser(
                Intent.createChooser(send, "Share retry PDF and findings JSON")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (error: Exception) {
            pdfCopy.delete()
            jsonCopy?.delete()
            throw error
        }
    }

    private fun requireReadable(file: File, label: String) {
        require(file.isFile && file.canRead() && file.length() > 0) {
            "The $label is missing or unreadable."
        }
    }

    private fun safeShareName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('.', '_')
        .take(48)
        .ifBlank { "document" }
}
