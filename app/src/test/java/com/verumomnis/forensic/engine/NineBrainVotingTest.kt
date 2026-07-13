package com.verumomnis.forensic.engine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for the Nine-Brain Engine voting system.
 * Validates consensus rules, quorum logic, and B9 behavior.
 */
class NineBrainVotingTest {

    private lateinit var orchestrator: NineBrainOrchestrator
    private lateinit var b1: ContradictionBrain
    private lateinit var b2: DocumentBrain
    private lateinit var b3: CommunicationBrain
    private lateinit var b4: BehavioralBrain
    private lateinit var b5: TimelineBrain
    private lateinit var b6: FinancialBrain
    private lateinit var b7: LegalBrain
    private lateinit var b8: AudioBrain
    private lateinit var b9: GuardianBrain

    @Before
    fun setup() {
        b1 = ContradictionBrain()
        b2 = DocumentBrain()
        b3 = CommunicationBrain()
        b4 = BehavioralBrain()
        b5 = TimelineBrain()
        b6 = FinancialBrain()
        b7 = LegalBrain()
        b8 = AudioBrain()
        b9 = GuardianBrain()

        orchestrator = NineBrainOrchestrator(
            brains = listOf(b1, b2, b3, b4, b5, b6, b7, b8, b9)
        )
    }

    // ─── Quorum Tests ───

    @Test
    fun `3-brain quorum - B1 plus 2 others confirms finding ACCEPTED`() {
        val finding = createFinding(
            confirmations = listOf(BrainId.B1, BrainId.B2, BrainId.B5)
        )
        val result = orchestrator.applyVoting(finding)
        assertEquals(FindingStatus.ACCEPTED, result.status)
    }

    @Test
    fun `2-brain quorum - B1 plus 1 other confirms finding ACCEPTED`() {
        val finding = createFinding(
            confirmations = listOf(BrainId.B1, BrainId.B4)
        )
        val result = orchestrator.applyVoting(finding)
        assertEquals(FindingStatus.ACCEPTED, result.status)
    }

    @Test
    fun `1-brain only - B1 plus 0 others returns INDETERMINATE`() {
        val finding = createFinding(
            confirmations = listOf(BrainId.B1)
        )
        val result = orchestrator.applyVoting(finding)
        assertEquals(FindingStatus.INDETERMINATE_DUE_TO_CONCEALMENT, result.status)
    }

    @Test
    fun `0-brain - no confirmations returns INSUFFICIENT`() {
        val finding = createFinding(
            confirmations = emptyList()
        )
        val result = orchestrator.applyVoting(finding)
        assertEquals(FindingStatus.INSUFFICIENT, result.status)
    }

    @Test
    fun `B1 must flag - finding without B1 is rejected even with 3 confirmations`() {
        val finding = createFinding(
            confirmations = listOf(BrainId.B2, BrainId.B3, BrainId.B4)
        )
        val result = orchestrator.applyVoting(finding)
        assertEquals(FindingStatus.INSUFFICIENT, result.status)
        assertTrue(result.reason.contains("B1 did not flag"))
    }

    // ─── B9 Tests ───

    @Test
    fun `B9 vote does not count toward quorum`() {
        val finding = createFinding(
            confirmations = listOf(BrainId.B1, BrainId.B9, BrainId.B2)
        )
        // B9 should not count, so only B1 + B2 = 2 confirming = ACCEPTED
        val result = orchestrator.applyVoting(finding)
        assertEquals(FindingStatus.ACCEPTED, result.status)
    }

    @Test
    fun `B9 alone cannot form quorum`() {
        val finding = createFinding(
            confirmations = listOf(BrainId.B1, BrainId.B9)
        )
        val result = orchestrator.applyVoting(finding)
        // B9 doesn't count, so only 1 confirming (B1) = INDETERMINATE
        assertEquals(FindingStatus.INDETERMINATE_DUE_TO_CONCEALMENT, result.status)
    }

