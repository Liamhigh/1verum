package com.verumomnis.forensic

import com.verumomnis.forensic.crypto.Sha512
import org.junit.Assert.assertEquals
import org.junit.Test

class Sha512Test {

    @Test
    fun knownVectorAbc() {
        val expected =
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a" +
                "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f"
        assertEquals(expected, Sha512.hash("abc"))
    }

    @Test
    fun producesOneHundredTwentyEightHexChars() {
        val hash = Sha512.hash("verum omnis".toByteArray())
        assertEquals(128, hash.length)
        assert(hash.all { it in "0123456789abcdef" })
    }

    @Test
    fun deterministic() {
        assertEquals(Sha512.hash("evidence"), Sha512.hash("evidence"))
    }
}
