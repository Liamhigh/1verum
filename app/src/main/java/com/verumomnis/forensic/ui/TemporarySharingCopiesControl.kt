package com.verumomnis.forensic.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.verumomnis.forensic.pdf.RetryShareCache
import com.verumomnis.forensic.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Local busy state deliberately does not block sealing, review, or other app actions. */
@Composable
internal fun TemporarySharingCopiesControl(
    refreshKey: Int = 0,
    cache: RetryShareCache? = null
) {
    val context = LocalContext.current
    val shareCache = remember(context, cache) { cache ?: RetryShareCache(context.cacheDir) }
    val scope = rememberCoroutineScope()
    var inventory by remember { mutableStateOf<RetryShareCache.Inventory?>(null) }
    var working by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RetryShareCache.CleanupResult?>(null) }
    var refresh by remember { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(shareCache, refreshKey, refresh) {
        inventory = withContext(Dispatchers.IO) { shareCache.inventory() }
    }
    VoCard(title = "TEMPORARY SHARING COPIES", icon = Icons.Filled.PrivacyTip) {
        val current = inventory
        Text(
            current?.let { "${it.count} temporary sharing copies · ${it.totalBytes} bytes total" }
                ?: "Checking temporary sharing copies…",
            color = VoTextPrimary, fontSize = 12.sp
        )
        Text(
            "Retry PDF and findings JSON copies normally expire after 24 hours. Clear them sooner after recipients have imported them. Saved evidence, reports and findings JSON are kept.",
            color = VoTextMuted, fontSize = 12.sp
        )
        if (current != null && current.failures > 0) {
            Text("Some sharing copies could not be inspected. The count and size may be incomplete.",
                color = VoGold, fontSize = 12.sp)
        }
        result?.let {
            Text(
                if (it.failures > 0) {
                    "Removed ${it.deleted} temporary copies; ${it.failures} cleanup operation(s) failed. Some copies may remain. Try again. Saved originals were not changed."
                } else {
                    "Removed ${it.deleted} temporary copies. Saved originals were not changed."
                },
                color = if (it.failures > 0) VoGold else VoTextPrimary, fontSize = 12.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        VerumSecondaryButton(
            if (working) "Clearing sharing copies…" else "Clear temporary sharing copies",
            enabled = !working && current != null && (current.count > 0 || current.failures > 0),
            onClick = { confirming = true },
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = { refresh++ }, enabled = !working) {
            Text("Refresh sharing copy count", color = VoAccentBlue)
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = VoNavy2,
            title = { Text("Clear temporary sharing copies?", color = VoHeading) },
            text = {
                Text(
                    "${inventory?.count ?: 0} copies · ${inventory?.totalBytes ?: 0} bytes at last check. " +
                        "This removes all validated temporary retry sharing copies, including any staged since that check. " +
                        "Shares that receiving apps have not yet imported may stop opening. " +
                        "Copies already imported by another app are not removed. " +
                        "Saved evidence, reports and findings JSON in your vault will not be deleted.",
                    color = VoTextPrimary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    working = true
                    scope.launch {
                        try {
                            val (cleared, remaining) = withContext(Dispatchers.IO) {
                                shareCache.clearAll() to shareCache.inventory()
                            }
                            result = cleared
                            inventory = remaining
                        } finally {
                            working = false
                        }
                    }
                }) { Text("Clear copies now", color = VoGold) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Cancel", color = VoAccentBlue) }
            }
        )
    }
}
