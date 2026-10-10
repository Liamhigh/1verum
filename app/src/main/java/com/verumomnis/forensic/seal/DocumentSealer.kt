package com.verumomnis.forensic.seal

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * VO-DSS-1.2 — Document sealer (Android/PDFBox port).
 * Faithful port of buildSealedPDF() from seal-module/web/seal-document.html:
 *   - optional password cover page (delivery-receipt mode)
 *   - A4 watermark background at 20% opacity
 *   - original pages embedded at 88% scale, per-page error recovery (v1.2)
 *   - clean QR (no border/box) top-right, 10% of page dimension, 2.5% margin
 *   - seal footer bar on every page: type + chain, Seal ID + SHA-512 prefix + timestamp + page x/y
 *   - Subject metadata (default, matching the website):
 *       VO-SEAL2|<sealed-file-hash>|<sealId>|ORIG:<original-hash>[|CHAIN:...]
 *     written as a UTF-16BE hex string with the 128-zero placeholder patched
 *     after save, so self-sealed documents pass the VO-SEAL2 self-integrity
 *     check as Genuine (never LEGACY). Legacy VO-SEAL subjects remain
 *     verifiable via SealVerifier.
 *   - optional true AES-256 password encryption via StandardProtectionPolicy
 *     (PDFBox enforces it — unlike pdf-lib on the web, which silently ignores it)
 *
 * Interoperable with the website and the Firewall outputs.
 */
object DocumentSealer {

    private const val PAGE_SCALE = 0.88f
    private const val QR_FRACTION = 0.10f
    private const val MARGIN_FRACTION = 0.025f
    private const val WATERMARK_OPACITY = 0.2f

    private val DARK = floatArrayOf(0.02f, 0.05f, 0.08f)
    private val GOLD = floatArrayOf(0.82f, 0.65f, 0.28f)
    private val SLATE = floatArrayOf(0.58f, 0.71f, 0.78f)
    private val MUTED = floatArrayOf(0.36f, 0.42f, 0.48f)

    data class SealOptions(
        val timestampMs: Long, // injected — constitution: deterministic time
        val sealType: String = "private", // "private" | "commercial"
        val org: String? = null,
        val identity: SealMetadataCodec.SealIdentity? = null,
        val gpsLat: String? = null,
        val gpsLng: String? = null,
        val gpsAccuracyM: Int? = null,
        val device: String? = null, // pre-formatted "Platform|Cores|Timezone"
        val password: String? = null, // min 8 chars — enables cover page + AES-256
        val originalName: String = "Sealed Document",
        val anchorToBlockchain: Boolean = true,
        /** "v2" (default, website-compatible VO-SEAL2) or "legacy" (VO-SEAL). */
        val sealScheme: String = "v2"
    )

    data class SealedDocument(
        val sealedPdf: ByteArray,
        val sealId: String,
        val sha256: String,
        val sha512: String,
        /** VO-SEAL2: hash of the final sealed bytes (placeholder-patched). Empty for legacy. */
        val sealedSha512: String = "",
        val sealScheme: String = "v2",
        val verifyUrl: String,
        val metadata: SealMetadataCodec.SealMetadata,
        val priorChain: List<String>,
        val ots: OpenTimestampsClient.OtsResult?,
        val pageCount: Int,
        val pageErrors: Int,
        val passwordProtected: Boolean
    )

