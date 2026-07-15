package com.verumomnis.forensic.security

/**
 * Lightweight malware-pattern hook.
 *
 * Detects the EICAR anti-malware test pattern so the app can block known test
 * malware without adding an external antivirus dependency. Only the exact
 * standard test string is flagged; this is not a general malware scanner.
 */
object EicarScanner {

    // The canonical 68-byte EICAR test string.
    private const val EICAR =
        "X5O!P%@AP[4\\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*"

    fun isEicar(bytes: ByteArray): Boolean =
        bytes.size >= EICAR.length && String(bytes, Charsets.UTF_8).contains(EICAR)

    fun isEicar(text: String): Boolean = text.contains(EICAR)
}
