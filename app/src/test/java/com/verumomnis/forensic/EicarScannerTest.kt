package com.verumomnis.forensic

import com.verumomnis.forensic.security.EicarScanner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EicarScannerTest {

    @Test
    fun detectsEicarString() {
        val eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}\$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!\$H+H*"
        assertTrue(EicarScanner.isEicar(eicar))
        assertTrue(EicarScanner.isEicar(eicar.toByteArray()))
    }

    @Test
    fun doesNotFlagNormalText() {
        val normal = "This is a legitimate forensic statement about the AllFuels matter."
        assertFalse(EicarScanner.isEicar(normal))
        assertFalse(EicarScanner.isEicar(normal.toByteArray()))
    }
}
