package com.verumomnis.forensic.ui

import android.content.Context
import com.verumomnis.forensic.engine.PdfOcrExtractor
import com.verumomnis.forensic.engine.PdfExtractionResult
import com.verumomnis.forensic.model.PdfExtractionCoverage
import com.verumomnis.forensic.seal.DocumentSealer
import com.verumomnis.forensic.seal.OpenTimestampsClient

/** Device boundaries for the two sealing modes; tests never submit evidence or hashes online. */
class DocumentSealServices(
    val seal: (ByteArray, DocumentSealer.SealOptions) -> DocumentSealer.SealedDocument,
    val extractText: (ByteArray) -> String,
    val anchor: (String) -> OpenTimestampsClient.OtsResult,
    val extractPdf: (ByteArray) -> PdfExtractionResult = { bytes ->
        PdfExtractionResult(extractText(bytes), PdfExtractionCoverage(
            documentFailure = "Page-level extraction coverage unavailable"
        ))
    }
) {
    companion object {
        fun onDevice(context: Context) = DocumentSealServices(
            seal = { bytes, options -> DocumentSealer.sealWithBundledWatermark(context, bytes, options) },
            extractText = { bytes -> PdfOcrExtractor(context).extractText(bytes) },
            extractPdf = { bytes -> PdfOcrExtractor(context).extractWithCoverage(bytes) },
            anchor = { hash -> OpenTimestampsClient.submit(hash) }
        )
    }
}
