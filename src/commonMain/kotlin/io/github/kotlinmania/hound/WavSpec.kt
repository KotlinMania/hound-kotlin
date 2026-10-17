package io.github.kotlinmania.hound

import kotlin.math.min

/**
 * Specifies whether a sample is stored as an "IEEE Float" or an integer.
 */
public enum class SampleFormat {
    /**
     * Wave files with the `WAVE_FORMAT_IEEE_FLOAT` format tag store samples as floating point
     * values.
     *
     * Values are normally in the range `[-1.0, 1.0]`.
     */
    Float,

    /**
     * Wave files with the `WAVE_FORMAT_PCM` format tag store samples as integer values.
     */
    IntSample,
}

/**
 * Specifies properties of the audio data.
 */
public data class WavSpec(
    /** The number of channels. */
    public val channels: UShort,
    /**
     * The number of samples per second.
     *
     * A common value is 44100, this is 44.1 kHz which is used for CD audio.
     */
    public val sampleRate: UInt,
    /**
     * The number of bits per sample.
     *
     * A common value is 16 bits per sample, which is used for CD audio.
     */
    public val bitsPerSample: UShort,
    /** Whether the wav's samples are float or integer values. */
    public val sampleFormat: SampleFormat,
) {
    /**
     * Get "stand-alone" wav header representing infinite or unknown size wav file.
     * Use this if you need to write audio data to non-seekable sinks (like stdout).
     *
     * Such wav files are produced e.g. by FFmpeg and have `0xFFFFFFFF` instead of chunk sizes.
     */
    public fun intoHeaderForInfiniteFile(): ByteArray {
        val c = HoundByteCursor()
        val w = WavWriter.new(c, this)
        w.finalize()
        val v = c.toByteArray()

        // Set WAVE chunk size to a special signal value
        v[4] = 0xFF.toByte()
        v[5] = 0xFF.toByte()
        v[6] = 0xFF.toByte()
        v[7] = 0xFF.toByte()

        // Detect fmt size, get offset of data chunk's size and set it to signal value
        when (v[16].toInt() and 0xFF) {
            0x10 -> {
                v[0x28] = 0xFF.toByte()
                v[0x29] = 0xFF.toByte()
                v[0x2A] = 0xFF.toByte()
                v[0x2B] = 0xFF.toByte()
            }

            0x28 -> {
                v[0x40] = 0xFF.toByte()
                v[0x41] = 0xFF.toByte()
                v[0x42] = 0xFF.toByte()
                v[0x43] = 0xFF.toByte()
            }

            else -> {
                error("unreachable fmt chunk size: ${v[16]}")
            }
        }

        return v
    }
}

/**
 * Specifies properties of the audio data, as well as the layout of the stream.
 */
public data class WavSpecEx(
    /**
     * The normal information about the audio data.
     *
     * Bits per sample here is the number of _used_ bits per sample, not the
     * number of bits used to _store_ a sample.
     */
    public val spec: WavSpec,
    /** The number of bytes used to store a sample. */
    public val bytesPerSample: UShort,
)

/**
 * Represents an I/O error encountered while reading, writing, or seeking a WAVE stream.
 */
public class HoundIoError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The error type for operations on [WavReader] and [WavWriter].
 */
public sealed class HoundError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** An IO error occurred in the underlying reader or writer. */
    public class IoError(
        public val err: HoundIoError,
    ) : HoundError(err.message ?: "I/O error", err)

    /** Ill-formed WAVE data was encountered. */
    public class FormatError(
        public val reason: String,
    ) : HoundError("Ill-formed WAVE file: $reason")

    /**
     * The sample has more bits than the destination type.
     */
    public class TooWide : HoundError("The sample has more bits than the destination type.")

    /**
     * The number of samples written is not a multiple of the number of channels.
     */
    public class UnfinishedSample : HoundError("The number of samples written is not a multiple of the number of channels.")

    /** The format is not supported. */
    public class Unsupported : HoundError("The wave format of the file is not supported.")

    /**
     * The sample format is different than the destination format.
     */
    public class InvalidSampleFormat : HoundError("The sample format differs from the destination format.")
}

/** Subformat type for PCM audio with integer samples. */
internal val KSDATAFORMAT_SUBTYPE_PCM: ByteArray =
    byteArrayOf(
        0x01,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x10,
        0x00,
        0x80.toByte(),
        0x00,
        0x00,
        0xaa.toByte(),
        0x00,
        0x38,
        0x9b.toByte(),
        0x71,
    )

/** Subformat type for IEEE_FLOAT audio with float samples. */
internal val KSDATAFORMAT_SUBTYPE_IEEE_FLOAT: ByteArray =
    byteArrayOf(
        0x03,
        0x00,
        0x00,
        0x00,
        0x00,
        0x00,
        0x10,
        0x00,
        0x80.toByte(),
        0x00,
        0x00,
        0xaa.toByte(),
        0x00,
        0x38,
        0x9b.toByte(),
        0x71,
    )

