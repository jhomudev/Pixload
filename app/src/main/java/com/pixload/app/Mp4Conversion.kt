package com.pixload.app

import android.graphics.Bitmap
import android.util.Log
import org.jcodec.api.android.AndroidSequenceEncoder
import org.jcodec.common.io.NIOUtils
import org.jcodec.common.model.Rational
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

internal object Mp4Conversion {
    interface Encoder : AutoCloseable {
        fun encode(bitmap: Bitmap, count: Int)
        fun finish()
    }

    fun interface HardwareFactory {
        fun create(output: File, width: Int, height: Int): Encoder
    }

    class HardwareFailure(cause: Exception) : RuntimeException("Hardware MP4 encoding failed", cause)

    fun convert(
        archive: File, frames: JSONArray, output: File, progress: UgoiraConverter.Progress,
        decode: (ZipFile, String, Int) -> Bitmap,
        hardware: HardwareFactory = HardwareFactory(Mp4HardwareEncoder::create)
    ) {
        var notified = 0
        var completed = 0
        var terminal: Throwable? = null

        fun attempt(useHardware: Boolean) {
            ZipFile(archive).use { zip ->
                if (frames.length() == 0) throw IOException("La animación no contiene fotogramas.")
                val first = decode(zip, frames.getJSONObject(0).getString("file"), 1280)
                var encoder: Encoder? = null
                var failure: Throwable? = null
                var duration = 0L
                var encoded = 0L
                var finalized = false
                try {
                    val width = first.width - first.width % 2
                    val height = first.height - first.height % 2
                    if (width < 2 || height < 2) throw IOException("Fotogramas demasiado pequeños para MP4.")
                    encoder = if (useHardware) hardware.create(output, width, height) else software(output)
                    val limit = if (terminal != null) completed else frames.length()
                    for (i in 0 until limit) {
                        val frame = frames.getJSONObject(i)
                        val bitmap = if (i == 0) first else decode(zip, frame.getString("file"), 1280)
                        try {
                            val resized = if (bitmap.width == width && bitmap.height == height) bitmap
                            else Bitmap.createScaledBitmap(bitmap, width, height, true)
                            try {
                                duration += maxOf(20, frame.optInt("delay", 100)).toLong()
                                val needed = maxOf(1L, Math.round(duration * 30 / 1000.0))
                                if (needed > 18000) throw IOException("Animación demasiado larga para convertir a MP4 (máximo 10 minutos).")
                                val count = (needed - encoded).toInt()
                                if (count > 0) encoder.encode(resized, count)
                                encoded = needed
                            } finally {
                                if (resized !== bitmap) resized.recycle()
                            }
                        } finally { bitmap.recycle() }
                        completed = i + 1
                        if (i >= notified && terminal == null) {
                            notified = i + 1
                            try { progress.update(i + 1, frames.length()) }
                            catch (error: Throwable) { terminal = error; throw error }
                        }
                    }
                } catch (error: Throwable) {
                    failure = error
                    if (error !is HardwareFailure && terminal == null) terminal = error
                } finally {
                    if (!first.isRecycled) first.recycle()
                    try {
                        encoder?.finish()
                        finalized = encoded > 0
                    } catch (error: Throwable) {
                        if (error is HardwareFailure) failure = error
                        else if (failure == null) failure = error else failure.addSuppressed(error)
                    } finally {
                        try { encoder?.close() } catch (error: Exception) {
                            if (failure == null) failure = error else failure.addSuppressed(error)
                        }
                        if (encoder != null && !finalized) output.delete()
                    }
                }
                failure?.let { throw it }
            }
        }

        try {
            attempt(true)
        } catch (error: HardwareFailure) {
            Log.w("PixloadMp4", "Restarting MP4 from the first frame with JCodec", error)
            // JCodec opens a truncating channel only after all hardware handles have been released.
            try { attempt(false) } catch (fallback: Throwable) {
                if (terminal != null && terminal !== fallback) {
                    terminal.addSuppressed(fallback)
                    throw terminal
                }
                throw fallback
            }
            terminal?.let { throw it }
        }
    }

    private fun software(output: File): Encoder {
        val channel = NIOUtils.writableChannel(output)
        val encoder = try { AndroidSequenceEncoder(channel, Rational.R(30, 1)) }
        catch (error: Throwable) {
            try { channel.close() } catch (close: Exception) { error.addSuppressed(close) }
            output.delete()
            throw error
        }
        return object : Encoder {
            override fun encode(bitmap: Bitmap, count: Int) {
                repeat(count) { encoder.encodeImage(bitmap) }
            }
            override fun finish() { encoder.finish() }
            override fun close() { channel.close() }
        }
    }
}
