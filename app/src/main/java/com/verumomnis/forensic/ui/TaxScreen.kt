package com.verumomnis.forensic.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.engine.PdfBoxTextExtractor
import com.verumomnis.forensic.engine.TaxModule
import com.verumomnis.forensic.engine.tax.JurisdictionFee
import com.verumomnis.forensic.engine.tax.JurisdictionFees
import com.verumomnis.forensic.ui.theme.VoBackground
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoGreen
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary
import com.verumomnis.forensic.ui.theme.VoTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Currency
import java.util.Locale
import kotlin.math.max

/**
 * Tax service screen.
 *
 * Two parts:
 *  1. Company tax return review — quote-before-analysis. The user picks a
 *     jurisdiction (device locale by default, manual override via chips), uploads
 *     the company tax return (PDF), sees the estimated Verum fee (= 50% of the
 *     typical accountant fee in that jurisdiction, basis line shown), and only
 *     after tapping Accept is the document handed to the forensic scan flow with
 *     documentClass=tax context.
 *  2. The original ZA tax calculator (kept from the previous revision) with
 *     indicators-not-determinations wording.
 *
 * Wording discipline (D4): this is a document-analysis service fee; findings are
 * indicators, not determinations; no contingency / recovery-percentage language.
 */
@Composable
fun TaxScreen(viewModel: VerumViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---- Jurisdiction (device locale default, manual picker override) ----
    var fee by remember {
        mutableStateOf(JurisdictionFees.resolve(Locale.getDefault().country))
    }

    // ---- Picked tax return + quote acceptance state ----
    var pickedName by remember { mutableStateOf<String?>(null) }
    var pickedText by remember { mutableStateOf<String?>(null) }
    var extracting by remember { mutableStateOf(false) }
    var extractError by remember { mutableStateOf<String?>(null) }
    var accepted by remember { mutableStateOf(false) }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        accepted = false
        extractError = null
        pickedText = null
        extracting = true
        scope.launch {
            val (name, text) = withContext(Dispatchers.IO) {
                val n = viewModel.uriFileName(uri, context)
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                val t = bytes?.let { PdfBoxTextExtractor().extractText(it) } ?: ""
                n to t
            }
            pickedName = name
            pickedText = text
            extracting = false
            if (text.isBlank()) {
                extractError = "No readable text found in that PDF. Try a text-based (non-scanned) company tax return."
            }
        }
    }

    val (verumLow, verumHigh) = JurisdictionFees.verumFeeRange(fee)

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ================= QUOTE-BEFORE-ANALYSIS (D3) =================
        VoCard(title = "COMPANY TAX RETURN REVIEW · FEE = 50% OF LOCAL ACCOUNTANT", icon = Icons.Filled.UploadFile) {
            Text(
                "Verum Omnis reviews your company tax return for anomalies. This is a " +
                    "document-analysis service: findings are indicators, not determinations. " +
                    "You see the estimated fee before any analysis begins.",
                color = VoTextSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))

            // Manual country picker (default comes from the device locale).
            Text("Jurisdiction", color = VoTextMuted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                JurisdictionFees.pickerOptions().take(3).forEach { j ->
                    JurisdictionChip(j, fee, j.countryCode) { fee = it; accepted = false }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                JurisdictionFees.pickerOptions().drop(3).forEach { j ->
                    JurisdictionChip(j, fee, j.countryCode) { fee = it; accepted = false }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Step 1: pick the company tax return PDF.
            OutlinedButton(
                onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    if (pickedName == null) "Upload company tax return (PDF)"
                    else "Selected: $pickedName",
                    color = VoGold, fontSize = 13.sp
                )
            }
            if (extracting) {
                Spacer(Modifier.height(8.dp))
                Text("Reading document…", color = VoTextMuted, fontSize = 11.sp)
            }
            extractError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = VoTextMuted, fontSize = 11.sp)
            }

            // Step 2: estimated quote with basis line (D4 wording).
            if (pickedName != null && !pickedText.isNullOrBlank() && !accepted) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Typical accountant fee in ${fee.countryName}: " +
                        "${formatMoney(fee.typicalAccountantFeeLow, fee.currency)} – " +
                        "${formatMoney(fee.typicalAccountantFeeHigh, fee.currency)} · " +
                        "Verum fee (50%): ${formatMoney(verumLow, fee.currency)} – " +
                        formatMoney(verumHigh, fee.currency),
                    color = VoTextPrimary, fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(fee.note, color = VoTextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Estimated quote — confirmed before work begins. Fees are for the " +
                        "document-analysis service only; they never depend on the outcome " +
                        "of the review.",
                    color = VoTextMuted, fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val text = pickedText ?: return@Button
                        val name = pickedName ?: "tax-return.pdf"
                        viewModel.ingestDocument(name, "application/pdf", text)
                        viewModel.postEngine(
                            "Tax return review accepted (documentClass=tax): $name. " +
                                "Fee basis — typical accountant fee in ${fee.countryName}: " +
                                "${formatMoney(fee.typicalAccountantFeeLow, fee.currency)} – " +
                                "${formatMoney(fee.typicalAccountantFeeHigh, fee.currency)}; " +
                                "Verum fee (50%): ${formatMoney(verumLow, fee.currency)} – " +
                                "${formatMoney(verumHigh, fee.currency)}. " +
                                "Estimated quote, confirmed before work begins. " +
                                "Findings are indicators, not determinations. " +
                                "The document is now in the case file — seal the case to run the forensic analysis."
                        )
                        accepted = true
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VoGold, contentColor = VoBackground)
                ) { Text("Accept quote & add to case") }
            }

            // Step 3: confirmation after Accept.
            if (accepted) {
                Spacer(Modifier.height(12.dp))
                Row {
                    androidx.compose.material3.Icon(
                        Icons.Filled.CheckCircle, contentDescription = null,
                        tint = VoGreen, modifier = Modifier.height(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Added to the case file with your accepted quote. " +
                            "Seal the case (Scan screen) to run the analysis.",
                        color = VoTextPrimary, fontSize = 12.sp
                    )
                }
            }
        }

        // ================= ZA TAX CALCULATOR (kept from previous revision) =================
        ZaCalculatorCard(viewModel)
    }
}

