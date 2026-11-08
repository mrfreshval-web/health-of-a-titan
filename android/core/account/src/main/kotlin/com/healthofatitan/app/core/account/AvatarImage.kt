package com.healthofatitan.app.core.account

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * The avatar picture (A65 §5.1): the centre square of the photo, 512×512, WebP. [ImageDecoder]
 * applies the EXIF orientation while decoding; re-encoding writes no EXIF at all, so GPS, the
 * camera model and the time of the shot never reach the stored file. Nothing here recognises a
 * face (no biometrics, 152-ФЗ ст. 11).
 */
object AvatarImage {
    const val SIDE = 512
    private const val QUALITY = 85

    /** Decodes [source] (gallery, camera or the Google photo) and returns the WebP, or null. */
    fun square(source: ImageDecoder.Source): ByteArray? = runCatching {
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val shortSide = min(info.size.width, info.size.height)
            // decode no bigger than needed: the short side ≥ 512 after sampling
            var sample = 1
            while (shortSide / (sample * 2) >= SIDE) sample *= 2
            decoder.setTargetSampleSize(sample)
        }
        encode(crop(bitmap))
    }.getOrNull()

    /** The centre square, scaled to [SIDE]. */
    fun crop(bitmap: Bitmap): Bitmap {
        val side = min(bitmap.width, bitmap.height)
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        val square = Bitmap.createBitmap(bitmap, x, y, side, side)
        val target = min(SIDE, max(side, 1))
        return if (side == target) square else Bitmap.createScaledBitmap(square, target, target, true)
    }

    fun encode(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, out)
        out.toByteArray()
    }
}
