package com.verumomnis.forensic.engine

import com.verumomnis.forensic.model.GpsRecord

/** Raw input to the forensic engine: an ingested document plus capture context. */
data class EvidenceDocument(
    val evidenceId: String,
    val fileName: String,
    val type: String,
    val text: String,
    val sha512: String,
    val gps: GpsRecord? = null,
    /** Optional structured financials parsed from the document. */
    val revenue: Double? = null,
    val expenses: Double? = null,
    /** Optional document metadata for B2 tamper detection. */
    val creatorTool: String? = null,
    val documentKind: String? = null
)
