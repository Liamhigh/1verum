package com.verumomnis.forensic

import android.app.Application
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader

/**
 * Application entry point. Initialises the PDFBox-Android resource loader once,
 * which is required before any PDDocument operation (font and ICC resources
 * ship inside the library's assets). See VO-DSS-1.2 seal module.
 */
class VerumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
