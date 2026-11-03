package com.pixload.app

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

internal class Mp4HardwareEncoder private constructor(
    private val codec: MediaCodec,
    private val muxer: MediaMuxer,
    private val output: File,
    private val width: Int,
    private val height: Int
) : Mp4Conversion.Encoder {
    private val pixels = IntArray(width * height)
    private val yuv = ByteArray(width * height * 3 / 2)
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var samples = 0L
    private var written = 0L
    private var ended = false
    private var muxerStarted = false

    override fun encode(bitmap: Bitmap, count: Int) = hardware {
        // Convert once per source frame, including when its delay repeats the same image.
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = pixels[y * width + x]
                val r = rgb shr 16 and 255
                val g = rgb shr 8 and 255
                val b = rgb and 255
                yuv[y * width + x] = ((66 * r + 129 * g + 25 * b + 128 shr 8) + 16).toByte()
                if (x % 2 == 0 && y % 2 == 0) {
                    // Average each 2x2 chroma block before BT.601 limited-range conversion.
                    var red = 0; var green = 0; var blue = 0
                    for (dy in 0..1) for (dx in 0..1) {
                        val p = pixels[(y + dy) * width + x + dx]
                        red += p shr 16 and 255
                        green += p shr 8 and 255
                        blue += p and 255
                    }
                    red = (red + 2) / 4; green = (green + 2) / 4; blue = (blue + 2) / 4
                    val chroma = y / 2 * (width / 2) + x / 2
                    yuv[width * height + chroma] = ((-38 * red - 74 * green + 112 * blue + 128 shr 8) + 128).toByte()
                    yuv[width * height * 5 / 4 + chroma] = ((112 * red - 94 * green - 18 * blue + 128 shr 8) + 128).toByte()
                }
            }
        }
        repeat(count) {
            val index = input()
            val image = codec.getInputImage(index) ?: throw IOException("Encoder has no flexible YUV input image")
            image.use {
                copyPlane(it.planes[0], 0, width, height)
                copyPlane(it.planes[1], width * height, width / 2, height / 2)
                copyPlane(it.planes[2], width * height * 5 / 4, width / 2, height / 2)
            }
            codec.queueInputBuffer(index, 0, yuv.size, samples * 1_000_000 / 30, 0)
            samples++
            drain(0)
        }
    }

    private fun copyPlane(plane: Image.Plane, offset: Int, columns: Int, rows: Int) {
        val buffer = plane.buffer
        val start = buffer.position()
        for (row in 0 until rows) {
            if (plane.pixelStride == 1) {
                buffer.position(start + row * plane.rowStride)
                buffer.put(yuv, offset + row * columns, columns)
            } else {
                for (column in 0 until columns) {
                    buffer.put(start + row * plane.rowStride + column * plane.pixelStride,
                        yuv[offset + row * columns + column])
                }
            }
        }
    }

    private fun input(): Int {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (true) {
            val index = codec.dequeueInputBuffer(10_000)
            if (index >= 0) return index
            drain(0)
            if (SystemClock.elapsedRealtime() >= deadline) throw IOException("AVC input timed out")
        }
    }

    private fun drain(timeoutUs: Long): Boolean {
        val index = codec.dequeueOutputBuffer(info, timeoutUs)
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            check(track == -1) { "AVC output format changed twice" }
            val format = codec.outputFormat
            check(format.containsKey("csd-0") && format.containsKey("csd-1")) { "AVC configuration missing" }
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            track = muxer.addTrack(format)
            muxer.start()
            muxerStarted = true
        } else if (index >= 0) {
            try {
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    check(muxerStarted) { "AVC sample before configuration" }
                    check(kotlin.math.abs(info.presentationTimeUs - written * 1_000_000 / 30) <= 1) {
                        "AVC encoder reordered or changed timestamps"
                    }
                    val buffer = codec.getOutputBuffer(index) ?: throw IOException("Missing AVC output")
                    info.flags = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()
                    muxer.writeSampleData(track, buffer, info)
                    written++
                }
                if (eos) ended = true
            } finally {
                codec.releaseOutputBuffer(index, false)
            }
        }
        return index != MediaCodec.INFO_TRY_AGAIN_LATER
    }

    override fun finish() = hardware {
        val index = input()
        codec.queueInputBuffer(index, 0, 0, samples * 1_000_000 / 30, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        var deadline = SystemClock.elapsedRealtime() + 10_000
        while (!ended) {
            if (drain(10_000)) deadline = SystemClock.elapsedRealtime() + 10_000
            if (SystemClock.elapsedRealtime() >= deadline) throw IOException("AVC output timed out")
        }
        check(written == samples && written > 0) { "AVC encoder dropped samples" }
        // An explicit empty EOS sample pins the final duration, including one-sample videos.
        info.set(0, 0, samples * 1_000_000 / 30, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        muxer.writeSampleData(track, ByteBuffer.allocate(0), info)
        muxer.stop()
        muxerStarted = false
        fixDuration()
    }

    override fun close() = hardware {
        try { codec.release() } finally { muxer.release() }
    }

    private fun fixDuration() {
        // MediaMuxer can round header duration even when sample PTS and the EOS timestamp are exact.
        // Patch only timing fields in its single-track file; encoded data and sample tables stay put.
        RandomAccessFile(output, "rw").use { file ->
            var headers = 0
            fun boxes(start: Long, end: Long) {
                var offset = start
                while (offset < end) {
                    check(end - offset >= 8) { "Truncated MP4 box" }
                    file.seek(offset)
                    val shortSize = file.readInt().toLong() and 0xffffffffL
                    val type = file.readInt()
                    val header = if (shortSize == 1L) 16L else 8L
                    val size = when (shortSize) {
                        0L -> end - offset
                        1L -> file.readLong()
                        else -> shortSize
                    }
                    check(size >= header && size <= end - offset) { "Invalid MP4 box size" }
                    val data = offset + header
                    when (type) {
                        0x6d6f6f76, 0x7472616b, 0x6d646961, 0x65647473 -> boxes(data, offset + size) // moov/trak/mdia/edts
                        0x6d766864, 0x6d646864, 0x746b6864 -> { // mvhd/mdhd/tkhd
                            file.seek(data)
                            val version = file.readUnsignedByte()
                            check(version <= 1) { "Unsupported MP4 timing header" }
                            val durationOffset = when (type) {
                                0x746b6864 -> if (version == 1) 28L else 20L
                                else -> if (version == 1) 24L else 16L
                            }
                            check(data + durationOffset + (if (version == 1) 8 else 4) <= offset + size)
                            var ticks = samples
                            if (type != 0x746b6864) {
                                file.seek(data + durationOffset - 4)
                                if (type == 0x6d646864) {
                                    val scale = file.readInt()
                                    check(scale > 0 && scale % 30 == 0) { "Inexact MP4 media timescale" }
                                    ticks *= scale / 30
                                } else file.writeInt(30)
                            }
                            file.seek(data + durationOffset)
                            if (version == 1) file.writeLong(ticks) else file.writeInt(ticks.toInt())
                            headers++
                        }
                        0x656c7374 -> { // elst: baseline AVC has no reordering or initial empty edit.
                            file.seek(data)
                            val version = file.readUnsignedByte()
                            check(version <= 1 && size - header >= if (version == 1) 28 else 20)
                            file.seek(data + 4)
                            check(file.readInt() == 1) { "Unexpected MP4 edit list" }
                            file.seek(data + if (version == 1) 16 else 12)
                            check((if (version == 1) file.readLong() else file.readInt().toLong()) == 0L)
                            check(file.readInt() == 0x00010000) { "Unexpected MP4 edit rate" }
                            file.seek(data + 8)
                            if (version == 1) file.writeLong(samples) else file.writeInt(samples.toInt())
                        }
                    }
                    offset += size
                }
            }
            boxes(0, file.length())
            check(headers == 3) { "Expected one MP4 video track" }
        }
    }

    companion object {
        fun create(output: File, width: Int, height: Int): Mp4HardwareEncoder = hardware {
            val candidate = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull {
                it.isEncoder && (if (Build.VERSION.SDK_INT >= 29) it.isHardwareAccelerated
                    else !it.name.startsWith("OMX.google.") && !it.name.startsWith("c2.android.")) &&
                    it.supportedTypes.any { type -> type.equals("video/avc", true) } &&
                    it.getCapabilitiesForType("video/avc").colorFormats.contains(
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            } ?: throw IOException("No hardware AVC encoder with flexible YUV420")
            val caps = candidate.getCapabilitiesForType("video/avc")
            Log.i("PixloadMp4", "encoder=${candidate.name} colors=${caps.colorFormats.contentToString()} selected=YUV420Flexible")
            val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_BIT_RATE, maxOf(1_000_000, width * height * 18))
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_NTSC)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            }
            var codec: MediaCodec? = null
            var muxer: MediaMuxer? = null
            try {
                codec = MediaCodec.createByCodecName(candidate.name)
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                Mp4HardwareEncoder(codec, muxer, output, width, height)
            } catch (error: Throwable) {
                try { codec?.release() } catch (release: Exception) { error.addSuppressed(release) }
                try { muxer?.release() } catch (release: Exception) { error.addSuppressed(release) }
                if (muxer != null) output.delete()
                throw error
            }
        }

        private inline fun <T> hardware(block: () -> T): T = try { block() }
        catch (error: Exception) { throw Mp4Conversion.HardwareFailure(error) }
    }
}
