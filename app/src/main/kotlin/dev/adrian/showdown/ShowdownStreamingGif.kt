package dev.adrian.showdown

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Arrays

internal class ShowdownStreamingGif private constructor(
    private val width: Int,
    private val height: Int,
    private val outputWidth: Int,
    private val outputHeight: Int,
    private val frames: List<Frame>,
    private val canvasPixels: IntArray,
    private val outputBitmap: Bitmap,
    private val memoryBytes: Int
) {
    private var currentFrameIndex = -1
    private var savedCanvas: IntArray? = null
    private var released = false

    val isAnimated: Boolean get() = !released && frames.size > 1
    val estimatedMemoryBytes: Int get() = memoryBytes
    val sourceWidth: Int get() = width
    val sourceHeight: Int get() = height
    val frameWidth: Int get() = outputWidth
    val frameHeight: Int get() = outputHeight

    fun frameAt(elapsedMillis: Long): Bitmap? {
        if (!isAnimated) return null
        val targetFrame = frameIndexAt(elapsedMillis)
        if (targetFrame < currentFrameIndex) reset()
        while (currentFrameIndex < targetFrame) {
            renderFrame(currentFrameIndex + 1, currentFrameIndex + 1 == targetFrame)
        }
        if (currentFrameIndex < 0) renderFrame(0, true)
        return outputBitmap
    }

    fun release() {
        if (released) return
        released = true
        outputBitmap.recycle()
        savedCanvas = null
        currentFrameIndex = -1
    }

    private fun frameIndexAt(elapsedMillis: Long): Int {
        val position = elapsedMillis.coerceAtLeast(0L) % totalDurationMillis()
        var elapsed = 0L
        frames.forEachIndexed { index, frame ->
            elapsed += frame.durationMillis
            if (position < elapsed) return index
        }
        return frames.lastIndex
    }

    private fun totalDurationMillis(): Long = frames.sumOf { it.durationMillis }.coerceAtLeast(1L)

    private fun reset() {
        Arrays.fill(canvasPixels, 0)
        savedCanvas = null
        currentFrameIndex = -1
    }

    private fun renderFrame(index: Int, publish: Boolean) {
        if (currentFrameIndex >= 0) applyDisposal(frames[currentFrameIndex])
        val frame = frames[index]
        savedCanvas = if (frame.disposal == DISPOSAL_PREVIOUS) canvasPixels.copyOf() else null
        decodeFrame(frame)
        if (publish) publishFrame()
        currentFrameIndex = index
    }

    private fun applyDisposal(frame: Frame) {
        when (frame.disposal) {
            DISPOSAL_BACKGROUND -> clearFrame(frame)
            DISPOSAL_PREVIOUS -> savedCanvas?.copyInto(canvasPixels) ?: clearFrame(frame)
        }
    }

    private fun clearFrame(frame: Frame) {
        val left = (frame.left * outputWidth / width).coerceIn(0, outputWidth)
        val top = (frame.top * outputHeight / height).coerceIn(0, outputHeight)
        val right = ((frame.left + frame.width) * outputWidth / width).coerceIn(left, outputWidth)
        val bottom = ((frame.top + frame.height) * outputHeight / height).coerceIn(top, outputHeight)
        for (y in top until bottom) {
            Arrays.fill(canvasPixels, y * outputWidth + left, y * outputWidth + right, 0)
        }
    }

    private fun decodeFrame(frame: Frame) {
        val rows = if (frame.interlaced) interlacedRows(frame.height) else IntArray(frame.height) { it }
        var pixelIndex = 0
        decodeLzw(frame) { colorIndex ->
            if (pixelIndex >= frame.width * frame.height) return@decodeLzw
            val row = rows[pixelIndex / frame.width]
            val column = pixelIndex % frame.width
            val x = frame.left + column
            val y = frame.top + row
            if (x in 0 until width && y in 0 until height && colorIndex != frame.transparentIndex) {
                val outputX = (x * outputWidth / width).coerceIn(0, outputWidth - 1)
                val outputY = (y * outputHeight / height).coerceIn(0, outputHeight - 1)
                canvasPixels[outputY * outputWidth + outputX] = frame.palette.getOrElse(colorIndex) { 0 }
            }
            pixelIndex += 1
        }
    }

    private fun publishFrame() {
        outputBitmap.setPixels(canvasPixels, 0, outputWidth, 0, 0, outputWidth, outputHeight)
    }

    private fun decodeLzw(frame: Frame, emit: (Int) -> Unit) {
        val minimumCodeSize = frame.minimumCodeSize
        if (minimumCodeSize !in 2..8) return
        val clearCode = 1 shl minimumCodeSize
        val endCode = clearCode + 1
        val prefix = IntArray(MAX_LZW_CODES)
        val suffix = ByteArray(MAX_LZW_CODES)
        val stack = ByteArray(MAX_LZW_CODES)
        val reader = GifBitReader(frame.imageData)
        var codeSize = minimumCodeSize + 1
        var availableCode = clearCode + 2
        var previousCode = -1
        var firstColor = 0
        var stackSize = 0
        while (true) {
            val code = reader.read(codeSize) ?: return
            when {
                code == clearCode -> {
                    codeSize = minimumCodeSize + 1
                    availableCode = clearCode + 2
                    previousCode = -1
                }
                code == endCode -> return
                previousCode < 0 -> {
                    if (code >= clearCode) return
                    firstColor = code
                    emit(code)
                    previousCode = code
                }
                else -> {
                    val inputCode = code
                    var resolvedCode = code
                    if (resolvedCode == availableCode) {
                        if (stackSize == stack.size) return
                        stack[stackSize++] = firstColor.toByte()
                        resolvedCode = previousCode
                    }
                    if (resolvedCode >= availableCode || resolvedCode >= MAX_LZW_CODES) return
                    while (resolvedCode >= clearCode) {
                        if (resolvedCode >= MAX_LZW_CODES || stackSize == stack.size) return
                        stack[stackSize++] = suffix[resolvedCode]
                        resolvedCode = prefix[resolvedCode]
                    }
                    if (resolvedCode < 0 || resolvedCode >= clearCode || stackSize == stack.size) return
                    firstColor = resolvedCode
                    stack[stackSize++] = firstColor.toByte()
                    while (stackSize > 0) emit(stack[--stackSize].toInt() and 0xff)
                    if (availableCode < MAX_LZW_CODES) {
                        prefix[availableCode] = previousCode
                        suffix[availableCode] = firstColor.toByte()
                        availableCode += 1
                        if (availableCode == (1 shl codeSize) && codeSize < MAX_LZW_BITS) codeSize += 1
                    }
                    previousCode = inputCode
                }
            }
        }
    }

    private data class Frame(
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val interlaced: Boolean,
        val palette: IntArray,
        val transparentIndex: Int,
        val disposal: Int,
        val durationMillis: Long,
        val minimumCodeSize: Int,
        val imageData: ByteArray
    )

    private data class GraphicControl(
        val disposal: Int = DISPOSAL_NONE,
        val durationMillis: Long = DEFAULT_FRAME_DURATION_MILLIS,
        val transparentIndex: Int = -1
    )

    private class GifBitReader(private val bytes: ByteArray) {
        private var bitOffset = 0

        fun read(bitCount: Int): Int? {
            if (bitOffset + bitCount > bytes.size * 8) return null
            var value = 0
            repeat(bitCount) { index ->
                val offset = bitOffset + index
                value = value or (((bytes[offset / 8].toInt() ushr (offset % 8)) and 1) shl index)
            }
            bitOffset += bitCount
            return value
        }
    }

    private class GifCursor(private val bytes: ByteArray) {
        var position = 0

        fun readByte(): Int? = if (position < bytes.size) bytes[position++].toInt() and 0xff else null

        fun readLittleEndian(): Int? {
            val low = readByte() ?: return null
            val high = readByte() ?: return null
            return low or (high shl 8)
        }

        fun readBytes(count: Int): ByteArray? {
            if (count < 0 || position + count > bytes.size) return null
            return bytes.copyOfRange(position, position + count).also { position += count }
        }

        fun readColorTable(size: Int): IntArray? {
            val bytes = readBytes(size * 3) ?: return null
            return IntArray(size) { index ->
                val offset = index * 3
                0xff000000.toInt() or
                    ((bytes[offset].toInt() and 0xff) shl 16) or
                    ((bytes[offset + 1].toInt() and 0xff) shl 8) or
                    (bytes[offset + 2].toInt() and 0xff)
            }
        }

        fun readSubBlocks(): ByteArray? {
            val output = ByteArrayOutputStream()
            while (true) {
                val size = readByte() ?: return null
                if (size == 0) return output.toByteArray()
                val block = readBytes(size) ?: return null
                output.write(block)
            }
        }
    }

    companion object {
        private const val MAX_LZW_CODES = 4096
        private const val MAX_LZW_BITS = 12
        private const val DEFAULT_FRAME_DURATION_MILLIS = 100L
        private const val DISPOSAL_NONE = 1
        private const val DISPOSAL_BACKGROUND = 2
        private const val DISPOSAL_PREVIOUS = 3

        fun fromFile(
            file: File,
            maxFrameDimension: Int,
            maxSourceDimension: Int,
            maxSourcePixels: Long,
            maxFileBytes: Long,
            maxFrames: Int
        ): ShowdownStreamingGif? {
            if (!file.isFile || file.length() !in 1L..maxFileBytes) return null
            val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
            return parse(
                bytes,
                maxFrameDimension,
                maxSourceDimension,
                maxSourcePixels,
                maxFrames
            )
        }

        private fun parse(
            bytes: ByteArray,
            maxFrameDimension: Int,
            maxSourceDimension: Int,
            maxSourcePixels: Long,
            maxFrames: Int
        ): ShowdownStreamingGif? {
            val cursor = GifCursor(bytes)
            val signature = cursor.readBytes(6)?.toString(StandardCharsets.US_ASCII) ?: return null
            if (signature != "GIF87a" && signature != "GIF89a") return null
            val width = cursor.readLittleEndian() ?: return null
            val height = cursor.readLittleEndian() ?: return null
            val packed = cursor.readByte() ?: return null
            cursor.readByte() ?: return null
            cursor.readByte() ?: return null
            if (width <= 0 || height <= 0 || maxOf(width, height) > maxSourceDimension || width.toLong() * height.toLong() > maxSourcePixels) return null
            val globalPalette = if (packed and 0x80 != 0) {
                cursor.readColorTable(1 shl ((packed and 0x07) + 1)) ?: return null
            } else {
                null
            }
            var graphicControl = GraphicControl()
            val frames = mutableListOf<Frame>()
            while (true) {
                when (val marker = cursor.readByte() ?: return null) {
                    0x21 -> {
                        when (cursor.readByte() ?: return null) {
                            0xf9 -> {
                                val blockSize = cursor.readByte() ?: return null
                                if (blockSize < 4) return null
                                val control = cursor.readByte() ?: return null
                                val delay = cursor.readLittleEndian() ?: return null
                                val transparent = cursor.readByte() ?: return null
                                if (blockSize > 4) cursor.readBytes(blockSize - 4) ?: return null
                                cursor.readByte() ?: return null
                                graphicControl = GraphicControl(
                                    disposal = when ((control shr 2) and 0x07) {
                                        DISPOSAL_BACKGROUND -> DISPOSAL_BACKGROUND
                                        DISPOSAL_PREVIOUS -> DISPOSAL_PREVIOUS
                                        else -> DISPOSAL_NONE
                                    },
                                    durationMillis = if (delay == 0) DEFAULT_FRAME_DURATION_MILLIS else (delay * 10L).coerceAtLeast(20L),
                                    transparentIndex = if (control and 0x01 != 0) transparent else -1
                                )
                            }
                            else -> cursor.readSubBlocks() ?: return null
                        }
                    }
                    0x2c -> {
                        if (frames.size >= maxFrames) return null
                        val left = cursor.readLittleEndian() ?: return null
                        val top = cursor.readLittleEndian() ?: return null
                        val frameWidth = cursor.readLittleEndian() ?: return null
                        val frameHeight = cursor.readLittleEndian() ?: return null
                        val imagePacked = cursor.readByte() ?: return null
                        if (frameWidth <= 0 || frameHeight <= 0 || left + frameWidth > width || top + frameHeight > height) return null
                        val localPalette = if (imagePacked and 0x80 != 0) {
                            cursor.readColorTable(1 shl ((imagePacked and 0x07) + 1)) ?: return null
                        } else {
                            null
                        }
                        val minimumCodeSize = cursor.readByte() ?: return null
                        val imageData = cursor.readSubBlocks() ?: return null
                        val palette = localPalette ?: globalPalette ?: return null
                        frames += Frame(
                            left,
                            top,
                            frameWidth,
                            frameHeight,
                            imagePacked and 0x40 != 0,
                            palette,
                            graphicControl.transparentIndex,
                            graphicControl.disposal,
                            graphicControl.durationMillis,
                            minimumCodeSize,
                            imageData
                        )
                        graphicControl = GraphicControl()
                    }
                    0x3b -> break
                    else -> return null
                }
            }
            if (frames.size < 2 || !hasDistinctFrames(frames)) return null
            val (outputWidth, outputHeight) = boundedAnimatedFrameSize(width, height, maxFrameDimension)
            val canvasPixels = IntArray(outputWidth * outputHeight)
            val outputBitmap = runCatching {
                Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            }.getOrElse {
                return null
            }
            val memoryBytes = frames.sumOf { it.imageData.size }
                .toLong()
                .plus(canvasPixels.size.toLong() * 4L)
                .plus(outputBitmap.allocationByteCount.toLong())
                .coerceIn(1L, Int.MAX_VALUE.toLong())
                .toInt()
            return runCatching {
                ShowdownStreamingGif(
                    width,
                    height,
                    outputWidth,
                    outputHeight,
                    frames,
                    canvasPixels,
                    outputBitmap,
                    memoryBytes
                )
            }.getOrElse {
                outputBitmap.recycle()
                null
            }
        }

        private fun hasDistinctFrames(frames: List<Frame>): Boolean {
            val first = frames.first()
            return frames.drop(1).any { frame ->
                frame.width != first.width ||
                    frame.height != first.height ||
                    frame.left != first.left ||
                    frame.top != first.top ||
                    frame.imageData.contentEquals(first.imageData).not()
            }
        }

        internal fun interlacedRows(height: Int): IntArray {
            val rows = IntArray(height)
            var index = 0
            val starts = intArrayOf(0, 4, 2, 1)
            val steps = intArrayOf(8, 8, 4, 2)
            starts.indices.forEach { pass ->
                var row = starts[pass]
                while (row < height && index < rows.size) {
                    rows[index++] = row
                    row += steps[pass]
                }
            }
            return rows
        }
    }
}
