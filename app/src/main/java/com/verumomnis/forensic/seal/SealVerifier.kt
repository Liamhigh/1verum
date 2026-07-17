package com.verumomnis.forensic.seal

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.verumomnis.forensic.model.SealRecord
import com.verumomnis.forensic.vault.EvidenceVault
import java.nio.charset.Charset

/**
 * VO-DSS-1.2 — Seal verifier (Android/PDFBox port).
 * Port of verify.html verifyFile(): read the seal from PDF Subject metadata,
 * fall back to a raw-bytes scan, then compare against an expected SHA-512
 * (e.g. from a scanned QR code) when supplied.
 */
object SealVerifier {

    enum class Verdict {
        VERIFIED,   // embedded seal matches the expected hash — document intact
        TAMPERED,   // embedded seal does NOT match the expected hash
        SEAL_FOUND, // seal present, no expected hash to compare against
        NO_SEAL     // no Verum Omnis seal detected
    }

    data class SealVerification(
        val verdict: Verdict,
        val seal: SealChain.ParsedSealSubject?,
        val metadataSource: String?, // "PDF Subject metadata" | "PDF content scan" | null
        val isEncrypted: Boolean,
        val expectedHash: String?,
        val reason: String
    )

    private val RAW_SUBJECT_RE =
        Regex("\\(VO-SEAL\\|([a-f0-9]{128})\\|(VO-[A-F0-9]+)(\\|CHAIN:[A-Z0-9,-]+)?\\)", RegexOption.IGNORE_CASE)

    /** Extract the seal (if any) from a PDF — Subject metadata first, raw scan fallback. */
    fun parseSeal(pdfBytes: ByteArray): SealChain.ParsedSealSubject? {
        try {
            PDDocument.load(pdfBytes).use { doc ->
                val parsed = SealChain.parseSubject(doc.documentInformation?.subject)
                if (parsed != null) return parsed
                val keywords = doc.documentInformation?.keywords
                if (keywords != null && keywords.contains(SealChain.KEYWORDS_PREFIX)) {
                    scanRawBytes(pdfBytes)?.let { return it }
                }
            }
        } catch (e: InvalidPasswordException) {
            // Password-protected — metadata unreadable without the password.
            return null
        } catch (e: Exception) {
            // Unparseable as PDF — fall through to raw scan.
        }
        return scanRawBytes(pdfBytes)
    }

    /** Whether the PDF requires a password to open. */
    fun isEncrypted(pdfBytes: ByteArray): Boolean = try {
        PDDocument.load(pdfBytes).use { it.isEncrypted }
    } catch (e: InvalidPasswordException) {
        true
    } catch (e: Exception) {
        false
    }

    /** Raw-bytes fallback — scans the file for the literal subject string. */
    fun scanRawBytes(pdfBytes: ByteArray): SealChain.ParsedSealSubject? {
        val latin1 = String(pdfBytes, Charset.forName("ISO-8859-1"))
        val m = RAW_SUBJECT_RE.find(latin1) ?: return null
        val subject = "VO-SEAL|${m.groupValues[1]}|${m.groupValues[2]}${m.groupValues.getOrNull(3) ?: ""}"
        return SealChain.parseSubject(subject)
    }

    /**
     * Verify a (possibly sealed) PDF.
     * @param expectedHash optional SHA-512 (full or prefix, e.g. the 32-hex QR value).
     */
    fun verify(pdfBytes: ByteArray, expectedHash: String? = null): SealVerification {
        val encrypted = isEncrypted(pdfBytes)
        val seal = parseSeal(pdfBytes)
        val expected = expectedHash?.lowercase()

        if (expected != null && seal != null) {
            val match = seal.sha512.lowercase().startsWith(expected)
            return SealVerification(
                verdict = if (match) Verdict.VERIFIED else Verdict.TAMPERED,
                seal = seal,
                metadataSource = "PDF Subject metadata",
                isEncrypted = encrypted,
                expectedHash = expected,
                reason = if (match) {
                    "The SHA-512 fingerprint embedded in this document matches the expected hash. The document has NOT been tampered with since it was sealed."
                } else {
                    "The SHA-512 fingerprint does NOT match the expected hash. This document has been altered or corrupted since it was sealed — do not accept it."
                }
            )
        }

        if (seal != null) {
            return SealVerification(
                verdict = Verdict.SEAL_FOUND,
                seal = seal,
                metadataSource = "PDF Subject metadata",
                isEncrypted = encrypted,
                expectedHash = expected,
                reason = "A Verum Omnis seal was found embedded in this document. Compare the Seal ID and SHA-512 prefix with the footer printed on every page to confirm authenticity."
            )
        }

        return SealVerification(
            verdict = Verdict.NO_SEAL,
            seal = null,
            metadataSource = null,
            isEncrypted = encrypted,
            expectedHash = expected,
            reason = if (encrypted) {
                "This document is password-protected. Open it with the sender's password, then verify the decrypted copy."
            } else {
                "No Verum Omnis seal metadata was detected. The document was not sealed by the Verum Omnis system, or the seal metadata was stripped in transit."
            }
        )
    }

    /** Outcome of checking a SHA-512 fingerprint against real records held on this device. */
    data class HashRecordCheck(val verdict: Verdict, val message: String)

    /**
     * Check a 128-hex SHA-512 fingerprint against actual records on this
     * device — session seals first, then the vault integrity manifest.
     * A format-valid hash is never "authentic" on its own: an authenticity
     * claim is made only when it matches a real seal record.
     */
    fun checkHashAgainstRecords(
        sha512: String,
        sessionSeals: List<SealRecord?>,
        vaultManifest: List<EvidenceVault.IntegrityEntry>
    ): HashRecordCheck {
        sessionSeals.filterNotNull().firstOrNull { it.sha512.equals(sha512, ignoreCase = true) }?.let {
            return HashRecordCheck(
                Verdict.SEAL_FOUND,
                "Authentic: matches a Verum Omnis seal record on this device (seal ${it.sealId} · ${it.documentReference})."
            )
        }
        vaultManifest.firstOrNull { it.sha512.equals(sha512, ignoreCase = true) }?.let {
            return HashRecordCheck(
                Verdict.SEAL_FOUND,
                "Authentic: matches a Verum Omnis seal record on this device (vault evidence file ${it.fileName})."
            )
        }
        return HashRecordCheck(
            Verdict.NO_SEAL,
            "Valid SHA-512 format (128 hex chars). Format check only — no matching seal record was found in this device's vault, so authenticity cannot be confirmed from the hash alone."
        )
    }
}
