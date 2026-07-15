package com.verumomnis.forensic.pdf

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Generates a deterministic QR bitmap from a UTF-8 payload.
 *
 * Used for the per-report verification QR (caseId + finalSealHash +
 * constitutionHash + engineVersion) and for any in-app seal verification flow.
 */
object QrCodeGenerator {

    fun generate(payload: String, sizePx: Int = 256): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val bitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
