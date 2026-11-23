package com.verumomnis.forensic

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.verumomnis.forensic.pdf.RetryShareCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point. Initialises the PDFBox-Android resource loader once,
 * which is required before any PDDocument operation (font and ICC resources
 * ship inside the library's assets). See VO-DSS-1.2 seal module.
 */
class VerumApplication : Application() {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        backgroundScope.launch {
            val cleanup = RetryShareCache(cacheDir).cleanExpired()
            if (cleanup.failures > 0) {
                android.util.Log.w("RetryShareCache", "Some expired sharing copies could not be cleared.")
            }
        }
    }
}
