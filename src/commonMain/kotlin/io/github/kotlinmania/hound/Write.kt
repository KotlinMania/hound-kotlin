// port-lint: source src/write.rs
package io.github.kotlinmania.hound

/** Writes an unsigned 8-bit integer. */
public fun HoundWrite.writeU8(x: UByte) {
    writeByte(x.toInt())
}

/** Writes a signed 16-bit integer in little-endian format. */
public fun HoundWrite.writeLeI16(x: Short) {
    writeLeU16(x.toUShort())
}

/** Writes an unsigned 16-bit integer in little-endian format. */
public fun HoundWrite.writeLeU16(x: UShort) {
    val u = x.toInt()
    writeByte(u and 0xff)
    writeByte((u ushr 8) and 0xff)
}

/** Writes a signed 24-bit integer in little-endian format. */
public fun HoundWrite.writeLeI24(x: Int) {
    writeLeU24(x.toUInt())
}

/** Writes a signed 24-bit integer in 4-byte little-endian format. */
public fun HoundWrite.writeLeI244(x: Int) {
    writeLeU32(x.toUInt() and 0x00ffffffu)
}

/** Writes an unsigned 24-bit integer in little-endian format. */
public fun HoundWrite.writeLeU24(x: UInt) {
    val u = x.toInt()
    writeByte(u and 0xff)
    writeByte((u ushr 8) and 0xff)
    writeByte((u ushr 16) and 0xff)
}

/** Writes a signed 32-bit integer in little-endian format. */
public fun HoundWrite.writeLeI32(x: Int) {
    writeLeU32(x.toUInt())
}

/** Writes an unsigned 32-bit integer in little-endian format. */
public fun HoundWrite.writeLeU32(x: UInt) {
    val u = x.toInt()
    writeByte(u and 0xff)
    writeByte((u ushr 8) and 0xff)
    writeByte((u ushr 16) and 0xff)
    writeByte((u ushr 24) and 0xff)
}

/** Writes an IEEE float in little-endian format. */
public fun HoundWrite.writeLeF32(x: Float) {
    writeLeU32(x.toRawBits().toUInt())
}

/**
 * Generates a bitmask with [channels] ones in the least significant bits.
 */
internal fun channelMask(channels: UShort): UInt {
    val clamped = if (channels > 18u.toUShort()) 18 else channels.toInt()
    var mask = 0u
    for (c in 0 until clamped) {
        mask = mask or (1u shl c)
    }
    return mask
}

private enum class FmtKind {
    PcmWaveFormat,
    WaveFormatExtensible,
}

private fun readAppend(reader: HoundReadSeek): Triple<WavSpecEx, UInt, UInt> {
    readWaveHeader(reader)
    val dataHeader = readUntilData(reader)
    val specEx = dataHeader.specEx
    val dataLen = dataHeader.dataLen

    val dataLenOffset = reader.seek(HoundSeekFrom.Current(0L)).toUInt() - 4u
    val spec = specEx.spec
    val numSamples = dataLen / specEx.bytesPerSample.toUInt()

    if (numSamples * specEx.bytesPerSample.toUInt() != dataLen) {
        throw HoundError.FormatError("data chunk length is not a multiple of sample size")
    }

    val supported =
        when {
            specEx.bytesPerSample == 1u.toUShort() && spec.bitsPerSample == 8u.toUShort() -> true
            specEx.bytesPerSample == 2u.toUShort() && spec.bitsPerSample == 16u.toUShort() -> true
            specEx.bytesPerSample == 3u.toUShort() && spec.bitsPerSample == 24u.toUShort() -> true
            specEx.bytesPerSample == 4u.toUShort() && spec.bitsPerSample == 32u.toUShort() -> true
            else -> false
        }

    if (!supported) {
        throw HoundError.Unsupported()
    }

    if (numSamples % spec.channels.toUInt() != 0u) {
        throw HoundError.FormatError("invalid data chunk length")
    }

    return Triple(specEx, dataLen, dataLenOffset)
}

