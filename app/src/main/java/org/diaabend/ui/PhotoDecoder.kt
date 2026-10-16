package org.diaabend.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import kotlin.math.min

/** File extensions this device can decode. HEIC needs Android 9 (Fire OS 7). */
val PHOTO_EXTENSIONS: Set<String> =
    setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") +
        if (Build.VERSION.SDK_INT >= 28) setOf("heic", "heif") else emptySet()

object PhotoDecoder {
    /**
     * Decodes a photo upright and scaled down to fit into [maxWidth] x [maxHeight], so that even a
     * 50 megapixel photo costs no more memory than one screen. Returns null if it can't be decoded.
     */
    fun decode(bytes: ByteArray, maxWidth: Int, maxHeight: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val exif = runCatching { ExifInterface(ByteArrayInputStream(bytes)) }.getOrNull()
            val rotation = exif?.rotationDegrees ?: 0
            val sideways = rotation == 90 || rotation == 270
            val targetWidth = if (sideways) maxHeight else maxWidth
            val targetHeight = if (sideways) maxWidth else maxHeight

            // Coarse downsampling while decoding (powers of two), exact scaling afterwards
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= targetWidth || bounds.outHeight / (sample * 2) >= targetHeight) {
                sample *= 2
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            decoded?.let { bitmap ->
                val scale = min(1f, min(targetWidth.toFloat() / bitmap.width, targetHeight.toFloat() / bitmap.height))
                val matrix = Matrix().apply {
                    postScale(scale, scale)
                    if (exif?.isFlipped == true) postScale(-1f, 1f)
                    postRotate(rotation.toFloat())
                }
                if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        }
    } catch (e: OutOfMemoryError) {
        null
    }
}
