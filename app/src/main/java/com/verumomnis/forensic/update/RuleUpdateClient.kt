package com.verumomnis.forensic.update

import com.verumomnis.forensic.core.Constitution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigDecimal
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit

/**
 * Signed rule-update client (rule-format.md v1).
 *
 * Downloads the signed rule manifest from the Verum rules service, canonicalizes
 * the embedded package (recursive key sort, compact separators, UTF-8, raw
 * non-ASCII — exactly the bytes the server signed), verifies the RSA-4096
 * `SHA512withRSA` signature against the pinned public key
 * ([Constitution.RULES_PUBLIC_KEY_DER_B64], publicKeyId `vo-master-1`), and
 * persists the package via [RuleRegistry] ONLY when the signature is valid AND
 * the package version is strictly newer than the installed one.
 *
 * A package that fails ANY check (network, malformed JSON, unknown key id,
 * bad signature, stale version) is NEVER applied; the previously installed
 * rules (if any) remain in force untouched.
 *
 * The companion's pure functions ([canonicalJson], [isNewerVersion],
 * [verifySignature], [parseVersion]) carry no Android dependencies so they are
 * exercised directly by JVM unit tests.
 */
class RuleUpdateClient(
    private val registry: RuleRegistry,
    private val manifestUrl: String = Constitution.RULE_MANIFEST_URL,
    private val publicKeyDerB64: String = Constitution.RULES_PUBLIC_KEY_DER_B64,
    private val httpClient: OkHttpClient = defaultClient()
) {

    /** Typed outcome of one update check. Every failure mode is explicit and honest. */
    sealed class UpdateResult {
        abstract val message: String

        /** Verified + newer package was persisted and is now the active ruleset. */
        data class Applied(val version: String, val publishedAt: String) : UpdateResult() {
            override val message: String
                get() = "Rule package v$version signature-verified and applied (published $publishedAt)"
        }

        /** Signature was valid but the package is not newer than the installed one; nothing applied. */
        data class Stale(val remoteVersion: String, val currentVersion: String?) : UpdateResult() {
            override val message: String
                get() = "Remote rules v$remoteVersion are not newer than installed " +
                    "v${currentVersion ?: "none"}; not applied"
        }

        /** Signature did not verify against the pinned public key; package rejected. */
        data class BadSignature(val detail: String = "") : UpdateResult() {
            override val message: String
                get() = "Rule package signature verification failed" +
                    (if (detail.isNotEmpty()) ": $detail" else "") + "; package rejected"
        }

        /** Manifest/package JSON was structurally invalid or used an unknown key/algorithm. */
        data class Malformed(val detail: String) : UpdateResult() {
            override val message: String get() = "Rule manifest malformed: $detail; package rejected"
        }

        /** Transport-level failure (DNS, TLS, timeout, non-2xx). */
        data class Network(val detail: String) : UpdateResult() {
            override val message: String get() = "Rule update fetch failed: $detail"
        }
    }

    /**
     * Runs one update check on [Dispatchers.IO]. Never throws for expected
     * failure modes; always returns a typed [UpdateResult].
     */
    suspend fun checkForUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        // 1. Fetch the manifest.
        val body = try {
            val request = Request.Builder().url(manifestUrl).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateResult.Network("HTTP ${response.code} from rules service")
                }
                response.body?.string()
                    ?: return@withContext UpdateResult.Network("empty response body")
            }
        } catch (e: Exception) {
            return@withContext UpdateResult.Network(e.message ?: e.javaClass.simpleName)
        }

        // 2. Parse the manifest envelope.
        val manifest = (try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
            null
        }) ?: return@withContext UpdateResult.Malformed("manifest is not a JSON object")

        val pkg = manifest["package"] as? JsonObject
            ?: return@withContext UpdateResult.Malformed("missing 'package' object")
        val signatureB64 = (manifest["signature"] as? JsonPrimitive)
            ?.takeIf { it.isString }?.content
            ?: return@withContext UpdateResult.Malformed("missing 'signature'")
        val algorithm = (manifest["algorithm"] as? JsonPrimitive)?.contentOrNull
        if (algorithm != EXPECTED_ALGORITHM) {
            return@withContext UpdateResult.Malformed(
                "unexpected algorithm '$algorithm' (expected '$EXPECTED_ALGORITHM')"
            )
        }
        val keyId = (manifest["publicKeyId"] as? JsonPrimitive)?.contentOrNull
        if (keyId != EXPECTED_KEY_ID) {
            return@withContext UpdateResult.Malformed(
                "unknown publicKeyId '$keyId' (this build pins '$EXPECTED_KEY_ID')"
            )
        }

        // 3. Structural sanity of the package itself.
        val version = (pkg["version"] as? JsonPrimitive)?.contentOrNull
            ?: return@withContext UpdateResult.Malformed("package has no 'version'")
        if (parseVersion(version) == null) {
            return@withContext UpdateResult.Malformed("package version '$version' is not semver x.y.z")
        }
        val publishedAt = (pkg["published_at"] as? JsonPrimitive)?.contentOrNull ?: ""
        if (pkg["rules"] !is JsonObject) {
            return@withContext UpdateResult.Malformed("package has no 'rules' object")
        }

        // 4. Canonicalize + verify BEFORE trusting any package content.
        val canonicalBytes = canonicalJson(pkg).toByteArray(Charsets.UTF_8)
        if (!verifySignature(canonicalBytes, signatureB64, publicKeyDerB64)) {
            return@withContext UpdateResult.BadSignature()
        }

        // 5. Accept only a strictly newer version than the installed one.
        val installed = registry.currentVersion
        if (!isNewerVersion(version, installed)) {
            return@withContext UpdateResult.Stale(version, installed)
        }

        // 6. Persist atomically; the new rules take effect from here on.
        registry.persist(packageJson = pkg.toString(), version = version, publishedAt = publishedAt)
        UpdateResult.Applied(version, publishedAt)
    }

    companion object {
        const val EXPECTED_ALGORITHM = "RSASSA-PKCS1-v1_5-SHA512"
        const val EXPECTED_KEY_ID = "vo-master-1"

        private val SEMVER_REGEX = Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$")

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /**
         * Canonical JSON per rule-format.md v1 — the exact signed bytes:
         * UTF-8, compact separators, object keys sorted recursively at every
         * depth (same order as JavaScript's `sort()` on keys, i.e. by UTF-16
         * code unit, which is what Kotlin's natural String ordering also does),
         * array order preserved, strings quoted with JSON.stringify semantics
         * (only `"`, `\` and control characters escaped; non-ASCII emitted raw),
         * integers emitted without a decimal point or exponent.
         */
        fun canonicalJson(element: JsonElement): String = when (element) {
            is JsonNull -> "null"
            is JsonObject -> buildString {
                append('{')
                element.keys.sorted().forEachIndexed { index, key ->
                    if (index > 0) append(',')
                    append(quoteJsonString(key))
                    append(':')
                    append(canonicalJson(element.getValue(key)))
                }
                append('}')
            }
            is JsonArray -> element.joinToString(prefix = "[", postfix = "]", separator = ",") {
                canonicalJson(it)
            }
            is JsonPrimitive ->
                if (element.isString) quoteJsonString(element.content)
                else canonicalNumberOrLiteral(element.content)
        }

        /**
         * JSON string quoting identical to JavaScript `JSON.stringify`:
         * escapes `"` and `\`, uses the short escapes \b \t \n \f \r, emits
         * other control characters (< U+0020) as lowercase \u00xx, and emits
         * EVERYTHING else — including all non-ASCII — as raw characters.
         * (This intentionally differs from org.json's `quote()`, which escapes
         * U+007F–U+009F and U+2000–U+20FF and would corrupt the signed bytes.)
         */
        fun quoteJsonString(value: String): String = buildString(value.length + 2) {
            append('"')
            for (c in value) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\t' -> append("\\t")
                    '\n' -> append("\\n")
                    '' -> append("\\f")
                    '\r' -> append("\\r")
                    else ->
                        if (c < ' ') append("\\u").append(c.code.toString(16).padStart(4, '0'))
                        else append(c)
                }
            }
            append('"')
        }

        /**
         * Numbers: v1 packages contain only integers, emitted without a decimal
         * point or exponent (matching JSON.stringify of integral values, e.g.
         * `5`, `1000`). Integral values are normalized through BigDecimal;
         * non-integral literals are passed through unchanged (not present in v1).
         */
        private fun canonicalNumberOrLiteral(raw: String): String = when (raw) {
            "true", "false" -> raw
            else -> try {
                val stripped = BigDecimal(raw).stripTrailingZeros()
                if (stripped.scale() <= 0) stripped.toBigIntegerExact().toString() else raw
            } catch (e: NumberFormatException) {
                raw
            }
        }

        /**
         * Verifies `signatureB64` (RSASSA-PKCS1-v1_5 with SHA-512, JCA name
         * `SHA512withRSA`) over [canonicalBytes] using the pinned SPKI/DER
         * RSA public key. Returns false for ANY failure to verify — mismatch,
         * truncated or undecodable signature/key material, provider error.
         * An unverifiable package is always a rejection, never an exception.
         */
        fun verifySignature(
            canonicalBytes: ByteArray,
            signatureB64: String,
            publicKeyDerB64: String
        ): Boolean = try {
            val der = java.util.Base64.getDecoder().decode(publicKeyDerB64)
            val publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(der))
            val signatureBytes = java.util.Base64.getDecoder().decode(signatureB64)
            val verifier = Signature.getInstance("SHA512withRSA")
            verifier.initVerify(publicKey)
            verifier.update(canonicalBytes)
            verifier.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }

        /** Parses strict `x.y.z` semver into [major, minor, patch], or null. */
        fun parseVersion(version: String): LongArray? =
            SEMVER_REGEX.matchEntire(version.trim())?.let { m ->
                longArrayOf(
                    m.groupValues[1].toLong(),
                    m.groupValues[2].toLong(),
                    m.groupValues[3].toLong()
                )
            }

        /**
         * True iff [remote] is a valid semver strictly newer than [installed]
         * (any valid remote is newer when nothing is installed yet).
         */
        fun isNewerVersion(remote: String, installed: String?): Boolean {
            val r = parseVersion(remote) ?: return false
            val i = installed?.let { parseVersion(it) } ?: return true
            for (idx in 0..2) {
                if (r[idx] != i[idx]) return r[idx] > i[idx]
            }
            return false
        }
    }
}