    /**
     * Seal a PDF document to VO-DSS-1.2.
     * @param watermarkPng optional A4 watermark bitmap (asset); pages seal without it.
     */
    fun seal(
        originalPdfBytes: ByteArray,
        options: SealOptions,
        watermarkPng: Bitmap? = null
    ): SealedDocument {
        val password = options.password?.takeIf { it.length >= 8 }

        // Steps 2 + 8: dual hash of the ORIGINAL bytes.
        val sha256 = SealHasher.sha256Hex(originalPdfBytes)
        val sha512 = SealHasher.sha512Hex(originalPdfBytes)
        val sealId = SealHasher.sealIdFromSha512(sha512)

        // Chain detection (v1.2): read any prior seal from the source document.
        val priorChain = SealVerifier.parseSeal(originalPdfBytes)
            ?.let { SealChain.chainIdsFrom(it, sealId) }
            ?: emptyList()

        // Metadata + QR payload (step 5).
        val meta = SealMetadataCodec.collect(
            SealMetadataCodec.SealMetadataInput(
                timestampMs = options.timestampMs,
                sealType = options.sealType,
                org = options.org,
                identity = options.identity,
                lock = password != null,
                gpsLat = options.gpsLat,
                gpsLng = options.gpsLng,
                gpsAccuracyM = options.gpsAccuracyM,
                device = options.device
            )
        )
        val verifyUrl = SealMetadataCodec.buildVerifyUrl(sha512, meta)

        // Step 3: OpenTimestamps (skippable for offline sealing). Anchors the
        // SHA-512 fingerprint via the canonical OpenTimestampsService.
        val ots = if (options.anchorToBlockchain) OpenTimestampsClient.submit(sha512) else null

        // Steps 4, 6, 7: build the sealed PDF.
        var pageErrors = 0
        var totalPages = 0
        val sealedBytes = PDDocument.load(originalPdfBytes).use { orig ->
            PDDocument().use { sealed ->
                val wmImg = watermarkPng?.let {
                    try {
                        LosslessFactory.createFromImage(sealed, it)
                    } catch (e: Exception) {
                        null
                    }
                }
                val qrImg = LosslessFactory.createFromImage(sealed, qrBitmap(verifyUrl, 400))

                val ts = formatUtc(options.timestampMs)
                val short = sha512.substring(0, 16)

                val firstBox = if (orig.numberOfPages > 0) orig.getPage(0).mediaBox else PDRectangle.A4
                var pw = firstBox.width
                var ph = firstBox.height

                if (password != null) {
                    val cover = PDPage(PDRectangle(pw, ph))
                    sealed.addPage(cover)
                    drawCoverPage(sealed, cover, wmImg, sealId, options.identity?.e)
                }
                totalPages = orig.numberOfPages + if (password != null) 1 else 0

                val layerUtility = LayerUtility(sealed)
                for (i in 0 until orig.numberOfPages) {
                    val srcPage = orig.getPage(i)
                    val box = srcPage.mediaBox
                    pw = box.width
                    ph = box.height
                    val page = PDPage(PDRectangle(pw, ph))
                    sealed.addPage(page)

                    PDPageContentStream(sealed, page).use { cs ->
                        if (wmImg != null) {
                            val s = maxOf(pw / wmImg.width, ph / wmImg.height)
                            val gs = PDExtendedGraphicsState()
                            gs.nonStrokingAlphaConstant = WATERMARK_OPACITY
                            cs.saveGraphicsState()
                            cs.setGraphicsStateParameters(gs)
                            cs.drawImage(wmImg, (pw - wmImg.width * s) / 2f, (ph - wmImg.height * s) / 2f, wmImg.width * s, wmImg.height * s)
                            cs.restoreGraphicsState()
                        }

                        try {
                            val form = layerUtility.importPageAsForm(orig, i)
                            cs.saveGraphicsState()
                            cs.transform(Matrix.getTranslateInstance(pw * (1f - PAGE_SCALE) / 2f, ph * (1f - PAGE_SCALE) / 2f))
                            cs.transform(Matrix.getScaleInstance(PAGE_SCALE, PAGE_SCALE))
                            cs.drawForm(form)
                            cs.restoreGraphicsState()
                        } catch (e: Exception) {
                            // v1.2 per-page recovery: error notice instead of aborting the seal.
                            pageErrors++
                            cs.setNonStrokingColor(0.15f, 0.05f, 0.05f)
                            cs.addRect(pw * 0.1f, ph * 0.45f, pw * 0.8f, ph * 0.1f)
                            cs.fill()
                            drawText(cs, "! Page content could not be embedded", PDType1Font.HELVETICA_BOLD, 14f, 0.9f, 0.3f, 0.3f, pw * 0.15f, ph * 0.5f)
                            drawText(cs, "This page contained complex elements that could not be copied.", PDType1Font.HELVETICA, 10f, 0.7f, 0.5f, 0.5f, pw * 0.15f, ph * 0.46f)
                            drawText(cs, "The original file is preserved in the metadata. Seal ID: $sealId", PDType1Font.HELVETICA, 9f, 0.5f, 0.5f, 0.5f, pw * 0.15f, ph * 0.43f)
                        }

                        // Clean QR: top-right, 10% of page dimension, 2.5% margin, no border/box.
                        val qs = minOf(pw, ph) * QR_FRACTION
                        val m = minOf(pw, ph) * MARGIN_FRACTION
                        cs.drawImage(qrImg, pw - m - qs, ph - m - qs, qs, qs)

                        // Seal footer.
                        val fy = ph * 0.02f
                        val fs = minOf(pw, ph) * 0.018f
                        cs.saveGraphicsState()
                        val footerGs = PDExtendedGraphicsState()
                        footerGs.nonStrokingAlphaConstant = 0.85f
                        cs.setGraphicsStateParameters(footerGs)
                        cs.setNonStrokingColor(DARK[0], DARK[1], DARK[2])
                        cs.addRect(0f, 0f, pw, fy + fs * 3)
                        cs.fill()
                        cs.restoreGraphicsState()

                        val stLabel = if (options.sealType == "commercial" && options.org != null) {
                            "COMMERCIAL SEAL -- ${options.org.uppercase(Locale.US)}"
                        } else {
                            "PRIVATE SEAL -- FREE TIER"
                        }
                        val chainLabel = if (priorChain.isNotEmpty()) "  |  Chain: ${priorChain.size} prev" else ""
                        drawText(cs, stLabel + chainLabel, PDType1Font.HELVETICA_BOLD, fs * 0.8f, GOLD[0], GOLD[1], GOLD[2], pw * 0.03f, fy + fs * 1.8f)
                        drawText(cs, "Seal: $sealId  |  SHA-512: $short...  |  $ts  |  ${i + 1}/${orig.numberOfPages}", PDType1Font.HELVETICA, fs * 0.75f, SLATE[0], SLATE[1], SLATE[2], pw * 0.03f, fy + fs * 0.5f)
                        drawText(cs, "verumglobal.foundation  |  OpenTimestamps  |  Patent Pending", PDType1Font.HELVETICA, fs * 0.75f, MUTED[0], MUTED[1], MUTED[2], pw * 0.55f, fy + fs * 0.5f)
                    }
                }

                // Metadata (step 7) — the interoperable seal carrier.
                val useV2 = options.sealScheme == "v2"
                val info = sealed.documentInformation
                info.title = options.originalName
                info.author = "Verum Omnis"
                if (useV2) {
                    // VO-SEAL2: subject carries the fixed placeholder; the real
                    // sealed-file hash is patched in after save (see below).
                    // Forced hex form so the subject is stored as UTF-16BE-hex
                    // ASCII, exactly like the web sealer (pdf-lib).
                    val subject = SealChain.buildSubject(
                        sha512 = SealV2IntegrityChecker.HASH_PLACEHOLDER,
                        sealId = sealId,
                        previousChain = priorChain,
                        scheme = "v2",
                        origHash = sha512
                    )
                    val cosSubject = COSString(
                        byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + subject.toByteArray(Charsets.UTF_16BE)
                    )
                    cosSubject.setForceHexForm(true)
                    info.cosObject.setItem(COSName.getPDFName("Subject"), cosSubject)
                } else {
                    info.subject = SealChain.buildSubject(sha512, sealId, priorChain, scheme = "legacy")
                }
                info.keywords = SealChain.buildKeywords(sha512, options.sealType)
                info.producer = SealChain.PRODUCER
                info.creationDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = options.timestampMs
                }

                // Optional true AES-256 encryption (PDFBox enforces it; pdf-lib
                // on the web silently ignores it — see the module KDoc).
                if (password != null) {
                    val policy = StandardProtectionPolicy("", password, AccessPermission())
                    policy.setEncryptionKeyLength(256)
                    policy.setPreferAES(true)
                    sealed.protect(policy)
                }

                val out = ByteArrayOutputStream()
                sealed.save(out)
                out.toByteArray()
            }
        }

