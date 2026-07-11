package com.verumomnis.forensic.crypto

import java.security.MessageDigest

/** SHA-512 fingerprinting (spec 6.1). Returns a 128-character lowercase hex string. */
object Sha512 {
    fun hash(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-512")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun hash(text: String): String = hash(text.toByteArray(Charsets.UTF_8))
}
