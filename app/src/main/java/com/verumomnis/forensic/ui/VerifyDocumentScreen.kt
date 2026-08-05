package com.verumomnis.forensic.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.verumomnis.forensic.R
import com.verumomnis.forensic.seal.SealMetadataCodec
import com.verumomnis.forensic.ui.theme.Cormorant
import com.verumomnis.forensic.ui.theme.JetBrainsMono
import com.verumomnis.forensic.ui.theme.SourceSans
import com.verumomnis.forensic.ui.theme.VoAccentBlue
import com.verumomnis.forensic.ui.theme.VoBorder
import com.verumomnis.forensic.ui.theme.VoGold
import com.verumomnis.forensic.ui.theme.VoPanel
import com.verumomnis.forensic.ui.theme.VoTextMuted
import com.verumomnis.forensic.ui.theme.VoTextPrimary

/**
 * Verification hub hand-off screen.
 *
 * Constitution §7: verification happens ONLY at the Verification Hub —
 * verumglobal.foundation/verify.html. This screen never renders a verdict;
 * it explains the hub and opens it in the browser (ACTION_VIEW). The app
 * claims no verification authority of its own.
 */
@Composable
fun VerifyDocumentScreen(
    state: UiState,
    viewModel: VerumViewModel,
    onBack: () -> Unit,
    onNavigateSeal: () -> Unit,
    onNavigateDocuments: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var launchFailed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            NavLinkVerify("Seal Document", selected = false, onClick = onNavigateSeal)
            Spacer(Modifier.width(24.dp))
            NavLinkVerify("Verify", selected = true, onClick = {})
        }
        Spacer(Modifier.height(8.dp))
        VerifyHeader()
        Spacer(Modifier.height(24.dp))

        // The one action: open the Verification Hub.
        VerumPrimaryButton(
            label = "Open the Verification Hub",
            onClick = { openVerificationHub(context) { launchFailed = true } },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text(
            SealMetadataCodec.VERIFY_BASE_URL.removePrefix("https://"),
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = VoAccentBlue,
            modifier = Modifier.clickable { openVerificationHub(context) { launchFailed = true } }
        )

        if (launchFailed) {
            Spacer(Modifier.height(10.dp))
            Text(
                "No browser could be opened on this device. Enter the address above " +
                    "on any device to verify a sealed document.",
                color = VoGold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(24.dp))
        VoHonestyNote(
            title = "Verification happens only at the Verification Hub.",
            text = "This app applies seals; it does not judge them. The canonical verifier at " +
                "verumglobal.foundation/verify.html recomputes the SHA-512 fingerprint in your " +
                "browser and checks the Bitcoin blockchain timestamp. No document content is " +
                "uploaded — the check runs client-side on the website."
        )
        Spacer(Modifier.height(20.dp))

        HowItWorksCard()
        Spacer(Modifier.height(24.dp))
        VoSealFooter()
    }
}

@Composable
private fun NavLinkVerify(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label.uppercase(),
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = if (selected) VoGold else VoAccentBlue,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun VerifyHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        // Brand mark untinted — the logo reads gold/full-colour everywhere.
        Image(
            painter = painterResource(R.drawable.vo_badge),
            contentDescription = "Verum Omnis",
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        VoKicker("The Website Is the Verification Hub", center = true)
        Spacer(Modifier.height(12.dp))
        VoSerifHeading("Verify a Sealed Document", fontSize = 32, center = true)
        Spacer(Modifier.height(8.dp))
        Text(
            "Every Verum Omnis seal is verified in one place: the Verification Hub on " +
                "verumglobal.foundation. Open it in your browser, then paste the SHA-512 " +
                "fingerprint or upload the sealed PDF there.",
            fontFamily = SourceSans,
            fontSize = 15.sp,
            color = VoTextMuted,
            lineHeight = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.95f)
        )
    }
}

@Composable
private fun HowItWorksCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(VoPanel, RoundedCornerShape(16.dp))
            .border(1.dp, VoBorder, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Text(
            "What the hub checks",
            fontFamily = Cormorant,
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp,
            color = VoGold
        )
        Spacer(Modifier.height(12.dp))
        HubStep("01", "Fingerprint", "The hub recomputes the document's SHA-512 hash in your browser and compares it with the seal footer.")
        HubStep("02", "Blockchain timestamp", "The OpenTimestamps proof is checked against the Bitcoin blockchain (confirmation typically takes 1–2 hours after sealing).")
        HubStep("03", "Chain of custody", "Prior seal IDs encoded in the QR metadata are listed so re-sealed documents show their full history.", last = true)
    }
}

@Composable
private fun HubStep(no: String, title: String, body: String, last: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = if (last) 0.dp else 14.dp)) {
        Text(no, fontFamily = JetBrainsMono, fontSize = 12.sp, color = VoAccentBlue, letterSpacing = 1.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = VoTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(body, color = VoTextMuted, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

/**
 * Constitution §7 — ALL verification opens the canonical hub URL.
 *
 * A silent failure would leave the user believing verification was under way
 * when no browser opened at all, so [onFailure] reports it and the screen shows
 * the address to open manually.
 */
private fun openVerificationHub(context: Context, onFailure: () -> Unit = {}) {
    val result = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SealMetadataCodec.VERIFY_BASE_URL)))
    }
    result.exceptionOrNull()?.let { error ->
        Log.w("VerumVerify", "Could not open the Verification Hub in a browser", error)
        onFailure()
    }
}
