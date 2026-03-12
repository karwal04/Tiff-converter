package com.tiffconverter.app

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object TiffEncoder {

    fun encode(bitmaps: List<Bitmap>, quality: Int, onProgress: (Int) -> Unit = {}): ByteArray {
        val output = ByteArrayOutputStream()
        val useCompression = quality < 95
        val pages = mutableListOf<ByteArray>()
        val widths = mutableListOf<Int>()
        val heights = mutableListOf<Int>()

        bitmaps.forEachIndexed { i, bmp ->
            onProgress((i.toFloat() / bitmaps.size * 80).toInt())
            pages.add(encodeImage(bmp, useCompression, quality))
            widths.add(bmp.width)
            heights.add(bmp.height)
        }

        val numTags = 11
        val ifdSize = 2 + 12 * numTags + 4
        val extraPerIfd = 16
        val headerSize = 8

        val imgOffsets = mutableListOf<Int>()
        var offset = headerSize + bitmaps.size * (ifdSize + extraPerIfd)
        for (page in pages) {
            imgOffsets.add(offset)
            offset += page.size
        }

        val hdr = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        hdr.putShort(0x4949)
        hdr.putShort(42)
        hdr.putInt(headerSize)
        output.write(hdr.array())

        pages.forEachIndexed { idx, _ ->
            onProgress(80 + (idx.toFloat() / bitmaps.size * 15).toInt())
            val ifdOffset = headerSize + idx * (ifdSize + extraPerIfd)
            val extraOffset = ifdOffset + 2 + 12 * numTags + 4
            val nextIfd = if (idx < bitmaps.size - 1) headerSize + (idx + 1) * (ifdSize + extraPerIfd) else 0
            val buf = ByteBuffer.allocate(ifdSize + extraPerIfd).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(numTags.toShort())
            writeTag(buf, 256, 4, 1, widths[idx])
            writeTag(buf, 257, 4, 1, heights[idx])
            writeTag(buf, 258, 3, 1, 8)
            writeTag(buf, 259, 3, 1, if (useCompression) 32946 else 1)
            writeTag(buf, 262, 3, 1, 2)
            writeTag(buf, 273, 4, 1, imgOffsets[idx])
            writeTag(buf, 277, 3, 1, 3)
            writeTag(buf, 278, 4, 1, heights[idx])
            writeTag(buf, 279, 4, 1, pages[idx].size)
            writeTag(buf, 282, 5, 1, extraOffset)
            writeTag(buf, 283, 5, 1, extraOffset + 8)
            buf.putInt(nextIfd)
            buf.putInt(72)
            buf.putInt(1)
            buf.putInt(72)
            buf.putInt(1)
            output.write(buf.array())
        }

        for (page in pages) output.write(page)
        onProgress(100)
        return output.toByteArray()
    }

    private fun encodeImage(bmp: Bitmap, compress: Boolean, quality: Int): ByteArray {
        val rgb = ByteArray(bmp.width * bmp.height * 3)
        var off = 0
        val row = IntArray(bmp.width)
        for (y in 0 until bmp.height) {
            bmp.getPixels(row, 0, bmp.width, 0, y, bmp.width, 1)
            for (px in row) {
                rgb[off++] = ((px shr 16) and 0xFF).toByte()
                rgb[off++] = ((px shr 8) and 0xFF).toByte()
                rgb[off++] = (px and 0xFF).toByte()
            }
        }
        if (!compress) return rgb
        val level = when {
            quality < 20 -> 9
            quality < 40 -> 8
            quality < 60 -> 6
            quality < 80 -> 4
            else -> 2
        }
        val deflater = java.util.zip.Deflater(level)
        deflater.setInput(rgb)
        deflater.finish()
        val out = ByteArrayOutputStream(rgb.size)
        val buf = ByteArray(8192)
        while (!deflater.finished()) {
            out.write(buf, 0, deflater.deflate(buf))
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun writeTag(buf: ByteBuffer, tag: Int, type: Int, count: Int, value: Int) {
        buf.putShort(tag.toShort())
        buf.putShort(type.toShort())
        buf.putInt(count)
        if (type == 3) {
            buf.putShort(value.toShort())
            buf.putShort(0)
        } else {
            buf.putInt(value)
        }
    }
}