/**
 * Converts an unsigned integer in the range 0-255 to a signed one in the range -128-127.
 */
internal fun signedFromU8(x: UByte): Byte = (x.toShort() - 128).toByte()

/**
 * Converts a signed integer in the range -128-127 to an unsigned one in the range 0-255.
 */
internal fun u8FromSigned(x: Byte): UByte = (x.toShort() + 128).toUByte()

/**
 * Tries to cast the sample to an 8-bit signed integer, throwing [HoundError.TooWide] on overflow.
 */
internal fun narrowToI8(x: Int): Byte {
    if (x < Byte.MIN_VALUE.toInt() || x > Byte.MAX_VALUE.toInt()) {
        throw HoundError.TooWide()
    }
    return x.toByte()
}

/**
 * Tries to cast the sample to a 16-bit signed integer, throwing [HoundError.TooWide] on overflow.
 */
internal fun narrowToI16(x: Int): Short {
    if (x < Short.MIN_VALUE.toInt() || x > Short.MAX_VALUE.toInt()) {
        throw HoundError.TooWide()
    }
    return x.toShort()
}

/**
 * Tries to cast the sample to a 24-bit signed integer, throwing [HoundError.TooWide] on overflow.
 */
internal fun narrowToI24(x: Int): Int {
    if (x < -(1 shl 23) || x > (1 shl 23) - 1) {
        throw HoundError.TooWide()
    }
    return x
}

internal fun writeSampleI8(sample: Byte, writer: HoundWrite, bits: UShort) {
    writePaddedI8(sample, writer, bits, (bits / 8u).toUShort())
}

internal fun writePaddedI8(sample: Byte, writer: HoundWrite, bits: UShort, byteWidth: UShort) {
    when {
        bits == 8u.toUShort() && byteWidth == 1u.toUShort() -> writer.writeU8(u8FromSigned(sample))
        bits == 16u.toUShort() && byteWidth == 2u.toUShort() -> writer.writeLeI16(sample.toShort())
        bits == 24u.toUShort() && byteWidth == 3u.toUShort() -> writer.writeLeI24(sample.toInt())
        bits == 24u.toUShort() && byteWidth == 4u.toUShort() -> writer.writeLeI244(sample.toInt())
        bits == 32u.toUShort() && byteWidth == 4u.toUShort() -> writer.writeLeI32(sample.toInt())
        else -> throw HoundError.Unsupported()
    }
}

internal fun readSampleI8(reader: HoundRead, fmt: SampleFormat, bytes: UShort, bits: UShort): Byte {
    if (fmt != SampleFormat.IntSample) {
        throw HoundError.InvalidSampleFormat()
    }
    return when {
        bytes == 1u.toUShort() && bits == 8u.toUShort() -> signedFromU8(reader.readU8())
        bytes > 1u.toUShort() -> throw HoundError.TooWide()
        else -> throw HoundError.Unsupported()
    }
}

internal fun writeSampleI16(sample: Short, writer: HoundWrite, bits: UShort) {
    writePaddedI16(sample, writer, bits, (bits / 8u).toUShort())
}

internal fun writePaddedI16(sample: Short, writer: HoundWrite, bits: UShort, byteWidth: UShort) {
    when {
        bits == 8u.toUShort() && byteWidth == 1u.toUShort() -> {
            writer.writeU8(u8FromSigned(narrowToI8(sample.toInt())))
        }

        bits == 16u.toUShort() && byteWidth == 2u.toUShort() -> {
            writer.writeLeI16(sample)
        }

        bits == 24u.toUShort() && byteWidth == 3u.toUShort() -> {
            writer.writeLeI24(sample.toInt())
        }

        bits == 24u.toUShort() && byteWidth == 4u.toUShort() -> {
            writer.writeLeI244(sample.toInt())
        }

        bits == 32u.toUShort() && byteWidth == 4u.toUShort() -> {
            writer.writeLeI32(sample.toInt())
        }

        else -> {
            throw HoundError.Unsupported()
        }
    }
}

internal fun readSampleI16(reader: HoundRead, fmt: SampleFormat, bytes: UShort, bits: UShort): Short {
    if (fmt != SampleFormat.IntSample) {
        throw HoundError.InvalidSampleFormat()
    }
    return when {
        bytes == 1u.toUShort() && bits == 8u.toUShort() -> signedFromU8(reader.readU8()).toShort()
        bytes == 2u.toUShort() && bits == 16u.toUShort() -> reader.readLeI16()
        bytes > 2u.toUShort() -> throw HoundError.TooWide()
        else -> throw HoundError.Unsupported()
    }
}

