package com.verumomnis.forensic.seal

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.verumomnis.forensic.model.ParsedSealData
import com.verumomnis.forensic.model.SealRecord
import com.verumomnis.forensic.model.SealScanResult
import com.verumomnis.forensic.model.SealScanVerdict
import com.verumomnis.forensic.vault.EvidenceVault
import java.nio.charset.Charset
import kotlin.math.min

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
        Regex("\\(VO-SEAL2?\\|([0-9a-fA-F]{128})\\|(VO-[0-9A-Za-z-]+)(\\|ORIG:[0-9a-fA-F]{128})?(\\|CHAIN:[A-Za-z0-9,-]+)?\\)", RegexOption.IGNORE_CASE)

    private val RAW_V2_PREFIX = voUtf16Hex("VO-SEAL2|")

    private val FOOTER_SEAL_ID_RE =
        Regex("""Seal:\s*(VO-[0-9A-F]{12})""", RegexOption.IGNORE_CASE)
    private val FOOTER_SEAL_ID_RE2 =
        Regex("""VERUM\s+OMNIS\s+SEAL\s*\|\s*(VO-[0-9A-Za-z-]+)""", RegexOption.IGNORE_CASE)
    private val FOOTER_HASH_RE =
        Regex("""SHA-512:\s*([0-9a-f]{12,16})""", RegexOption.IGNORE_CASE)

    private fun voUtf16Hex(str: String): String = buildString {
        for (c in str) append(c.code.toString(16).uppercase().padStart(4, '0'))
    }

    private fun voUtf16HexDecode(hex: String): String = buildString {
        for (i in 0 until hex.length - 3 step 4) {
            val code = hex.substring(i, i + 4).toIntOrNull(16) ?: break
            append(code.toChar())
        }
    }

    /** Extract the seal (if any) from a PDF — raw fast paths first, then PDF metadata. */
    fun parseSeal(pdfBytes: ByteArray): SealChain.ParsedSealSubject? {
        // 1. Raw fast paths over the bytes (no parser needed) — mirrors web verify.html.
        parseSealV2Raw(pdfBytes)?.let { return it }
        scanRawBytes(pdfBytes)?.let { return it }

        // 2. PDF Subject metadata via PDFBox.
        return try {
            PDDocument.load(pdfBytes).use { doc ->
                SealChain.parseSubject(doc.documentInformation?.subject)
            }
        } catch (e: InvalidPasswordException) {
            // Password-protected — metadata unreadable without the password.
            null
        } catch (e: Exception) {
            // Unparseable as PDF — no seal detectable.
            null
        }
    }

    /** Whether the PDF requires a password to open. */
    fun isEncrypted(pdfBytes: ByteArray): Boolean = try {
        PDDocument.load(pdfBytes).use { it.isEncrypted }
    } catch (e: InvalidPasswordException) {
        true
    } catch (e: Exception) {
        false
    }

    /** Raw-bytes fallback — scans the file for the literal legacy subject string. */
    fun scanRawBytes(pdfBytes: ByteArray): SealChain.ParsedSealSubject? {
        val latin1 = String(pdfBytes, Charset.forName("ISO-8859-1"))
        val m = RAW_SUBJECT_RE.find(latin1) ?: return null
        val subject = "VO-SEAL|${m.groupValues[1]}|${m.groupValues[2]}${m.groupValues.getOrNull(3) ?: ""}"
        return SealChain.parseSubject(subject)
    }

    /**
     * Raw-bytes fast path for VO-SEAL2: pdf-lib writes the Subject Info string as
     * UTF-16BE-hex ASCII. Search for the prefix and decode the window.
     */
    fun parseSealV2Raw(pdfBytes: ByteArray): SealChain.ParsedSealSubject? {
        val latin1 = String(pdfBytes, Charset.forName("ISO-8859-1"))
        val idx = latin1.indexOf(RAW_V2_PREFIX)
        if (idx == -1) return null
        val window = latin1.substring(idx, min(idx + 1600, latin1.length))
        val decoded = voUtf16HexDecode(window)
        return SealChain.parseSubject(decoded)
    }

    /** A seal detected only from page footer text (no readable metadata). */
    data class FooterSeal(
        val sealId: String,
        val hashPrefix: String?
    )

    /**
     * PDFBox text-extraction fallback: look for the Verum seal footer on the
     * first pages. This matches the web verify.html page-text fallback.
     */
    fun scanFooterSeal(pdfBytes: ByteArray): FooterSeal? {
        return try {
            PDDocument.load(pdfBytes).use { doc ->
                val stripper = PDFTextStripper()
                val maxPages = min(doc.numberOfPages, 3)
                for (p in 0 until maxPages) {
                    stripper.startPage = p + 1
                    stripper.endPage = p + 1
                    val text = try {
                        stripper.getText(doc)
                    } catch (e: Exception) {
                        continue
                    }
                    FOOTER_SEAL_ID_RE.find(text)?.let { m ->
                        val hashPrefix = FOOTER_HASH_RE.find(text)?.groupValues?.get(1)?.lowercase()
                        return FooterSeal(m.groupValues[1], hashPrefix)
                    }
                    FOOTER_SEAL_ID_RE2.find(text)?.let { m ->
                        val hashPrefix = FOOTER_HASH_RE.find(text)?.groupValues?.get(1)?.lowercase()
                        return FooterSeal(m.groupValues[1], hashPrefix)
                    }
                }
                null
            }
        } catch (e: InvalidPasswordException) {
            null
        } catch (e: Exception) {
            null
        }
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

    /**
     * QR-aware verification that mirrors the live webdocsol verify.html.
     *
     * @param pdfBytes full PDF file bytes.
     * @param qrPayload optional payload decoded from a scanned seal QR code.
     * @return [SealScanResult] with the same verdict taxonomy as the website
     *         (MATCH, TAMPERED, SEAL_PRESENT, LEGACY, NO_SEAL).
     */
    fun verifyWithQr(pdfBytes: ByteArray, qrPayload: QrScanPayload? = null): SealScanResult {
        val fileHash = SealHasher.sha512Hex(pdfBytes)
        val encrypted = isEncrypted(pdfBytes)
        val seal = parseSeal(pdfBytes)
        val footer = if (seal == null) scanFooterSeal(pdfBytes) else null

        if (seal != null) {
            val parsed = seal.toParsedSealData()
            val metadataSource = "PDF Subject"

            when (seal.scheme) {
                "v2" -> {
                    val v2 = SealV2IntegrityChecker.check(pdfBytes, seal.sha512)
                    val qrHash = qrPayload?.hashPrefix?.lowercase()
                    val expectedForQr = seal.origHash?.lowercase() ?: seal.sha512.lowercase()
                    val qrMatches = qrHash == null || expectedForQr.startsWith(qrHash)

                    return when {
                        !v2.feasible -> SealScanResult(
                            verdict = SealScanVerdict.SEAL_PRESENT,
                            seal = parsed,
                            qrMetadata = qrPayload?.metadata,
                            scannedHashPrefix = qrPayload?.hashPrefix,
                            fileHash = fileHash,
                            recomputedHash = null,
                            originalHash = seal.origHash,
                            otsDigest = seal.origHash?.otsDigest(),
                            metadataSource = metadataSource,
                            isEncrypted = encrypted,
                            reason = "This PDF carries a VO-SEAL2 seal, but the file was re-encoded by another PDF tool after sealing (the seal metadata no longer sits at its original byte position), so the sealed-file hash cannot be recomputed. The embedded hashes are shown for manual comparison. This is not a tamper finding."
                        )
                        v2.match && qrMatches -> SealScanResult(
                            verdict = SealScanVerdict.MATCH,
                            seal = parsed,
                            qrMetadata = qrPayload?.metadata,
                            scannedHashPrefix = qrPayload?.hashPrefix,
                            fileHash = fileHash,
                            recomputedHash = v2.recomputed,
                            originalHash = seal.origHash,
                            otsDigest = seal.origHash?.otsDigest(),
                            metadataSource = metadataSource,
                            isEncrypted = encrypted,
                            reason = "This sealed file's own SHA-512 matches the hash embedded in its seal (VO-SEAL2 self-integrity check over the final sealed bytes). The document has not been altered since sealing."
                        )
                        !qrMatches -> SealScanResult(
                            verdict = SealScanVerdict.TAMPERED,
                            seal = parsed,
                            qrMetadata = qrPayload?.metadata,
                            scannedHashPrefix = qrPayload?.hashPrefix,
                            fileHash = fileHash,
                            recomputedHash = v2.recomputed,
                            originalHash = seal.origHash,
                            otsDigest = seal.origHash?.otsDigest(),
                            metadataSource = metadataSource,
                            isEncrypted = encrypted,
                            reason = "The scanned QR hash prefix does not match this document's original content hash. The file may have been swapped or altered."
                        )
                        else -> SealScanResult(
                            verdict = SealScanVerdict.TAMPERED,
                            seal = parsed,
                            qrMetadata = qrPayload?.metadata,
                            scannedHashPrefix = qrPayload?.hashPrefix,
                            fileHash = fileHash,
                            recomputedHash = v2.recomputed,
                            originalHash = seal.origHash,
                            otsDigest = seal.origHash?.otsDigest(),
                            metadataSource = metadataSource,
                            isEncrypted = encrypted,
                            reason = "The sealed file's recomputed SHA-512 does NOT match the hash embedded in its VO-SEAL2 seal. This file has been modified after sealing."
                        )
                    }
                }
                else -> {
                    // Legacy VO-SEAL (pre-v1.3): embedded hash covers the original content.
                    val originalHash = seal.origHash ?: seal.sha512
                    val qrHash = qrPayload?.hashPrefix?.lowercase()
                    val qrMatches = qrHash == null || originalHash.lowercase().startsWith(qrHash)
                    return SealScanResult(
                        verdict = if (qrMatches) SealScanVerdict.LEGACY else SealScanVerdict.TAMPERED,
                        seal = parsed,
                        qrMetadata = qrPayload?.metadata,
                        scannedHashPrefix = qrPayload?.hashPrefix,
                        fileHash = fileHash,
                        recomputedHash = null,
                        originalHash = originalHash,
                        otsDigest = originalHash.otsDigest(),
                        metadataSource = metadataSource,
                        isEncrypted = encrypted,
                        reason = if (qrMatches) {
                            "This PDF carries a Verum Omnis seal in the legacy (pre-v1.3) format. The embedded SHA-512 covers the document's original pre-seal content; integrity of that original is attested by the embedded hash and its OpenTimestamps anchoring at sealing time. A sealed-file hash check is not applicable to this format — this is not a tamper finding."
                        } else {
                            "The scanned QR hash prefix does not match this legacy seal's embedded hash. The file may have been swapped or altered."
                        }
                    )
                }
            }
        }

        if (footer != null) {
            return SealScanResult(
                verdict = SealScanVerdict.SEAL_PRESENT,
                seal = ParsedSealData(
                    scheme = "footer",
                    sealId = footer.sealId,
                    embeddedHash = footer.hashPrefix ?: ""
                ),
                qrMetadata = qrPayload?.metadata,
                scannedHashPrefix = qrPayload?.hashPrefix,
                fileHash = fileHash,
                recomputedHash = null,
                originalHash = null,
                otsDigest = null,
                metadataSource = "PDF text footer",
                isEncrypted = encrypted,
                reason = "A Verum Omnis seal footer was found in this PDF's page text, but the seal metadata is missing or unreadable, so no integrity verdict can be computed. The file hash is shown for manual comparison."
            )
        }

        return SealScanResult(
            verdict = SealScanVerdict.NO_SEAL,
            seal = null,
            qrMetadata = qrPayload?.metadata,
            scannedHashPrefix = qrPayload?.hashPrefix,
            fileHash = fileHash,
            recomputedHash = null,
            originalHash = null,
            otsDigest = null,
            metadataSource = null,
            isEncrypted = encrypted,
            reason = if (encrypted) {
                "This document is password-protected. Open it with the sender's password, then verify the decrypted copy."
            } else {
                "No Verum Omnis seal metadata was detected. The document was not sealed by the Verum Omnis system, or the seal metadata was stripped in transit."
            }
        )
    }

    private fun SealChain.ParsedSealSubject.toParsedSealData(): ParsedSealData =
        ParsedSealData(
            scheme = scheme,
            sealId = sealId,
            embeddedHash = sha512,
            originalHash = origHash,
            chain = chain
        )

    private fun String.otsDigest(): String =
        SealHasher.sha256Hex(toByteArray(Charsets.UTF_8))
}
