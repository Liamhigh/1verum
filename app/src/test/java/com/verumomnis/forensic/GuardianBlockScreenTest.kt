package com.verumomnis.forensic

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.verumomnis.forensic.engine.EvidenceDocument
import com.verumomnis.forensic.engine.ForensicService
import com.verumomnis.forensic.engine.ReportGenerator
import com.verumomnis.forensic.model.GuardianAssessment
import com.verumomnis.forensic.model.GuardianViolation
import com.verumomnis.forensic.model.GuardianViolationType
import com.verumomnis.forensic.model.Severity
import com.verumomnis.forensic.ui.GuardianBlockScreen
import com.verumomnis.forensic.ui.theme.VerumOmnisTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h1100dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuardianBlockScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hardStopAllowsReadOnlyReportReviewAndShowsShareFailure() {
        val now = Instant.parse("2026-11-21T16:00:00Z")
        val findings = ForensicService.scan(
            listOf(
                EvidenceDocument(
                    evidenceId = "DOC001",
                    fileName = "quoted-terms.txt",
                    type = "document",
                    text = "This filing quotes the phrase “lethal targeting” for analysis.",
                    sha512 = "a".repeat(128)
                )
            ),
            now
        ).findings
        val report = ReportGenerator.generate(findings, "ScreenReview", now)
        val pdf = File(Files.createTempDirectory("guardian-report-test").toFile(), "report.pdf")
            .apply { writeText("test share target") }
        val assessment = GuardianAssessment(
            violations = listOf(
                GuardianViolation(
                    type = GuardianViolationType.ARTICLE_X_WEAPONIZATION,
                    severity = Severity.CRITICAL,
                    description = "Article X screening indicator; review the source in context.",
                    evidenceId = "DOC001",
                    trigger = "lethal targeting",
                    timestamp = now.toString()
                )
            ),
            hardStopRequired = true
        )

        composeRule.setContent {
            VerumOmnisTheme {
                GuardianBlockScreen(
                    assessment = assessment,
                    report = report,
                    reportPdfFile = pdf,
                    onClearCase = {},
                    onReadConstitution = {},
                    onSharePdf = { "The PDF is saved, but sharing could not be opened." }
                )
            }
        }

        composeRule.onNodeWithText("Review sealed report").assertIsDisplayed().performClick()
        composeRule.onNodeWithText(report.title).fetchSemanticsNode()
        composeRule.onNodeWithText("Share sealed PDF · report.pdf").performClick()
        composeRule.onNodeWithText(
            "The PDF is saved, but sharing could not be opened."
        ).fetchSemanticsNode()
    }
}