internal fun writeSampleI32(sample: Int, writer: HoundWrite, bits: UShort) {
    writePaddedI32(sample, writer, bits, (bits / 8u).toUShort())
}

internal fun writePaddedI32(sample: Int, writer: HoundWrite, bits: UShort, byteWidth: UShort) {
    when {
        bits == 8u.toUShort() && byteWidth == 1u.toUShort() -> {
            writer.writeU8(u8FromSigned(narrowToI8(sample)))
        }

        bits == 16u.toUShort() && byteWidth == 2u.toUShort() -> {
            writer.writeLeI16(narrowToI16(sample))
        }

        bits == 24u.toUShort() && byteWidth == 3u.toUShort() -> {
            writer.writeLeI24(narrowToI24(sample))
        }

        bits == 24u.toUShort() && byteWidth == 4u.toUShort() -> {
            writer.writeLeI244(narrowToI24(sample))
        }

        bits == 32u.toUShort() && byteWidth == 4u.toUShort() -> {
            writer.writeLeI32(sample)
        }

        else -> {
            throw HoundError.Unsupported()
        }
    }
}

internal fun readSampleI32(reader: HoundRead, fmt: SampleFormat, bytes: UShort, bits: UShort): Int {
    if (fmt != SampleFormat.IntSample) {
        throw HoundError.InvalidSampleFormat()
    }
    return when {
        bytes == 1u.toUShort() && bits == 8u.toUShort() -> signedFromU8(reader.readU8()).toInt()
        bytes == 2u.toUShort() && bits == 16u.toUShort() -> reader.readLeI16().toInt()
        bytes == 3u.toUShort() && bits == 24u.toUShort() -> reader.readLeI24()
        bytes == 4u.toUShort() && bits == 24u.toUShort() -> reader.readLeI244()
        bytes == 4u.toUShort() && bits == 32u.toUShort() -> reader.readLeI32()
        bytes > 4u.toUShort() -> throw HoundError.TooWide()
        else -> throw HoundError.Unsupported()
    }
}

internal fun writeSampleF32(sample: Float, writer: HoundWrite, bits: UShort) {
    writePaddedF32(sample, writer, bits, (bits / 8u).toUShort())
}

internal fun writePaddedF32(sample: Float, writer: HoundWrite, bits: UShort, byteWidth: UShort) {
    when {
        bits == 32u.toUShort() && byteWidth == 4u.toUShort() -> writer.writeLeF32(sample)
        else -> throw HoundError.Unsupported()
    }
}

internal fun readSampleF32(reader: HoundRead, fmt: SampleFormat, bytes: UShort, bits: UShort): Float {
    if (fmt != SampleFormat.Float) {
        throw HoundError.InvalidSampleFormat()
    }
    return when {
        bytes == 4u.toUShort() && bits == 32u.toUShort() -> reader.readLeF32()
        bytes > 4u.toUShort() -> throw HoundError.TooWide()
        else -> throw HoundError.Unsupported()
    }
}

/**
 * Enumeration of possible methods to seek within an I/O stream.
 */
public sealed class HoundSeekFrom {
    public data class Start(
        public val pos: ULong,
    ) : HoundSeekFrom()

    public data class End(
        public val offset: Long,
    ) : HoundSeekFrom()

    public data class Current(
        public val offset: Long,
    ) : HoundSeekFrom()
}

/**
 * Trait for reading bytes from a WAVE source.
 */
public interface HoundRead {
    public fun read(buf: ByteArray, offset: Int = 0, length: Int = buf.size - offset): Int

    public fun readByte(): Int {
        val buf = ByteArray(1)
        val n = read(buf, 0, 1)
        if (n <= 0) {
            throw HoundError.IoError(HoundIoError("Failed to read enough bytes."))
        }
        return buf[0].toInt() and 0xFF
    }
}

/**
 * Trait for seeking within a WAVE stream.
 */
public interface HoundSeek {
    public fun seek(pos: HoundSeekFrom): ULong
}

/**
 * Trait for writing bytes to a WAVE destination.
 */
public interface HoundWrite {
    public fun write(buf: ByteArray, offset: Int = 0, length: Int = buf.size - offset): Int

    public fun writeByte(b: Int) {
        val buf = ByteArray(1)
        buf[0] = b.toByte()
        writeAll(buf, 0, 1)
    }

    public fun writeAll(buf: ByteArray, offset: Int = 0, length: Int = buf.size - offset) {
        var n = 0
        while (n < length) {
            val written = write(buf, offset + n, length - n)
            if (written <= 0) {
                throw HoundError.IoError(HoundIoError("Failed to write all bytes."))
            }
            n += written
        }
    }