/**
 * A writer that specifically only writes integer samples of 16 bits per sample.
 */
public class SampleWriter16 internal constructor(
    private val writer: HoundWriteSeek,
    private val buffer: ByteArray,
    private val bufferLen: Int,
    private val parent: WavWriter,
) {
    private var index: Int = 0

    /** Writes a single 8-bit integer sample into the 16-bit buffer. */
    public fun writeSampleI8(sample: Byte) {
        writeSampleI16(sample.toShort())
    }

    /** Writes a single 16-bit integer sample into the 16-bit buffer. */
    public fun writeSampleI16(sample: Short) {
        if (index + 2 > bufferLen) {
            error("Trying to write more samples than reserved for the sample writer.")
        }
        writeSampleUncheckedI16(sample)
    }

    /** Writes a single 32-bit integer sample (truncated to 16 bits) into the 16-bit buffer. */
    public fun writeSampleI32(sample: Int) {
        writeSampleI16(sample.toShort())
    }

    /** Writes a single 16-bit sample without bounds checking. */
    public fun writeSampleUncheckedI16(sample: Short) {
        val u = sample.toUShort().toUInt()
        buffer[index] = (u and 0xffu).toByte()
        buffer[index + 1] = ((u shr 8) and 0xffu).toByte()
        index += 2
    }

    /** Flushes the internal buffer to the underlying writer. */
    public fun flush() {
        if (index != bufferLen) {
            error("Insufficient samples written to the sample writer.")
        }
        writer.writeAll(buffer, 0, bufferLen)
        parent.addDataBytesWritten(bufferLen.toUInt())
    }
}

/**
 * A writer that accepts samples and writes the WAVE format.
 */
