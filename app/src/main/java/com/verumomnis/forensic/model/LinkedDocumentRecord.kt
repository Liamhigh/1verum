package com.verumomnis.forensic.model

import kotlinx.serialization.Serializable

/** Explicit parent/derivative references; filenames are never used to infer identity. */
@Serializable
data class LinkedDocumentRecord(
    val id: String,
    val sourceName: String,
    val sourceSha512: String,
    val createdAt: String,
    val sourcePath: String,
    val forensicRequested: Boolean,
    val sealedPdfPath: String? = null,
    val reportPdfPath: String? = null,
    val timestampProofPath: String? = null,
    val reportReference: String? = null
)
