package com.islandskiesastro.astroplanner

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = this.usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val bytes = this.bytes ?: return ByteArray(0)
    return ByteArray(this.length.toInt()).also { arr ->
        arr.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, this.length)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual class ImageStorage {
    private val imagesDir: String

    init {
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val docs = paths.first() as String
        imagesDir = "$docs/images"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = imagesDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null
        )
    }

    actual fun getDir(): String = imagesDir

    @OptIn(ExperimentalForeignApi::class)
    actual fun write(filename: String, bytes: ByteArray) {
        val path = "$imagesDir/$filename"
        bytes.toNSData().writeToFile(path, atomically = true)
    }

    actual fun read(filename: String): ByteArray? {
        val path = "$imagesDir/$filename"
        return NSData.dataWithContentsOfFile(path)?.toByteArray()
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun cropImageBytes(src: ByteArray, x: Int, y: Int, size: Int): ByteArray {
    val image = UIImage.imageWithData(src.toNSData()) ?: return src
    val cgImage = image.CGImage ?: return src
    val rect = CGRectMake(x.toDouble(), y.toDouble(), size.toDouble(), size.toDouble())
    val croppedCg = CGImageCreateWithImageInRect(cgImage, rect) ?: return src
    val croppedImage = UIImage(croppedCg)
    val result = UIImageJPEGRepresentation(croppedImage, 0.9)?.toByteArray() ?: src
    CGImageRelease(croppedCg)
    return result
}