public class WavWriter internal constructor(
    private val wavSpec: WavSpec,
    private val bytesPerSample: UShort,
    private val writer: HoundWriteSeek,
    private var dataBytesWritten: UInt,
    private var sampleWriterBuffer: ByteArray,
    private var finalized: Boolean,
    private val dataLenOffset: UInt,
) {
    public companion object {
        /**
         * Creates a writer that writes the WAVE format to the underlying writer.
         */
        public fun new(writer: HoundWriteSeek, spec: WavSpec): WavWriter {
            val specEx =
                WavSpecEx(
                    spec = spec,
                    bytesPerSample = ((spec.bitsPerSample.toUInt() + 7u) / 8u).toUShort(),
                )
            return newWithSpecEx(writer, specEx)
        }

        /**
         * Creates a writer that writes the WAVE format using an extended specification.
         */
        public fun newWithSpecEx(writer: HoundWriteSeek, specEx: WavSpecEx): WavWriter {
            val spec = specEx.spec
            val fmtKind =
                if (spec.channels > 2u.toUShort() || spec.bitsPerSample > 16u.toUShort()) {
                    FmtKind.WaveFormatExtensible
                } else {
                    FmtKind.PcmWaveFormat
                }

            val wavWriter =
                WavWriter(
                    wavSpec = spec,
                    bytesPerSample = specEx.bytesPerSample,
                    writer = writer,
                    dataBytesWritten = 0u,
                    sampleWriterBuffer = ByteArray(0),
                    finalized = false,
                    dataLenOffset =
                        when (fmtKind) {
                            FmtKind.WaveFormatExtensible -> 64u
                            FmtKind.PcmWaveFormat -> 40u
                        },
                )

            val supported =
                when (spec.bitsPerSample.toInt()) {
                    8, 16, 24, 32 -> true
                    else -> false
                }
            if (!supported) {
                throw HoundError.Unsupported()
            }

            wavWriter.writeHeaders(fmtKind)
            return wavWriter
        }

        /**
         * Creates a writer that appends samples to an existing file stream.
         */
        public fun newAppend(writer: HoundReadWriteSeek): WavWriter {
            val (specEx, dataLen, dataLenOffset) = readAppend(writer)
            writer.seek(HoundSeekFrom.Current(dataLen.toLong()))
            return WavWriter(
                wavSpec = specEx.spec,
                bytesPerSample = specEx.bytesPerSample,
                writer = writer,
                dataBytesWritten = dataLen,
                sampleWriterBuffer = ByteArray(0),
                finalized = false,
                dataLenOffset = dataLenOffset,
            )
        }

        /**
         * Creates a writer that writes the WAVE format to a virtual file path.
         */
        public fun create(filename: String, spec: WavSpec): WavWriter =
            new(HoundVirtualFs.openCreate(filename), spec)

        /**
         * Creates a writer that appends samples to an existing virtual file path.
         */
        public fun append(filename: String): WavWriter {
            val cursor = HoundVirtualFs.openAppend(filename)
            cursor.seek(HoundSeekFrom.Start(0uL))
            return newAppend(cursor)
        }
    }

    private fun writeHeaders(fmtKind: FmtKind) {
        val buffer = HoundByteCursor()
        buffer.writeAll("RIFF".encodeToByteArray())
        buffer.writeLeU32(0u)
        buffer.writeAll("WAVE".encodeToByteArray())
        buffer.writeAll("fmt ".encodeToByteArray())

        when (fmtKind) {
            FmtKind.PcmWaveFormat -> writePcmWaveFormat(buffer)
            FmtKind.WaveFormatExtensible -> writeWaveFormatExtensible(buffer)
        }

        buffer.writeAll("data".encodeToByteArray())
        buffer.writeLeU32(0u)

        val headerLen = dataLenOffset.toInt() + 4
        val headerBytes = buffer.toByteArray()
        writer.writeAll(headerBytes, 0, headerLen)
    }

    private fun writeWaveFormat(buffer: HoundByteCursor) {
        buffer.writeLeU16(wavSpec.channels)
        buffer.writeLeU32(wavSpec.sampleRate)
        val bytesPerSec = wavSpec.sampleRate * bytesPerSample.toUInt() * wavSpec.channels.toUInt()
        buffer.writeLeU32(bytesPerSec)
        buffer.writeLeU16((bytesPerSec / wavSpec.sampleRate).toUShort())
    }

    private fun writePcmWaveFormat(buffer: HoundByteCursor) {
        buffer.writeLeU32(16u)
        when (wavSpec.sampleFormat) {
            SampleFormat.IntSample -> {
                buffer.writeLeU16(1u)
            }

            SampleFormat.Float -> {
                if (wavSpec.bitsPerSample == 32u.toUShort()) {
                    buffer.writeLeU16(3u)
                } else {
                    error(
                        "Invalid number of bits per sample. " +
                            "When writing SampleFormat::Float, bits_per_sample must be 32.",
                    )
                }
            }
        }
        writeWaveFormat(buffer)
        buffer.writeLeU16(wavSpec.bitsPerSample)
    }

    private fun writeWaveFormatExtensible(buffer: HoundByteCursor) {
        buffer.writeLeU32(40u)
        buffer.writeLeU16(0xfffeu)
        writeWaveFormat(buffer)
        buffer.writeLeU16((bytesPerSample.toUInt() * 8u).toUShort())
        buffer.writeLeU16(22u)
        buffer.writeLeU16(wavSpec.bitsPerSample)
        buffer.writeLeU32(channelMask(wavSpec.channels))

        val subformatGuid =
            when (wavSpec.sampleFormat) {
                SampleFormat.IntSample -> {
                    KSDATAFORMAT_SUBTYPE_PCM
                }

                SampleFormat.Float -> {
                    if (wavSpec.bitsPerSample == 32u.toUShort()) {
                        KSDATAFORMAT_SUBTYPE_IEEE_FLOAT
                    } else {
                        error(
                            "Invalid number of bits per sample. " +
                                "When writing SampleFormat::Float, bits_per_sample must be 32.",
                        )
                    }
                }
            }
        buffer.writeAll(subformatGuid)
    }

    internal fun addDataBytesWritten(bytes: UInt) {
        val sum = dataBytesWritten.toULong() + bytes.toULong()
        dataBytesWritten = if (sum > UInt.MAX_VALUE.toULong()) UInt.MAX_VALUE else sum.toUInt()
    }

    /** Writes a single 8-bit signed integer sample for one channel. */
    public fun writeSampleI8(sample: Byte) {
        writePaddedI8(sample, writer, wavSpec.bitsPerSample, bytesPerSample)
        addDataBytesWritten(bytesPerSample.toUInt())
    }

    /** Writes a single 16-bit signed integer sample for one channel. */
    public fun writeSampleI16(sample: Short) {
        writePaddedI16(sample, writer, wavSpec.bitsPerSample, bytesPerSample)
        addDataBytesWritten(bytesPerSample.toUInt())
    }

    /** Writes a single 32-bit signed integer sample for one channel. */
    public fun writeSampleI32(sample: Int) {
        writePaddedI32(sample, writer, wavSpec.bitsPerSample, bytesPerSample)
        addDataBytesWritten(bytesPerSample.toUInt())
    }

    /** Writes a single 32-bit floating-point sample for one channel. */
    public fun writeSampleF32(sample: Float) {
        writePaddedF32(sample, writer, wavSpec.bitsPerSample, bytesPerSample)
        addDataBytesWritten(bytesPerSample.toUInt())
    }

    /**
     * Create an efficient writer that writes 16-bit integer samples only.
     */
    public fun getI16Writer(numSamples: UInt): SampleWriter16 {
        if (wavSpec.sampleFormat != SampleFormat.IntSample) {
            error("When calling get_i16_writer, the sample format must be int.")
        }
        if (wavSpec.bitsPerSample != 16u.toUShort()) {
            error("When calling get_i16_writer, the number of bits per sample must be 16.")
        }

        val maxSamplesLeft = (UInt.MAX_VALUE - dataBytesWritten) / 2u
        if (numSamples > maxSamplesLeft) {
            error("Not enough space in data section, can have at most 2^31 i16 samples.")
        }
        val numBytes = numSamples.toInt() * 2
        if (sampleWriterBuffer.size < numBytes) {
            sampleWriterBuffer = ByteArray(numBytes)
        }
        return SampleWriter16(
            writer = writer,
            buffer = sampleWriterBuffer,
            bufferLen = numBytes,
            parent = this,
        )
    }

    private fun updateHeader() {
        val headerSize = dataLenOffset + 4u - 8u
        val fileSizeSum = dataBytesWritten.toULong() + headerSize.toULong()
        val fileSize = if (fileSizeSum > UInt.MAX_VALUE.toULong()) UInt.MAX_VALUE else fileSizeSum.toUInt()

        writer.seek(HoundSeekFrom.Start(4uL))
        writer.writeLeU32(fileSize)
        writer.seek(HoundSeekFrom.Start(dataLenOffset.toULong()))
        writer.writeLeU32(dataBytesWritten)

        if ((dataBytesWritten / bytesPerSample.toUInt()) % wavSpec.channels.toUInt() != 0u) {
            throw HoundError.UnfinishedSample()
        }
    }

    /**
     * Updates the WAVE header and flushes the underlying writer.
     */
    public fun flush() {
        val currentPos = writer.seek(HoundSeekFrom.Current(0L))
        updateHeader()
        writer.flush()
        writer.seek(HoundSeekFrom.Start(currentPos))
    }

    internal fun finalizeWriter() {
        if (finalized) return
        finalized = true
        updateHeader()
        writer.flush()
    }

    /** Returns information about the WAVE file being written. */
    public fun spec(): WavSpec = wavSpec

    /** Returns the duration of the file written so far, in samples. */
    public fun duration(): UInt =
        dataBytesWritten / (bytesPerSample.toUInt() * wavSpec.channels.toUInt())

    /** Returns the number of samples in the file written so far. */
    public fun len(): UInt = dataBytesWritten / bytesPerSample.toUInt()
}

/**
 * Updates the WAVE header (which requires knowing all samples) and flushes the underlying writer.
 */
public fun WavWriter.finalize() {
    finalizeWriter()
}
