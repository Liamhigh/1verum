package com.verumomnis.forensic.acceptance

import com.verumomnis.forensic.core.*
import com.verumomnis.forensic.crypto.*
import com.verumomnis.forensic.engine.*
import com.verumomnis.forensic.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * End-to-end acceptance tests for Verum Omnis.
 * Validates complete user workflows from evidence ingestion through sealed report generation.
 */
class EndToEndAcceptanceTest {

    private lateinit var vault: EvidenceVault
    private lateinit var sealer: EvidenceSealer
    private lateinit var forensicService: ForensicService

    @Before
    fun setup() {
        vault = EvidenceVault.createInMemory()
        sealer = EvidenceSealer()
        forensicService = ForensicService()
    }

    // WORKFLOW 1: Import PDF → Seal → Verify Seal

    @Test
    fun `WORKFLOW 1 import PDF and compute SHA-512 hash`() {
        val pdfContent = loadSampleEvidence("mou_clause_7")
        val artifact = vault.ingest(pdfContent, filename = "mou_clause_7.pdf")

        assertNotNull("Hash must be computed at ingestion", artifact.hash)
        assertEquals("Hash must be 128 hex chars", 128, artifact.hash.length)
        assertEquals("ID must be first 16 bytes of hash", artifact.hash.take(32), artifact.id)
    }

    @Test
    fun `WORKFLOW 1 seal evidence cryptographically`() {
        val artifact = vault.ingest(loadSampleEvidence("mou_clause_7"), filename = "mou.pdf")
        val seal = sealer.seal(artifact)

        assertNotNull("Seal must have ID", seal.id)
        assertTrue("Seal ID must start with seal-", seal.id.startsWith("seal-"))
        assertNotNull("Seal must have hash", seal.hash)
        assertNotNull("Seal must have timestamp", seal.timestamp)
        assertEquals("v5.2.8", seal.constitutionVersion)
    }

    @Test
    fun `WORKFLOW 1 verify seal detects tampering`() {
        val artifact = vault.ingest(loadSampleEvidence("mou_clause_7"), filename = "mou.pdf")
        val seal = sealer.seal(artifact)

        assertEquals(SealStatus.VERIFIED, sealer.verify(seal, artifact))

        val tampered = artifact.copy(content = artifact.content.copyOf().also { it[0] = (it[0] + 1).toByte() })
        assertEquals(SealStatus.TAMPERED, sealer.verify(seal, tampered))
    }

    // WORKFLOW 2: Contradiction Engine → Report → Evidence References

    @Test
    fun `WORKFLOW 2 contradiction engine finds contradictions`() {
        val evidence = loadAllFuelsEvidenceSet()
        val b1 = ContradictionBrain()
        val result = b1.analyze(evidence)

        assertTrue("Must find at least one contradiction", result.findings.isNotEmpty())
        result.findings.forEach { f ->
            assertNotNull("Finding must have person anchor", f.anchorPerson)
            assertTrue("Page must be > 0", f.anchorPage > 0)
        }
    }

    @Test
    fun `WORKFLOW 2 report references correct evidence`() {
        val evidence = loadAllFuelsEvidenceSet()
        val scanResult = forensicService.scan(evidence, Instant.parse("2026-07-13T00:00:00Z"))
        val report = ReportGenerator.generate(scanResult)

        scanResult.findings.forEach { finding ->
            assertTrue("Report must contain finding ${finding.id}", report.containsFinding(finding.id))
        }
    }

    @Test
    fun `WORKFLOW 2 report has seal footer on every page`() {
        val evidence = loadMinimalEvidenceSet()
        val report = ReportGenerator.generate(forensicService.scan(evidence, Instant.now()))
        val pages = report.getPages()

        assertTrue("Report must have pages", pages.isNotEmpty())
        pages.forEachIndexed { i, page ->
            assertTrue("Page ${i + 1} must have seal footer", page.hasSealFooter())
        }
    }

    @Test
    fun `WORKFLOW 2 report is deterministic`() {
        val evidence = loadMinimalEvidenceSet()
        val fixedTime = Instant.parse("2026-07-13T00:00:00Z")
        val r1 = ReportGenerator.generate(forensicService.scan(evidence, fixedTime))
        val r2 = ReportGenerator.generate(forensicService.scan(evidence, fixedTime))
        assertEquals(r1.hash, r2.hash)
    }

    // WORKFLOW 3: Full Forensic Scan

    @Test
    fun `WORKFLOW 3 full scan produces sealed ScanResult`() {
        val evidence = loadAllFuelsEvidenceSet()
        val result = forensicService.scan(evidence, Instant.now())

        assertNotNull("Must have seal ID", result.sealId)
        assertNotNull("Must have content hash", result.contentHash)
        assertEquals("v5.2.8", result.constitutionVersion)
    }

    @Test
    fun `WORKFLOW 3 scan produces constitutional categories`() {
        val evidence = loadAllFuelsEvidenceSet()
        val result = forensicService.scan(evidence, Instant.now())
        val categories = result.findings.map { it.category }.toSet()

        assertTrue("Should find Goodwill contradictions",
            categories.contains(ContradictionCategory.GOODWILL_VALUE))
        assertTrue("Should find Contract contradictions",
            categories.contains(ContradictionCategory.CONTRACT_VALIDITY))
    }

    @Test
    fun `WORKFLOW 3 scan applies triple verification`() {
        val evidence = loadAllFuelsEvidenceSet()
        val result = forensicService.scan(evidence, Instant.now())

        result.findings.forEach { f ->
            assertNotNull("Finding must have verification", f.verification)
        }
    }

    // PERFORMANCE

    @Test
    fun `PERFORMANCE hash 1MB file under 1 second`() {
        val data = ByteArray(1024 * 1024) { (it % 256).toByte() }
        val start = System.currentTimeMillis()
        Sha512.hash(data)
        assertTrue("Hash took ${System.currentTimeMillis() - start}ms", System.currentTimeMillis() - start < 1000)
    }

    // Helpers

    private fun loadSampleEvidence(name: String): ByteArray {
        return javaClass.classLoader.getResourceAsStream("evidence/$name.txt")?.readBytes()
            ?: "Sample evidence content for $name".toByteArray()
    }

    private fun loadAllFuelsEvidenceSet(): List<EvidenceArtifact> {
        return listOf(
            EvidenceArtifact.create(loadSampleEvidence("mou_clause_7"), "mou.pdf", Instant.now()),
            EvidenceArtifact.create(loadSampleEvidence("sworn_testimony"), "testimony.pdf", Instant.now()),
            EvidenceArtifact.create(loadSampleEvidence("rent_ledger"), "rent.csv", Instant.now()),
            EvidenceArtifact.create(loadSampleEvidence("email_chain"), "emails.txt", Instant.now()),
            EvidenceArtifact.create(loadSampleEvidence("goodwill_valuation"), "valuation.pdf", Instant.now())
        )
    }

    private fun loadMinimalEvidenceSet(): List<EvidenceArtifact> {
        return listOf(
            EvidenceArtifact.create("MOU Clause 7".toByteArray(), "mou.pdf", Instant.now()),
            EvidenceArtifact.create("Sworn testimony".toByteArray(), "testimony.pdf", Instant.now())
        )
    }
}