@Composable
private fun JurisdictionChip(
    option: JurisdictionFee,
    current: JurisdictionFee,
    label: String,
    onSelect: (JurisdictionFee) -> Unit
) {
    FilterChip(
        selected = current.countryCode == option.countryCode,
        onClick = { onSelect(option) },
        label = { Text(label, fontSize = 11.sp) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VoGold)
    )
}

@Composable
private fun ZaCalculatorCard(viewModel: VerumViewModel) {
    var individual by remember { mutableStateOf(true) }
    var income by remember { mutableStateOf("450000") }
    var age by remember { mutableStateOf("40") }
    var revenue by remember { mutableStateOf("1000000") }
    var expenses by remember { mutableStateOf("400000") }

    VoCard(title = "SOUTH AFRICA · TAX ESTIMATE CALCULATOR", icon = Icons.Filled.Calculate) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = individual, onClick = { individual = true }, label = { Text("Individual") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VoGold))
            FilterChip(selected = !individual, onClick = { individual = false }, label = { Text("Company") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VoGold))
        }
        Spacer(Modifier.height(10.dp))
        if (individual) {
            OutlinedTextField(income, { income = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Taxable income (ZAR)") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(age, { age = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Age") }, singleLine = true)
        } else {
            OutlinedTextField(revenue, { revenue = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Revenue (ZAR)") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(expenses, { expenses = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Expenses (ZAR)") }, singleLine = true)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val result = if (individual) {
                    TaxModule.calculateIndividualTaxZA(income.toDoubleOrNull() ?: 0.0, age.toIntOrNull() ?: 0)
                } else {
                    TaxModule.calculateCompanyTaxZA(revenue.toDoubleOrNull() ?: 0.0, expenses.toDoubleOrNull() ?: 0.0)
                }
                val liability = max(0.0, result.taxLiability)
                viewModel.postEngine(
                    (if (individual) "Individual" else "Company") +
                        " tax estimate (ZA): taxable R%,.0f, estimated liability R%,.2f at an effective rate of %.1f%%. "
                            .format(result.taxableIncome, liability, result.rate * 100) +
                        "Heuristic estimate only — an indicator, not a determination."
                )
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VoGold, contentColor = VoBackground)
        ) { Text("Calculate estimate") }
        Spacer(Modifier.height(8.dp))
        Text(
            "Heuristic estimate posted to the chat. An indicator, not a determination — " +
                "confirm all figures with a registered tax practitioner.",
            color = VoTextMuted, fontSize = 11.sp
        )
    }
}

/** Format an amount with the ISO currency's symbol, e.g. R5,000 / $1,000 / £750. */
private fun formatMoney(amount: Double, currencyCode: String): String {
    val symbol = try {
        Currency.getInstance(currencyCode).getSymbol(Locale.getDefault())
    } catch (e: Exception) {
        "$currencyCode "
    }
    return symbol + "%,.0f".format(Locale.US, amount)
}
