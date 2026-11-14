package com.example.disasterlink.data

import android.content.ContentResolver
import android.graphics.*
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ImageUtils {
    private const val MAX_IMAGE_BYTES = 180 * 1024

    fun compressAndWatermark(
        resolver: ContentResolver,
        uri: Uri,
        label: String? = null
    ): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { input -> BitmapFactory.decodeStream(input, null, bounds) }
        
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("Could not read selected image")
        }

        val sample = generateSequence(1) { it * 2 }.takeWhile { it < 16 }.lastOrNull {
            bounds.outWidth / it <= 1600 && bounds.outHeight / it <= 1600
        } ?: 16

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val original = resolver.openInputStream(uri).use { input -> BitmapFactory.decodeStream(input, null, options) }
            ?: throw IllegalArgumentException("Could not decode selected image")

        try {
            // Enterprise: Add Watermark
            val mutable = original.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutable)
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = (mutable.height / 30f).coerceAtLeast(24f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(2f, 1f, 1f, Color.BLACK)
            }

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val text = (label?.let { "$it • " } ?: "") + timestamp
            
            val padding = 20f
            canvas.drawText(text, padding, mutable.height - padding, paint)

            var quality = 82
            var result: ByteArray
            do {
                val out = ByteArrayOutputStream()
                mutable.compress(Bitmap.CompressFormat.JPEG, quality, out)
                result = out.toByteArray()
                quality -= 8
            } while (result.size > MAX_IMAGE_BYTES && quality >= 30)

            if (result.size > MAX_IMAGE_BYTES) {
                throw IllegalArgumentException("Image is too large after compression")
            }
            return result
        } finally {
            original.recycle()
        }
    }
}
