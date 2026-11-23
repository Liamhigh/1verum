package com.verumomnis.forensic

import com.verumomnis.forensic.engine.ContradictionExtractor
import com.verumomnis.forensic.engine.EvidenceDocument
import com.verumomnis.forensic.engine.FindingsJsonEmitter
import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.engine.JurisdictionService
import com.verumomnis.forensic.engine.ReportGenerator
import com.verumomnis.forensic.engine.EntityExtractor
import com.verumomnis.forensic.vault.EvidenceVault
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import com.verumomnis.forensic.model.ContradictionCategory
import com.verumomnis.forensic.model.ForensicFindings
import com.verumomnis.forensic.model.GpsRecord
import com.verumomnis.forensic.model.JurisdictionSource
import com.verumomnis.forensic.model.PageExtraction
import com.verumomnis.forensic.model.PageReadStatus
import com.verumomnis.forensic.model.PdfExtractionCoverage
import com.verumomnis.forensic.model.StatementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.time.Instant

class EvidenceTraceabilityTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val now = Instant.parse("2026-07-06T14:33:00Z")
    private val gps = GpsRecord(-30.7667, 30.4, timestamp = now.toString())

    private fun pdf(id: String, name: String, page: Int, pageText: String, text: String) = EvidenceDocument(
        evidenceId = id,
        fileName = name,
        type = "pdf",
        text = text,
        sha512 = id.first().lowercaseChar().toString().repeat(128),
        gps = gps,
        extractionCoverage = PdfExtractionCoverage(
            totalPages = page,
            pages = listOf(PageExtraction(
                page = page,
                status = PageReadStatus.TEXT_LAYER,
                reason = "embedded_text",
                extractedChars = pageText.length,
                extractedText = pageText
            ))
        )
    )

    private fun evidence() = listOf(
        pdf(
            "DOC001", "statement-a.pdf", 2,
            "From: Morgan Lee\nThe transaction fell through.",
            "From: Morgan Lee\nThe transaction fell through."
        ),
        pdf(
            "DOC002", "statement-b.pdf", 4,
            "From: Pat Taylor\nThe transaction proceeded with the deal.",
            "From: Pat Taylor\nThe transaction proceeded with the deal."
        )
    )

    @Test
    fun personNamesNeedHumanContextAndOrganizationsAndHeadingsAreExcluded() {
        val docs = listOf(EvidenceDocument(
            evidenceId = "DOC",
            fileName = "fixture.txt",
            type = "text",
            text = "FORENSIC ANALYSIS REPORT\nFrom: AllFuels\nFrom: Acme Holdings\nDear Mr Fiction\nFrom: Morgan Lee\nMorgan Lee denied the contract existed.",
            sha512 = "a".repeat(128)
        ))

        val names = EntityExtractor.extractEntities(docs).people
        assertTrue(names.contains("Morgan Lee"))
        assertFalse(names.contains("AllFuels"))
        assertFalse(names.contains("Acme Holdings"))
        assertFalse(names.contains("FORENSIC ANALYSIS"))
        assertFalse(names.contains("Mr Fiction"))
        assertEquals(listOf("Morgan Lee"), EntityExtractor.extractPersons(docs).map { it.name })
    }

    @Test
    fun claimsUseOriginalPageTextAndPreserveQuotedStatements() {
        val contradictions = ContradictionExtractor.extract(evidence(), now)
        val transaction = contradictions.first { it.claimA.text.contains("fell through") }

        assertEquals(2, transaction.claimA.page)
        assertEquals(4, transaction.claimB.page)
        assertEquals("The transaction fell through.", transaction.claimA.text)
        assertEquals("The transaction proceeded with the deal.", transaction.claimB.text)
        assertEquals(2, transaction.claimA.line)
        assertEquals(2, transaction.claimB.line)
        assertEquals(StatementType.CLAIM, transaction.claimA.statementType)
    }

    @Test
    fun findingsJsonAndReportShareEvidencePersonJurisdictionAndLegalReview() {
        val scan = ForensicService.scan(evidence(), now)
        val findings = scan.findings
        val c = findings.contradictions.first { it.claimA.text.contains("fell through") }

        assertEquals(2, c.claimA.page)
        assertEquals(4, c.claimB.page)
        assertEquals("Pat Taylor", c.respondent)
        assertEquals("IDENTIFIED_FROM_EXPLICIT_SOURCE_ATTRIBUTION", c.personLinkStatus)
        assertEquals(findings.jurisdiction, c.jurisdiction)
        assertTrue(c.legalReferences.isEmpty()) // transaction category has no supported statute mapping

        val record = FindingsJsonEmitter.contradictionToRecord(c)
        assertEquals(c.respondent, record.respondent)
        assertEquals(c.personLinkStatus, record.person_link_status)
        assertEquals(c.jurisdiction, record.jurisdiction)
        assertEquals(c.claimA.text, record.claim_a_quote)
        assertEquals(c.claimA.page, record.claim_a_page)
        assertEquals(c.claimB.text, record.claim_b_quote)
        assertEquals(c.claimB.page, record.claim_b_page)
        assertTrue(record.legal_references.isEmpty())

        val report = ReportGenerator.generate(
            findings.copy(contradictions = findings.contradictions.map {
                if (it.contradictionId == c.contradictionId) it.copy(councilStatus = "ACCEPTED") else it
            }),
            "SyntheticTrace",
            now
        )
        assertTrue(report.body.contains("\"The transaction fell through.\""))
        assertTrue(report.body.contains("statement-a.pdf, page 2, line 2"))
        assertTrue(report.body.contains("statement-b.pdf, page 4, line 2"))
        assertTrue(report.body.contains("Pat Taylor"))
        assertTrue(report.body.contains("human legal review required"))
        assertTrue(report.body.contains("does not determine guilt"))
    }

    @Test
    fun lawMappingsAreRestrictedToJurisdictionAndOfficialSource() {
        val southAfrican = JurisdictionService.legalReferencesFor(
            "ZA-KZN", ContradictionCategory.SECTION_12B, "Petroleum Products Act Section 12B"
        ).single()
        assertTrue(southAfrican.citation.contains("Petroleum Products Act 120 of 1977"))
        assertEquals("ZA-KZN", southAfrican.jurisdiction)
        assertTrue(southAfrican.authoritativeUrl.startsWith("https://www.gov.za/"))

        assertTrue(JurisdictionService.legalReferencesFor(
            "UAE-RAKEZ", ContradictionCategory.SECTION_12B
        ).isEmpty())
        assertTrue(JurisdictionService.legalReferencesFor(
            "INTL", ContradictionCategory.PERJURY
        ).isEmpty())
    }

    @Test
    fun additionalElectronicSignatureReferencesRequireExactJurisdictionAndOfficialSources() {
        data class ExpectedReference(
            val jurisdiction: String,
            val sourceText: String,
            val citation: String,
            val officialUrl: String
        )

        val cases = listOf(
            ExpectedReference(
                "UK",
                "The parties used an electronic signature.",
                "Electronic Communications Act 2000, section 7",
                "https://www.legislation.gov.uk/ukpga/2000/7/section/7"
            ),
            ExpectedReference(
                "US",
                "E-SIGN Act: electronic signature in interstate commerce.",
                "Electronic Signatures in Global and National Commerce Act, 15 U.S.C. § 7001(a)",
                "https://uscode.house.gov/us/usc/t15/s7001"
            ),
            ExpectedReference(
                "IN",
                "An electronic signature under the Information Technology Act.",
                "Information Technology Act, 2000, section 5",
                "https://www.indiacode.nic.in/show-data?actid=AC_CEN_45_76_00001_200021_1517807324077&orderno=6&sectionId=13015&sectionno=5"
            ),
            ExpectedReference(
                "AU",
                "Commonwealth law requires an electronic signature.",
                "Electronic Transactions Act 1999 (Cth), section 10",
                "https://www.legislation.gov.au/C2004A00553/latest/text"
            )
        )

        cases.forEach { expected ->
            val reference = JurisdictionService.legalReferencesFor(
                expected.jurisdiction,
                ContradictionCategory.SIGNATURE_STATUS,
                expected.sourceText
            ).single()
            assertEquals(expected.jurisdiction, reference.jurisdiction)
            assertEquals(expected.citation, reference.citation)
            assertEquals(expected.officialUrl, reference.authoritativeUrl)
            assertTrue(reference.requiresHumanReview)
            assertTrue(reference.reviewNote.isNotBlank())
        }
    }

    @Test
    fun unsupportedSignatureScopesAndCategoriesStillRequireHumanReview() {
        assertTrue(
            JurisdictionService.legalReferencesFor(
                "US", ContradictionCategory.SIGNATURE_STATUS, "electronic signature in a local-only matter"
            ).isEmpty()
        )
        assertTrue(
            JurisdictionService.legalReferencesFor(
                "AU", ContradictionCategory.SIGNATURE_STATUS, "state law and electronic signature"
            ).isEmpty()
        )
        assertTrue(
            JurisdictionService.legalReferencesFor(
                "CA", ContradictionCategory.SIGNATURE_STATUS, "electronic signature"
            ).isEmpty()
        )
        assertTrue(
            JurisdictionService.legalReferencesFor(
                "UK", ContradictionCategory.PERJURY, "perjury allegation"
            ).isEmpty()
        )

        val reference = JurisdictionService.legalReferencesFor(
            "UK", ContradictionCategory.SIGNATURE_STATUS, "electronic signature"
        ).single()
        val contradiction = com.verumomnis.forensic.model.Contradiction(
            contradictionId = "VO-UK-SIGNATURE",
            brainSource = "B1",
            category = ContradictionCategory.SIGNATURE_STATUS,
            claimA = com.verumomnis.forensic.model.ContradictionClaim(
                text = "A signature is electronic.",
                source = "fixture.pdf",
                evidenceId = "DOC001",
                sha512 = "a".repeat(128)
            ),
            claimB = com.verumomnis.forensic.model.ContradictionClaim(
                text = "The signature was handwritten.",
                source = "fixture.pdf",
                evidenceId = "DOC001",
                sha512 = "a".repeat(128)
            ),
            severity = com.verumomnis.forensic.model.Severity.MODERATE,
            legalSignificance = "Signature-status indicator",
            timestamp = now.toString(),
            jurisdiction = "UK",
            legalReferences = listOf(reference)
        )
        val record = FindingsJsonEmitter.contradictionToRecord(contradiction)
        assertTrue(record.legal_hypothesis.toString().contains("\"requires_human_review\":true"))

        val findings = ForensicFindings(
            documentsAnalyzed = 1,
            evidenceAtoms = emptyList(),
            contradictions = listOf(contradiction.copy(councilStatus = "ACCEPTED")),
            timeline = emptyList(),
            legalMappings = listOf(reference.citation),
            jurisdiction = "UK",
            jurisdictionSource = JurisdictionSource("UK", venueLabel = "United Kingdom"),
            brainVerdicts = mapOf("B1" to "CONTRADICTION")
        )
        val report = ReportGenerator.generate(findings, "ReviewNote", now)
        assertTrue(report.body.contains("Human review required: ${reference.reviewNote}"))
    }

    @Test
    fun overlappingGeographicBoxesDoNotSelectTheWrongLegalRegion() {
        assertEquals(
            "UK",
            JurisdictionService.resolve(GpsRecord(51.5074, -0.1278, timestamp = now.toString())).jurisdiction
        )
        assertEquals(
            "UAE-DIFC",
            JurisdictionService.resolve(GpsRecord(25.2048, 55.2708, timestamp = now.toString())).jurisdiction
        )
        assertEquals(
            "INTL",
            JurisdictionService.resolve(GpsRecord(41.8781, -87.6298, timestamp = now.toString())).jurisdiction
        )
    }

    @Test
    fun flattenedPdfWithoutPageSpansNeverGetsPageOne() {
        val docs = listOf(
            EvidenceDocument("A", "a.pdf", "pdf", "The transaction fell through.", "a".repeat(128)),
            EvidenceDocument("B", "b.pdf", "pdf", "The transaction proceeded with the deal.", "b".repeat(128))
        )
        val contradiction = ContradictionExtractor.extract(docs, now).first()
        assertEquals(0, contradiction.claimA.page)
        assertEquals(0, contradiction.claimB.page)
    }

    @Test
    fun pageTextSurvivesVaultProvenancePersistence() {
        val vault = EvidenceVault(temporaryFolder.newFolder("vault"))
        val hash = "f".repeat(128)
        val coverage = PdfExtractionCoverage(
            totalPages = 2,
            pages = listOf(PageExtraction(
                page = 2,
                status = PageReadStatus.OCR,
                reason = "on_device_ocr",
                extractedChars = 21,
                extractedText = "Morgan Lee signed."
            ))
        )

        vault.storePdfExtraction("DOC001", "source.pdf", hash, coverage)
        val persisted = vault.loadPdfExtraction(hash)?.coverage?.pages?.single()

        assertEquals(2, persisted?.page)
        assertEquals("Morgan Lee signed.", persisted?.extractedText)
    }

    @Test
    fun unknownPagesAreExplicitInFindingsJson() {
        val textDocs = listOf(
            EvidenceDocument("A", "a.pdf", "pdf", "The transaction fell through.", "a".repeat(128)),
            EvidenceDocument("B", "b.pdf", "pdf", "The transaction proceeded with the deal.", "b".repeat(128))
        )
        val findings = ForensicService.scan(textDocs, now).findings
        val json = FindingsJsonEmitter.emit(findings, "unknown-pages", now)
        val parsed = Json.decodeFromString(FindingsJsonEmitter.FindingsJsonDocument.serializer(), json)
        val record = parsed.contradictions.first { it.proposition_a_text.contains("fell through") }

        assertEquals(findings.jurisdiction, parsed.jurisdiction)
        assertEquals(0, record.source_page)
        assertEquals(0, record.claim_a_page)
        assertEquals(0, record.claim_b_page)
        assertTrue(json.contains("\"source_page\":0"))
        assertTrue(json.contains("\"claim_a_page\":0"))
    }
}
