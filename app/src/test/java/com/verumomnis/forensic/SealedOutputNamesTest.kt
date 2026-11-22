package com.verumomnis.forensic

import com.verumomnis.forensic.pdf.SealedOutputNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SealedOutputNamesTest {
    @Test
    fun preservesReadableSourceNameAndRemovesRepeatedPdfExtensions() {
        assertEquals("Greensky Case File", SealedOutputNames.sourceStem("Greensky Case File.PDF.pdf"))
        assertEquals("Evidence Bundle", SealedOutputNames.sourceStem("C:\\Downloads\\Evidence Bundle.pdf"))
    }

    @Test
    fun stripsPathTraversalAndUnsafeFilenameCharacters() {
        val stem = SealedOutputNames.sourceStem("../../private:/Case?.pdf")
        assertFalse(stem.contains('/'))
        assertFalse(stem.contains('\\'))
        assertFalse(stem.contains(':'))
        assertFalse(stem.contains('?'))
        assertTrue(stem.isNotBlank())
    }

    @Test
    fun reportAndSealNamesIncludeTheUploadAndCollisionSuffixDoesNotOverwrite() {
        val report = SealedOutputNames.forensicReport("Matter A.pdf", "VO-VAULT-123")
        assertEquals("Matter A_forensic_report_VO-VAULT-123.pdf", report)
        assertEquals("Matter A_sealed_VO-123_42.pdf", SealedOutputNames.sealedDocument("Matter A.pdf", "VO-123", 42))

        val directory = Files.createTempDirectory("sealed-name-test").toFile()
        try {
            val first = SealedOutputNames.createUniqueFile(directory, report)
            val second = SealedOutputNames.createUniqueFile(directory, report)
            assertEquals("Matter A_forensic_report_VO-VAULT-123.pdf", first.name)
            assertEquals("Matter A_forensic_report_VO-VAULT-123_2.pdf", second.name)
        } finally {
            directory.deleteRecursively()
        }
    }
}
