package com.verumomnis.forensic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.engine.JurisdictionService
import com.verumomnis.forensic.model.JurisdictionSource
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

@Composable
internal fun CaseVenueSelector(
    selectedVenueCode: String?,
    gpsSuggestion: JurisdictionSource,
    onSelect: (String?) -> Unit
) {
    val venues = remember { JurisdictionService.supportedVenues() }
    val selectedVenue = venues.firstOrNull { it.code == selectedVenueCode }
    val suggestionLabel = gpsSuggestion.venueLabel.ifBlank { gpsSuggestion.jurisdiction }
    val buttonLabel = selectedVenue?.let { "${it.label} · ${it.code}" }
        ?: "Use GPS suggestion · $suggestionLabel (${gpsSuggestion.jurisdiction})"
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("GOVERNING VENUE", color = VoAccentBlue, fontSize = 11.sp)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonLabel, color = VoTextPrimary)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 360.dp)
            ) {
                DropdownMenuItem(
                    text = { Text("Use GPS suggestion · $suggestionLabel (${gpsSuggestion.jurisdiction})") },
                    onClick = {
                        expanded = false
                        onSelect(null)
                    }
                )
                venues.forEach { venue ->
                    DropdownMenuItem(
                        text = { Text("${venue.label} · ${venue.code}") },
                        onClick = {
                            expanded = false
                            onSelect(venue.code)
                        }
                    )
                }
            }
        }
        Text(
            if (selectedVenue == null) {
                "GPS is only a suggestion. Confirm the legal venue for this case; location remains supporting context."
            } else {
                "Investigator-selected venue. GPS remains supporting context and will not replace this choice."
            },
            color = VoTextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
