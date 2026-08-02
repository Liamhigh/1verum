package com.verumomnis.forensic.engine.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Downloads on-device model files, verifying SHA-256 before they're ever handed to
 * [LlamaModel.load] (ON_DEVICE_LLM_ARCHITECTURE.md section 6: "If a model fails
 * verification, the app refuses to load it and prompts for re-download").
 */
class ModelDownloadManager(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) {
    data class ModelSpec(
        val id: String,
        val displayName: String,
        val url: String,
        val sha256: String,
        val sizeBytes: Long
    )

    fun modelFile(spec: ModelSpec): File = File(modelsDir(), "${spec.id}.gguf")

    /** True only if the file exists AND its SHA-256 matches — a size-only check isn't enough. */
    fun isVerified(spec: ModelSpec): Boolean {
        val file = modelFile(spec)
        return file.exists() && file.length() == spec.sizeBytes && verifyHash(file, spec.sha256)
    }

    /**
     * Downloads and verifies [spec], reporting 0f..1f progress. Returns the verified file on
     * success. On network failure or a hash mismatch, the partial/corrupt download is deleted
     * and this returns null — it never hands back an unverified file.
     */
    suspend fun download(spec: ModelSpec, onProgress: (Float) -> Unit = {}): File? =
        withContext(Dispatchers.IO) {
            if (isVerified(spec)) return@withContext modelFile(spec)

            val target = modelFile(spec)
            val tmp = File(target.parentFile, "${target.name}.part")
            target.parentFile?.mkdirs()

            // Resume from a previous partial rather than restarting. These files are
            // ~2.5 GB: losing 90% of a download to one dropped connection is the
            // difference between "retry" and "give up", especially on mobile data.
            val already = if (tmp.exists()) tmp.length() else 0L
            val builder = Request.Builder().url(spec.url)
            if (already > 0) builder.header("Range", "bytes=$already-")

            try {
                client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null

                    // 206 means the server honoured the range and we append. Any 200
                    // after requesting a range means it ignored us and is resending
                    // from zero, so the partial must be discarded to avoid a corrupt
                    // file that would only be caught later by the hash check.
                    val resuming = already > 0 && response.code == HTTP_PARTIAL
                    val startedAt = if (resuming) already else 0L
                    val total = body.contentLength().takeIf { it > 0 }?.plus(startedAt)
                        ?: spec.sizeBytes

                    body.byteStream().use { input ->
                        java.io.FileOutputStream(tmp, resuming).use { output ->
                            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                            var readTotal = startedAt
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                readTotal += read
                                onProgress((readTotal.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep the partial so the next attempt resumes instead of restarting.
                // A truncated file is harmless: it is never renamed to the real path
                // and never loaded until the SHA-256 check below passes.
                return@withContext null
            }

            if (!verifyHash(tmp, spec.sha256)) {
                tmp.delete()
                return@withContext null
            }
            tmp.renameTo(target)
            target
        }

    private fun modelsDir(): File = File(context.filesDir, "models")

    private fun verifyHash(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return hex.equals(expectedSha256, ignoreCase = true)
    }

    private companion object {
        /** HTTP 206 Partial Content — the server honoured our Range request. */
        const val HTTP_PARTIAL = 206
        const val DOWNLOAD_BUFFER_BYTES = 1 shl 16
    }
}
