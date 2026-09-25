package io.github.menadion.magus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

// Profile pictures: a small square JPEG that lives in the person's family record next to their
// location, so no file storage is needed. 128 px is plenty for a 38 to 56 dp circle.
object Photos {
    const val SIZE = 128
    private const val QUALITY = 82

    // Reads the picked photo, takes its middle square, shrinks it, and returns JPEG bytes.
    fun shrink(context: Context, uri: Uri): ByteArray {
        val source = if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder also turns the photo the right way up.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val shortest = min(info.size.width, info.size.height)
                decoder.setTargetSampleSize(max(1, shortest / (SIZE * 2)))
                decoder.isMutableRequired = true
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            val shortest = min(bounds.outWidth, bounds.outHeight)
            val options = BitmapFactory.Options().apply { inSampleSize = max(1, shortest / (SIZE * 2)) }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("Couldn't read that picture.")
        }
        val side = min(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray?): Bitmap? =
        if (bytes == null) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    // A short key that changes when the picture changes, for caches.
    fun key(bytes: ByteArray?): String = if (bytes == null) "-" else bytes.size.toString() + ":" + bytes.contentHashCode()
}