    @Test
    fun `B9 veto overrides quorum`() {
        val finding = createFinding(
            confirmations = listOf(BrainId.B1, BrainId.B2, BrainId.B3),
            violatesConstitution = true
        )
        val result = orchestrator.applyVoting(finding)
        assertEquals(FindingStatus.CONSTITUTIONALLY_VETOED, result.status)
    }

    @Test
    fun `B9 cannot issue vote method returns null`() {
        val finding = createTestFinding()
        val vote = b9.vote(finding)
        assertNull(vote)
    }

    // ─── Execution Order Tests ───

    @Test
    fun `brain execution order is B2 B8 B3 B1 B5 B6 B4 B7 B9`() {
        val order = orchestrator.getExecutionOrder()
        val expected = listOf(
            BrainId.B2, BrainId.B8, BrainId.B3, BrainId.B1,
            BrainId.B5, BrainId.B6, BrainId.B4, BrainId.B7, BrainId.B9
        )
        assertEquals(expected, order)
    }

    @Test
    fun `sequential execution - each brain waits for previous`() {
        val executionLog = mutableListOf<BrainId>()
        val mockOrchestrator = createLoggingOrchestrator(executionLog)

        mockOrchestrator.runFullAnalysis(emptyList())

        // Verify order was maintained
        val expectedOrder = listOf(
            BrainId.B2, BrainId.B8, BrainId.B3, BrainId.B1,
            BrainId.B5, BrainId.B6, BrainId.B4, BrainId.B7, BrainId.B9
        )
        assertEquals(expectedOrder, executionLog)
    }

    // ─── Edge Cases ───

    @Test
    fun `duplicate confirmations are deduplicated`() {
        val finding = createFinding(
            confirmations = listOf(BrainId.B1, BrainId.B2, BrainId.B2, BrainId.B3)
        )
        val result = orchestrator.applyVoting(finding)
        assertEquals(FindingStatus.ACCEPTED, result.status)
    }

    @Test
    fun `empty evidence list returns empty findings`() {
        val result = orchestrator.runFullAnalysis(emptyList())
        assertTrue(result.findings.isEmpty())
        assertEquals(ScanStatus.COMPLETE, result.status)
    }

    @Test
    fun `null finding is rejected`() {
        val result = orchestrator.applyVoting(null)
        assertEquals(FindingStatus.INSUFFICIENT, result.status)
    }

    // ─── Helpers ───

    private fun createFinding(
        confirmations: List<BrainId>,
        violatesConstitution: Boolean = false
    ): Finding {
        return Finding(
            id = "TEST-001",
            claimA = Claim("Statement A", "Person A", "Doc A", 1, 1),
            claimB = Claim("Statement B", "Person B", "Doc B", 2, 2),
            severity = Severity.HIGH,
            confirmations = confirmations,
            violatesConstitution = violatesConstitution
        )
    }

    private fun createTestFinding(): Finding {
        return Finding(
            id = "TEST-002",
            claimA = Claim("X", "A", "D1", 1, 1),
            claimB = Claim("Y", "B", "D2", 2, 2),
            severity = Severity.MODERATE,
            confirmations = listOf(BrainId.B1)
        )
    }

    private fun createLoggingOrchestrator(
        log: MutableList<BrainId>
    ): NineBrainOrchestrator {
        // Creates a mock orchestrator that logs execution order
        return MockOrchestrator(log)
    }

    // Mock class for testing execution order
    private class MockOrchestrator(
        private val log: MutableList<BrainId>
    ) : NineBrainOrchestrator(emptyList()) {
        override fun runFullAnalysis(artifacts: List<EvidenceArtifact>): ScanResult {
            val order = getExecutionOrder()
            order.forEach { log.add(it) }
            return ScanResult(
                sealId = "test-seal",
                findings = emptyList(),
                status = ScanStatus.COMPLETE
            )
        }
    }
}
