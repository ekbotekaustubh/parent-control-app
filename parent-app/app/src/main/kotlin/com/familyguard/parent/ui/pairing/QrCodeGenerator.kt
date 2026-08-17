package com.familyguard.parent.ui.pairing

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/**
 * Encode-only QR generation (zxing:core) — this app never scans a code, only displays one
 * for the child device's camera to read (or the raw text as a manual-entry fallback), per
 * docs/pairing-security.md.
 */
object QrCodeGenerator {

    /** `familyguard://pair?code=XXXXXXXX` per docs/pairing-security.md's URI scheme. */
    fun pairingUri(code: String): String = "familyguard://pair?code=$code"

    fun generate(content: String, sizePx: Int = 512): ImageBitmap {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) BLACK else WHITE)
            }
        }
        return bitmap.asImageBitmap()
    }

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}
