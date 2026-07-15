package com.verumomnis.forensic.seal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64

/**
 * VO-DSS-1.2 — Seal metadata schema + QR verify-URL format.
 * The QR code on every sealed page encodes:
 *   https://verumglobal.foundation/verify.html?h=<SHA512_PREFIX_32>&m=<BASE64_METADATA>
 * where BASE64_METADATA = encodeURIComponent(base64(JSON.stringify(meta))).
 * Byte-compatible with the web sealer and the Firewall port for ASCII content.
 */
object SealMetadataCodec {

    const val SEAL_FORMAT_VERSION = "1.2"
    const val VERIFY_BASE_URL = "https://verumglobal.foundation/verify.html"

    /** Optional sender identity — encoded in QR metadata only, never stored server-side. */
    @Serializable
    data class SealIdentity(
        @SerialName("n") val n: String? = null, // full name
        @SerialName("id") val id: String? = null, // ID / passport number
        @SerialName("a") val a: String? = null, // physical address
        @SerialName("e") val e: String? = null // contact email
    )

    /** QR metadata schema. Declaration order matches the web collectSealMetadata(). */
    @Serializable
    data class SealMetadata(
        @SerialName("v") val v: String,
        @SerialName("t") val t: Long,
        @SerialName("id") val id: SealIdentity? = null,
        @SerialName("lock") val lock: Boolean? = null,
        @SerialName("gps") val gps: String? = null,
        @SerialName("acc") val acc: Int? = null,
        @SerialName("dev") val dev: String? = null,
        @SerialName("type") val type: String,
        @SerialName("org") val org: String? = null
    )

    /** Inputs to the collector. Time and device/GPS are injected by the caller. */
    data class SealMetadataInput(
        val timestampMs: Long,
        val sealType: String, // "private" | "commercial"
        val org: String? = null,
        val identity: SealIdentity? = null,
        val lock: Boolean = false,
        val gpsLat: String? = null,
        val gpsLng: String? = null,
        val gpsAccuracyM: Int? = null,
        val device: String? = null // pre-formatted "Platform|Cores|Timezone"
    )

    private val json = Json { encodeDefaults = false }

    /** Build the metadata object exactly as the web collectSealMetadata() does. */
    fun collect(input: SealMetadataInput): SealMetadata {
        val id = input.identity?.takeIf { it.n != null || it.id != null || it.a != null || it.e != null }
        return SealMetadata(
            v = SEAL_FORMAT_VERSION,
            t = input.timestampMs,
            id = id,
            lock = if (input.lock) true else null,
            gps = if (input.gpsLat != null && input.gpsLng != null) "${input.gpsLat},${input.gpsLng}" else null,
            acc = input.gpsAccuracyM,
            dev = input.device,
            type = input.sealType,
            org = if (input.sealType == "commercial") input.org else null
        )
    }

    /** Encode metadata for the QR/verify URL: encodeURIComponent(base64(JSON)). */
    fun encode(meta: SealMetadata): String {
        val jsonStr = json.encodeToString(meta)
        val b64 = Base64.getEncoder().encodeToString(jsonStr.toByteArray(Charsets.UTF_8))
        return URLEncoder.encode(b64, "UTF-8")
    }

    /** Tolerant decode of the `m` parameter (percent-decoded base64 JSON). */
    fun decode(encoded: String): SealMetadata? = try {
        val b64 = URLDecoder.decode(encoded, "UTF-8")
        val jsonStr = String(Base64.getDecoder().decode(b64), Charsets.UTF_8)
        json.decodeFromString<SealMetadata>(jsonStr)
    } catch (e: Exception) {
        null
    }

    /** Build the QR / verify URL: ?h=<first 32 hex of SHA-512>&m=<encoded metadata>. */
    fun buildVerifyUrl(sha512: String, meta: SealMetadata? = null): String {
        var url = "$VERIFY_BASE_URL?h=${sha512.substring(0, 32)}"
        if (meta != null) url += "&m=${encode(meta)}"
        return url
    }
}
