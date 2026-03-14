package com.islandskiesastro.astroplanner

expect class ImageStorage {
    fun getDir(): String
    fun write(filename: String, bytes: ByteArray)
    fun read(filename: String): ByteArray?
}

expect fun cropImageBytes(src: ByteArray, x: Int, y: Int, size: Int): ByteArray
