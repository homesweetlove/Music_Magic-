package com.musicmagic.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import java.io.ByteArrayOutputStream

/** Bounded, original-source artwork renderer. No ImageIO, no network, no EXIF rewrite. */
object ArtworkEditing {
    const val MAX_BYTES = 12 * 1024 * 1024
    fun isPng(bytes: ByteArray) =
        bytes.size >= 8 && bytes.sliceArray(0 until 8).contentEquals(
            byteArrayOf(-119,80,78,71,13,10,26,10))
    fun mime(bytes: ByteArray): String = if (isPng(bytes)) "image/png" else "image/jpeg"

    fun load(context: Context, uri: Uri): Bitmap {
        // Metadata bounds first; never decode camera-resolution originals at full size.
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        require(opts.outWidth > 0 && opts.outHeight > 0) { "표지를 읽을 수 없습니다." }
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > 1400) sample *= 2
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } ?: error("표지 이미지 파일을 열 수 없습니다.")
    }

    /** Output is square, center-cropped, and no larger than 1000px. */
    fun render(context: Context, uri: Uri, zoom: Float, quarterTurns: Int, png: Boolean): ByteArray {
        val source = load(context, uri)
        try {
            val targetSize = minOf(1000, minOf(source.width, source.height).coerceAtLeast(128))
            val result = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(result)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                val matrix = Matrix()
                val rotated = (quarterTurns.mod(4) % 2 != 0)
                val w = if (rotated) source.height.toFloat() else source.width.toFloat()
                val h = if (rotated) source.width.toFloat() else source.height.toFloat()
                val scale = maxOf(targetSize / w, targetSize / h) * zoom.coerceIn(1f, 3f)
                matrix.postTranslate(-source.width / 2f, -source.height / 2f)
                matrix.postRotate((quarterTurns.mod(4) * 90).toFloat())
                matrix.postScale(scale, scale)
                matrix.postTranslate(targetSize / 2f, targetSize / 2f)
                canvas.drawBitmap(source, matrix, paint)
                val bytes = ByteArrayOutputStream().use { output ->
                    check(result.compress(
                        if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                        if (png) 100 else 92, output,
                    )) { "앨범아트를 처리하지 못했습니다." }
                    output.toByteArray()
                }
                require(bytes.size <= MAX_BYTES) { "편집된 표지가 12MB를 초과합니다." }
                return bytes
            } finally { result.recycle() }
        } finally { source.recycle() }
    }
}
