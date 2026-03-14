package com.islandskiesastro.astroplanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

actual class ImageStorage(context: Context) {
    private val imagesDir = File(context.filesDir, "images").also { it.mkdirs() }

    actual fun getDir(): String = imagesDir.absolutePath

    actual fun write(filename: String, bytes: ByteArray) {
        File(imagesDir, filename).writeBytes(bytes)
    }

    actual fun read(filename: String): ByteArray? {
        val file = File(imagesDir, filename)
        return if (file.exists()) file.readBytes() else null
    }
}

actual fun cropImageBytes(src: ByteArray, x: Int, y: Int, size: Int): ByteArray {
    val bitmap = BitmapFactory.decodeByteArray(src, 0, src.size) ?: return src
    val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)
    val out = ByteArrayOutputStream()
    cropped.compress(Bitmap.CompressFormat.JPEG, 90, out)
    return out.toByteArray()
}
