package com.verumomnis.forensic.model

import com.verumomnis.forensic.seal.SealMetadataCodec

/**
 * Result of scanning a Verum seal QR code and verifying the sealed PDF.
 * Verdicts mirror the live webdocsol verify.html.
 */
enum class SealScanVerdict {
    MATCH,          // VO-SEAL2 self-integrity check passed; QR prefix matches.
    TAMPERED,       // Embedded hash does not match recomputed hash or QR prefix.
    SEAL_PRESENT,   // Seal metadata found but integrity check is infeasible (re-encoded).
    LEGACY,         // Legacy VO-SEAL (pre-v1.3); original-content hash embedded, not sealed-file hash.
    NO_SEAL         // No Verum seal detected.
}

/**
 * Parsed seal data from a QR code or PDF metadata.
 */
data class ParsedSealData(
    val scheme: String,          // "v2" | "legacy" | "footer"
    val sealId: String,
    val embeddedHash: String,  // 128-char SHA-512 (sealed-file for v2, original for legacy)
    val originalHash: String? = null,
    val chain: List<String> = emptyList()
)

/**
 * Full scan + verify result, displayed by the result screen.
 */
data class SealScanResult(
    val verdict: SealScanVerdict,
    val seal: ParsedSealData?,
    val qrMetadata: SealMetadataCodec.SealMetadata?,
    val scannedHashPrefix: String?,
    val fileHash: String?,          // SHA-512 of the uploaded PDF file
    val recomputedHash: String?,    // SHA-512 after restoring placeholder (v2 only)
    val originalHash: String?,      // OTS-anchored original content hash (v2/legacy)
    val otsDigest: String?,           // SHA-256 of original SHA-512 for OTS lookup
    val metadataSource: String?,    // "PDF Subject" | "PDF text footer" | "raw bytes" | null
    val isEncrypted: Boolean,
    val reason: String
)