    public fun flush() {}
}

/**
 * Combined [HoundRead] and [HoundSeek] interface.
 */
public interface HoundReadSeek :
    HoundRead,
    HoundSeek

/**
 * Combined [HoundWrite] and [HoundSeek] interface.
 */
public interface HoundWriteSeek :
    HoundWrite,
    HoundSeek

/**
 * Combined [HoundRead], [HoundWrite], and [HoundSeek] interface.
 */
public interface HoundReadWriteSeek :
    HoundReadSeek,
    HoundWriteSeek

/**
 * In-memory cursor over a growable byte buffer implementing [HoundReadWriteSeek].
 */
public class HoundByteCursor internal constructor(
    initialData: ByteArray,
    private val onFlush: ((ByteArray) -> Unit)?,
) : HoundReadWriteSeek {
    public constructor(initialData: ByteArray = ByteArray(0)) : this(initialData, null)

    private var buffer: ByteArray = initialData.copyOf()
    private var length: Int = initialData.size
    private var pos: Int = 0

    public val position: Int
        get() = pos

    public val size: Int
        get() = length

    public fun setPosition(newPosition: Int) {
        pos = newPosition
    }

    public fun intoInner(): ByteArray = buffer.copyOf(length)

    public fun toByteArray(): ByteArray = buffer.copyOf(length)

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity <= buffer.size) return
        var newCap = if (buffer.isEmpty()) 64 else buffer.size
        while (newCap < minCapacity) {
            newCap *= 2
        }
        val next = ByteArray(newCap)
        for (i in 0 until length) {
            next[i] = buffer[i]
        }
        buffer = next
    }

    override fun readByte(): Int {
        if (pos >= this.length) {
            throw HoundError.IoError(HoundIoError("Failed to read enough bytes."))
        }
        val b = buffer[pos].toInt() and 0xFF
        pos += 1
        return b
    }

    override fun read(buf: ByteArray, offset: Int, length: Int): Int {
        if (length == 0 || pos >= this.length) {
            return 0
        }
        val available = this.length - pos
        val toRead = min(length, available)
        for (i in 0 until toRead) {
            buf[offset + i] = buffer[pos + i]
        }
        pos += toRead
        return toRead
    }

    override fun writeByte(b: Int) {
        val endPos = pos + 1
        ensureCapacity(endPos)
        buffer[pos] = b.toByte()
        pos = endPos
        if (pos > this.length) {
            this.length = pos
        }
        onFlush?.invoke(buffer.copyOf(this.length))
    }

    override fun write(buf: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val endPos = pos + length
        ensureCapacity(endPos)
        for (i in 0 until length) {
            buffer[pos + i] = buf[offset + i]
        }
        pos = endPos
        if (pos > this.length) {
            this.length = pos
        }
        onFlush?.invoke(buffer.copyOf(this.length))
        return length
    }

    override fun flush() {
        onFlush?.invoke(buffer.copyOf(this.length))
    }

    override fun seek(pos: HoundSeekFrom): ULong {
        val target: Long =
            when (pos) {
                is HoundSeekFrom.Start -> pos.pos.toLong()
                is HoundSeekFrom.End -> this.length.toLong() + pos.offset
                is HoundSeekFrom.Current -> this.pos.toLong() + pos.offset
            }
        if (target < 0L) {
            throw HoundError.IoError(
                HoundIoError("Invalid seek to negative or overflowing position"),
            )
        }
        this.pos = target.toInt()
        return target.toULong()
    }
}

/**
 * Virtual file store used by [WavReader.open], [WavWriter.create], and [WavWriter.append]
 * across all multiplatform targets.
 */
public object HoundVirtualFs {
    private val files: MutableMap<String, ByteArray> = mutableMapOf()

    public fun putFile(path: String, bytes: ByteArray) {
        files[path] = bytes.copyOf()
    }

    public fun readFile(path: String): ByteArray =
        files[path]?.copyOf()
            ?: throw HoundError.IoError(HoundIoError("File not found: $path"))

    public fun fileLength(path: String): ULong =
        files[path]?.size?.toULong()
            ?: throw HoundError.IoError(HoundIoError("File not found: $path"))

    public fun listFiles(prefix: String): List<String> =
        files.keys.filter { it.startsWith(prefix) }.sorted()

    internal fun openRead(path: String): HoundByteCursor =
        HoundByteCursor(readFile(path))

    internal fun openCreate(path: String): HoundByteCursor {
        files[path] = ByteArray(0)
        return HoundByteCursor(ByteArray(0)) { updated ->
            files[path] = updated
        }
    }

    internal fun openAppend(path: String): HoundByteCursor {
        val existing = readFile(path)
        return HoundByteCursor(existing) { updated ->
            files[path] = updated
        }
    }
}
