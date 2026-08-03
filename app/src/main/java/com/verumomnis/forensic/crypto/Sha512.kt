package com.verumomnis.forensic.crypto

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** SHA-512 fingerprinting (spec 6.1). Returns a 128-character lowercase hex string. */
object Sha512 {

    /** Buffer for the streaming variants — 64 KB balances syscalls against footprint. */
    private const val BUFFER_BYTES = 1 shl 16

    fun hash(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-512")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun hash(text: String): String = hash(text.toByteArray(Charsets.UTF_8))

    /**
     * Hashes a file without loading it into memory.
     *
     * Evidence bundles run to hundreds of megabytes; holding one as a ByteArray
     * just to fingerprint it is what put the seal path within reach of an
     * OutOfMemoryError. Peak cost here is [BUFFER_BYTES] regardless of size.
     */
    fun hash(file: File): String = file.inputStream().use { hash(it) }

    /** Hashes a stream, consuming it. Peak cost is [BUFFER_BYTES]. */
    fun hash(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-512")
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Copies [input] to [output] and fingerprints it in the same pass.
     *
     * One read of the source produces both the stored copy and its hash, so a
     * large artifact is never resident in memory and never read twice.
     *
     * @return the SHA-512 of everything copied, and the byte count.
     */
    fun copyAndHash(input: InputStream, output: OutputStream): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-512")
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            total += read
        }
        output.flush()
        return digest.digest().joinToString("") { "%02x".format(it) } to total
    }
}
