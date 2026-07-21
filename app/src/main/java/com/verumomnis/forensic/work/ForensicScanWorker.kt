package com.verumomnis.forensic.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.verumomnis.forensic.engine.EvidenceDocument
import com.verumomnis.forensic.engine.FindingsJsonEmitter
import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.engine.ReportGenerator
import com.verumomnis.forensic.vault.EvidenceVault
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Background forensic scan worker (build spec FR2.2).
 *
 * Accepts serialized evidence descriptors via input data, runs the deterministic
 * Nine-Brain pipeline through [ForensicService.scan], generates the sealed report
 * via [ReportGenerator.generate], and stores the findings JSON + report in the
 * [EvidenceVault]. Progress is published as [Data] so the UI can show the active
 * brain and completion status.
 */
class ForensicScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val INPUT_CASE_NAME = "case_name"
        const val INPUT_EVIDENCE_COUNT = "evidence_count"
        const val PREFIX_FILENAME = "evidence_"
        const val SUFFIX_FILENAME = "_filename"
        const val SUFFIX_TEXT_FILE = "_text_file"
        const val SUFFIX_SHA512 = "_sha512"
        const val SUFFIX_TYPE = "_type"

        const val PROGRESS_STAGE = "stage"
        const val PROGRESS_DETAIL = "detail"
        const val PROGRESS_PERCENT = "percent"

        const val OUTPUT_REFERENCE = "report_reference"
        const val OUTPUT_CONTRADICTIONS = "contradiction_count"
        const val OUTPUT_SHORTCODE = "seal_shortcode"
    }

    override suspend fun doWork(): Result {
        val caseName = inputData.getString(INPUT_CASE_NAME) ?: "Matter"
        val count = inputData.getInt(INPUT_EVIDENCE_COUNT, 0)
        if (count == 0) return Result.failure(failure("No evidence items provided"))

        setProgress(progress("Ingest", "Reading $count evidence item(s)", 5))

        val documents = buildList {
            repeat(count) { index ->
                val base = "$PREFIX_FILENAME$index"
                val fileName = inputData.getString(base + SUFFIX_FILENAME) ?: "evidence_$index.txt"
                val textFilePath = inputData.getString(base + SUFFIX_TEXT_FILE) ?: ""
                val text = if (textFilePath.isNotBlank()) {
                    runCatching { java.io.File(textFilePath).readText() }.getOrDefault("")
                } else ""
                val sha512 = inputData.getString(base + SUFFIX_SHA512) ?: ""
                val type = inputData.getString(base + SUFFIX_TYPE) ?: "document"
                add(
                    EvidenceDocument(
                        evidenceId = "DOC%03d".format(index + 1),
                        fileName = fileName,
                        type = type,
                        text = text,
                        sha512 = sha512
                    )
                )
            }
        }

        setProgress(progress("B1-B9", "Running Nine-Brain forensic analysis", 35))
        val now = Instant.now()
        val vault = EvidenceVault(applicationContext)
        val scanResult = ForensicService.scan(documents, now = now, vault = vault, caseName = caseName)

        setProgress(progress("Report", "Generating sealed forensic report", 75))
        val findingsJsonPath = java.io.File(vault.findings, FindingsJsonEmitter.findingsFileName(caseName, now)).absolutePath
        val report = ReportGenerator.generate(scanResult.findings, caseName, now, findingsJsonPath = findingsJsonPath)

        setProgress(progress("Vault", "Sealing findings and report", 95))
        vault.storeFinding("${report.reference}.json", Json.encodeToString(scanResult.findings))
        vault.storeFinding("${report.reference}_report.txt", report.body)

        return Result.success(
            Data.Builder()
                .putString(OUTPUT_REFERENCE, report.reference)
                .putInt(OUTPUT_CONTRADICTIONS, report.contradictions.size)
                .putString(OUTPUT_SHORTCODE, report.seal.shortcode)
                .build()
        )
    }

    private fun progress(stage: String, detail: String, percent: Int): Data =
        Data.Builder()
            .putString(PROGRESS_STAGE, stage)
            .putString(PROGRESS_DETAIL, detail)
            .putInt(PROGRESS_PERCENT, percent)
            .build()

    private fun failure(message: String): Data =
        Data.Builder().putString("error", message).build()
}