        // VO-SEAL2 self-integrity (matching the website): hash the sealed bytes
        // with the placeholder in place, then patch the real hash into the
        // hex-encoded Subject. Byte length is unchanged, so xref stays valid.
        var sealedFileSha512 = ""
        val finalBytes = if (options.sealScheme == "v2") {
            val (hash, patched) = SealV2IntegrityChecker.embedSealedHash(sealedBytes)
            sealedFileSha512 = hash
            patched
        } else {
            sealedBytes
        }

        return SealedDocument(
            sealedPdf = finalBytes,
            sealId = sealId,
            sha256 = sha256,
            sha512 = sha512,
            sealedSha512 = sealedFileSha512,
            sealScheme = options.sealScheme,
            verifyUrl = verifyUrl,
            metadata = meta,
            priorChain = priorChain,
            ots = ots,
            pageCount = totalPages,
            pageErrors = pageErrors,
            passwordProtected = password != null
        )
    }

    private fun drawCoverPage(
        doc: PDDocument,
        page: PDPage,
        wmImg: PDImageXObject?,
        sealId: String,
        senderEmail: String?
    ) {
        val pw = page.mediaBox.width
        val ph = page.mediaBox.height
        PDPageContentStream(doc, page).use { cs ->
            cs.setNonStrokingColor(DARK[0], DARK[1], DARK[2])
            cs.addRect(0f, 0f, pw, ph)
            cs.fill()

            if (wmImg != null) {
                val s = maxOf(pw / wmImg.width, ph / wmImg.height)
                val gs = PDExtendedGraphicsState()
                gs.nonStrokingAlphaConstant = 0.15f
                cs.saveGraphicsState()
                cs.setGraphicsStateParameters(gs)
                cs.drawImage(wmImg, (pw - wmImg.width * s) / 2f, (ph - wmImg.height * s) / 2f, wmImg.width * s, wmImg.height * s)
                cs.restoreGraphicsState()
            }

            val cx = pw / 2f
            val cy = ph * 0.65f
            // Simple lock glyph: gold body + shackle block.
            cs.setNonStrokingColor(GOLD[0], GOLD[1], GOLD[2])
            cs.addRect(cx - 30f, cy - 25f, 60f, 50f)
            cs.fill()
            cs.addRect(cx - 12f, cy + 25f, 24f, 20f)
            cs.fill()
            cs.setNonStrokingColor(DARK[0], DARK[1], DARK[2])
            cs.addRect(cx - 6f, cy + 25f, 12f, 12f)
            cs.fill()

            drawText(cs, "DOCUMENT PROTECTED", PDType1Font.HELVETICA_BOLD, 22f, GOLD[0], GOLD[1], GOLD[2], cx - 140f, cy - 70f)
            var lineY = cy - 110f
            val instructions = listOf(
                "This document has been password-protected by the sender.",
                "",
                "To open this document:",
                "1. Contact the sender to request the password",
                "2. The sender will know you received this document",
                "3. This serves as your delivery receipt"
            )
            for (line in instructions) {
                if (line.isNotEmpty()) {
                    drawText(cs, line, PDType1Font.HELVETICA, 11f, SLATE[0], SLATE[1], SLATE[2], cx - 140f, lineY)
                }
                lineY -= 18f
            }
            if (senderEmail != null) {
                lineY -= 10f
                drawText(cs, "Sender contact:", PDType1Font.HELVETICA_BOLD, 11f, GOLD[0], GOLD[1], GOLD[2], cx - 140f, lineY)
                lineY -= 18f
                drawText(cs, senderEmail, PDType1Font.HELVETICA, 12f, 0.95f, 0.95f, 0.95f, cx - 140f, lineY)
            }
            drawText(cs, "Seal: $sealId  |  verumglobal.foundation", PDType1Font.HELVETICA, 9f, MUTED[0], MUTED[1], MUTED[2], cx - 100f, ph * 0.03f)
        }
    }

    private fun drawText(
        cs: PDPageContentStream,
        text: String,
        font: PDType1Font,
        size: Float,
        r: Float,
        g: Float,
        b: Float,
        x: Float,
        y: Float
    ) {
        cs.beginText()
        cs.setFont(font, size)
        cs.setNonStrokingColor(r, g, b)
        cs.newLineAtOffset(x, y)
        cs.showText(sanitize(text))
        cs.endText()
    }

    /** WinAnsi-safe text: replace characters the standard Type1 fonts cannot encode. */
    private fun sanitize(text: String): String =
        text.replace('—', '-').replace('–', '-').replace('⚠', '!').replace("…", "...")

    private fun formatUtc(timestampMs: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(timestampMs) + " UTC"
    }

    /** QR code bitmap: error correction H, quiet-zone margin 2 — no border, no box. */
    private fun qrBitmap(content: String, size: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 2
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
