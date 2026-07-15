package com.verumomnis.forensic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoTextMuted

@Composable
fun TaxScreen(viewModel: VerumViewModel) {
    var individual by remember { mutableStateOf(true) }
    var jurisdiction by remember { mutableStateOf("ZA-KZN") }
    var income by remember { mutableStateOf("450000") }
    var age by remember { mutableStateOf("40") }
    var revenue by remember { mutableStateOf("1000000") }
    var expenses by remember { mutableStateOf("400000") }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        VoCard(title = "TAX RETURN · VERUM FEE = 50% OF LOCAL ACCOUNTANT", icon = Icons.Filled.Calculate) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = individual, onClick = { individual = true }, label = { Text("Individual") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VoGold))
                FilterChip(selected = !individual, onClick = { individual = false }, label = { Text("Company") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VoGold))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ZA-KZN", "ZA-GP", "ZA-WC", "UAE-RAKEZ").forEach { j ->
                    FilterChip(selected = jurisdiction == j, onClick = { jurisdiction = j }, label = { Text(j, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = VoGold))
                }
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
                    viewModel.runTaxReturn(
                        entityType = if (individual) "individual" else "company",
                        jurisdiction = jurisdiction,
                        revenue = revenue.toDoubleOrNull() ?: 0.0,
                        expenses = expenses.toDoubleOrNull() ?: 0.0,
                        income = income.toDoubleOrNull() ?: 0.0,
                        age = age.toIntOrNull() ?: 0
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VoGold, contentColor = Color.Black)
            ) { Text("Calculate & Seal") }
            Spacer(Modifier.height(8.dp))
            Text("Result is posted to the chat and sealed. Same 50% deal for companies and private citizens.", color = VoTextMuted, fontSize = 11.sp)
        }
    }
}
