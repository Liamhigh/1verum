package com.verumomnis.forensic.seal

import java.net.URLDecoder

/**
 * VO-DSS-1.2 — Parsed QR verify-URL payload.
 *
 * A Verum seal QR code encodes:
 *   https://verumglobal.foundation/verify.html?h=<SHA512_PREFIX_32>&m=<URL_SAFE_BASE64(JSON)>
 *
 * This class parses the URL, extracts the SHA-512 prefix, and decodes the
 * embedded metadata via [SealMetadataCodec]. All parsing is pure Kotlin so it
 * is fully unit-testable off-device.
 */
data class QrScanPayload(
    val rawUrl: String,
    val hashPrefix: String,
    val metadata: SealMetadataCodec.SealMetadata?,
    val metadataSource: String
) {
    companion object {
        private const val BASE_URL = "https://verumglobal.foundation/verify.html"
        private val HASH_PARAM = Regex("[?&]h=([0-9a-fA-F]{32})")
        private val META_PARAM = Regex("[?&]m=([^&]+)")

        /**
         * Parse a scanned QR URL string.
         *
         * @return [QrScanPayload] with the hash prefix and decoded metadata (if present).
         * @throws IllegalArgumentException if the URL is not a Verum verify URL
         *         or the hash prefix is malformed.
         */
        fun parse(url: String): QrScanPayload {
            require(url.startsWith(BASE_URL) || url.contains("/verify.html")) {
                "Not a Verum Omnis verify URL: $url"
            }

            val hashMatch = HASH_PARAM.find(url)
                ?: throw IllegalArgumentException("Missing or malformed 'h' parameter in QR URL")
            val hashPrefix = hashMatch.groupValues[1].lowercase()

            val metaMatch = META_PARAM.find(url)
            val metadata = metaMatch?.let {
                val encoded = URLDecoder.decode(it.groupValues[1], "UTF-8")
                SealMetadataCodec.decode(encoded)
            }

            return QrScanPayload(
                rawUrl = url,
                hashPrefix = hashPrefix,
                metadata = metadata,
                metadataSource = metaMatch?.groupValues?.get(1) ?: ""
            )
        }

        /**
         * Tolerant parser that returns null instead of throwing on invalid input.
         * Useful when the scanner may feed non-Verum QR codes.
         */
        fun parseOrNull(url: String): QrScanPayload? = try {
            parse(url)
        } catch (_: Exception) {
            null
        }
    }
}
