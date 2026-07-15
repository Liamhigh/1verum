package com.verumomnis.forensic.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.verumomnis.forensic.ui.PendingFilePreview

/**
 * Schedules a [ForensicScanWorker] for background execution.
 *
 * The worker receives the pending evidence previews (text + SHA-512) so the scan
 * can continue even if the user backgrounds the app. UI code can observe the
 * worker's progress and output via [WorkManager.getWorkInfoByIdLiveData].
 */
object ScanWorkScheduler {

    fun schedule(context: Context, caseName: String, previews: List<PendingFilePreview>) {
        val data = buildInput(caseName, previews)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = OneTimeWorkRequestBuilder<ForensicScanWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .addTag("forensic_scan")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private fun buildInput(caseName: String, previews: List<PendingFilePreview>): Data {
        val builder = Data.Builder()
            .putString(ForensicScanWorker.INPUT_CASE_NAME, caseName)
            .putInt(ForensicScanWorker.INPUT_EVIDENCE_COUNT, previews.size)
        previews.forEachIndexed { index, preview ->
            val base = "${ForensicScanWorker.PREFIX_FILENAME}$index"
            builder.putString(base + ForensicScanWorker.SUFFIX_FILENAME, preview.fileName)
            builder.putString(base + ForensicScanWorker.SUFFIX_TEXT, preview.documentText)
            builder.putString(base + ForensicScanWorker.SUFFIX_SHA512, preview.sha512)
            builder.putString(base + ForensicScanWorker.SUFFIX_TYPE, preview.mimeType)
        }
        return builder.build()
    }
}
