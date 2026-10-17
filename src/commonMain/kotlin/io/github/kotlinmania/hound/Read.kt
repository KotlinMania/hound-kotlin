// port-lint: source src/read.rs
package io.github.kotlinmania.hound

import kotlin.math.min

/**
 * Reads as many bytes as [buf] is long.
 */
public fun HoundRead.readInto(buf: ByteArray) {
    var n = 0
    while (n < buf.size) {
        val progress = read(buf, n, buf.size - n)
        if (progress > 0) {
            n += progress
        } else {
            throw HoundError.IoError(HoundIoError("Failed to read enough bytes."))
        }
    }
}

/**
 * Skips over [n] bytes.
 */
public fun HoundRead.skipBytes(n: Int) {
    var nRead = 0
    val buf = ByteArray(1024)
    while (nRead < n) {
        val end = min(n - nRead, 1024)
        val progress = read(buf, 0, end)
        if (progress > 0) {
            nRead += progress
        } else {
            throw HoundError.IoError(HoundIoError("Failed to read enough bytes."))
        }
    }
}

/**
 * Reads 4 bytes and returns them in a [ByteArray].
 */
public fun HoundRead.read4Bytes(): ByteArray {
    val buf = ByteArray(4)
    readInto(buf)
    return buf
}

/**
 * Reads a single byte and interprets it as an 8-bit signed integer.
 */
public fun HoundRead.readI8(): Byte = readU8().toByte()

/**
 * Reads a single byte and interprets it as an 8-bit unsigned integer.
 */
public fun HoundRead.readU8(): UByte = readByte().toUByte()

/**
 * Reads two bytes and interprets them as a little-endian 16-bit signed integer.
 */
public fun HoundRead.readLeI16(): Short = readLeU16().toShort()

/**
 * Reads two bytes and interprets them as a little-endian 16-bit unsigned integer.
 */
public fun HoundRead.readLeU16(): UShort {
    val b0 = readByte().toUInt()
    val b1 = readByte().toUInt()
    return ((b1 shl 8) or b0).toUShort()
}

/**
 * Reads three bytes and interprets them as a little-endian 24-bit signed integer.
 * The sign bit will be extended into the most significant byte.
 */
public fun HoundRead.readLeI24(): Int {
    val x = readLeU24()
    return if ((x and (1u shl 23)) == 0u) {
        x.toInt()
    } else {
        (x or 0xff000000u).toInt()
    }
}

/**
 * Reads four bytes and interprets them as a little-endian 24-bit signed integer.
 * The sign bit will be extended into the most significant byte.
 */
public fun HoundRead.readLeI244(): Int {
    val x = readLeU32()
    return if ((x and (1u shl 23)) == 0u) {
        (x and 0x00ffffffu).toInt()
    } else {
        (x or 0xff000000u).toInt()
    }
}

/**
 * Reads three bytes and interprets them as a little-endian 24-bit unsigned integer.
 */
public fun HoundRead.readLeU24(): UInt {
    val b0 = readByte().toUInt()
    val b1 = readByte().toUInt()
    val b2 = readByte().toUInt()
    return (b2 shl 16) or (b1 shl 8) or b0
}

/**
 * Reads four bytes and interprets them as a little-endian 32-bit signed integer.
 */
public fun HoundRead.readLeI32(): Int = readLeU32().toInt()

/**
 * Reads four bytes and interprets them as a little-endian 32-bit unsigned integer.
 */
public fun HoundRead.readLeU32(): UInt {
    val b0 = readByte().toUInt()
    val b1 = readByte().toUInt()
    val b2 = readByte().toUInt()
    val b3 = readByte().toUInt()
    return (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
}

/**
 * Reads four bytes and interprets them as a little-endian 32-bit IEEE float.
 */
public fun HoundRead.readLeF32(): Float = Float.fromBits(readLeI32())

internal enum class ChunkKind {
    Fmt,
    Fact,
    Data,
    Unknown,
}

internal class ChunkHeader(
    val kind: ChunkKind,
    val len: UInt,
)

/**
 * Lower and optional upper bound for a WAVE sample iterator.
 */
public data class WavSizeHint(
    public val lower: Int,
    public val upper: Int?,
)

/**
 * Result of [readUntilData] containing the [WavSpecEx] and the data chunk byte length.
 */
public class WavDataHeader(
    public val specEx: WavSpecEx,
    public val dataLen: UInt,
)

/**
 * Reads the RIFF WAVE header, returns the supposed file size.
 */
public fun readWaveHeader(reader: HoundRead): ULong {
    val riff = reader.read4Bytes()
    if (!riff.contentEquals("RIFF".encodeToByteArray())) {
        throw HoundError.FormatError("no RIFF tag found")
    }
    val fileLen = reader.readLeU32()
    val wave = reader.read4Bytes()
    if (!wave.contentEquals("WAVE".encodeToByteArray())) {
        throw HoundError.FormatError("no WAVE tag found")
    }
    return fileLen.toULong() + 8uL
}

/**
 * Reads chunks until a data chunk is encountered.
 */
public fun readUntilData(reader: HoundRead): WavDataHeader {
    var specOpt: WavSpecEx? = null
    while (true) {
        val header = WavReader.readChunkHeader(reader)
        when (header.kind) {
            ChunkKind.Fmt -> {
                val spec = WavReader.readFmtChunk(reader, header.len)
                specOpt = spec
            }

            ChunkKind.Fact -> {
                // Consume the 4-byte samplesPerChannel field (ignoring EOF if truncated, matching upstream)
                try {
                    reader.readLeU32()
                } catch (_: HoundError) {
                    // Ignored per upstream readWaveHeader
                }
            }

            ChunkKind.Data -> {
                val spec = specOpt ?: throw HoundError.FormatError("missing fmt chunk")
                return WavDataHeader(spec, header.len)
            }

            ChunkKind.Unknown -> {
                reader.skipBytes(header.len.toInt())
            }
        }
    }
}

/**
 * Streaming iterator over 8-bit integer WAVE samples.
 */
public class WavSamplesI8 internal constructor(
    private val wavReader: WavReader,
) {
    public fun next(): Byte? = wavReader.nextSampleI8()

    public fun sizeHint(): WavSizeHint = wavReader.iterSizeHint()

    public fun len(): Int = wavReader.remainingSamples()

    public fun count(): Int {
        var c = 0
        while (next() != null) {
            c += 1
        }
        return c
    }

    public fun toList(): List<Byte> {
        val out = ArrayList<Byte>(len())
        while (true) {
            val s = next() ?: break
            out.add(s)
        }
        return out
    }
}

/**
 * Streaming iterator over 16-bit integer WAVE samples.
 */
public class WavSamplesI16 internal constructor(
    private val wavReader: WavReader,
) {
    public fun next(): Short? = wavReader.nextSampleI16()

    public fun sizeHint(): WavSizeHint = wavReader.iterSizeHint()

    public fun len(): Int = wavReader.remainingSamples()

    public fun count(): Int {
        var c = 0
        while (next() != null) {
            c += 1
        }
        return c
    }

    public fun toList(): List<Short> {
        val out = ArrayList<Short>(len())
        while (true) {
            val s = next() ?: break
            out.add(s)
        }
        return out
    }
}

/**
 * Streaming iterator over 32-bit integer WAVE samples.
 */
public class WavSamplesI32 internal constructor(
    private val wavReader: WavReader,
) {
    public fun next(): Int? = wavReader.nextSampleI32()

    public fun sizeHint(): WavSizeHint = wavReader.iterSizeHint()

    public fun len(): Int = wavReader.remainingSamples()

    public fun count(): Int {
        var c = 0
        while (next() != null) {
            c += 1
        }
        return c
    }

    public fun toList(): List<Int> {
        val out = ArrayList<Int>(len())
        while (true) {
            val s = next() ?: break
            out.add(s)
        }
        return out
    }
}

/**
 * Streaming iterator over 32-bit floating-point WAVE samples.
 */
public class WavSamplesF32 internal constructor(
    private val wavReader: WavReader,
) {
    public fun next(): Float? = wavReader.nextSampleF32()

    public fun sizeHint(): WavSizeHint = wavReader.iterSizeHint()

    public fun len(): Int = wavReader.remainingSamples()

    public fun count(): Int {
        var c = 0
        while (next() != null) {
            c += 1
        }
        return c
    }

    public fun toList(): List<Float> {
        val out = ArrayList<Float>(len())
        while (true) {
            val s = next() ?: break
            out.add(s)
        }
        return out
    }
}

/**
 * A reader that reads the WAVE format from the underlying reader.
 */
public class WavReader internal constructor(
    private val wavSpec: WavSpec,
    private val bytesPerSample: UShort,
    private val numSamples: UInt,
    internal var samplesRead: UInt,
    private val reader: HoundRead,
) {
    public companion object {
        private const val PCM: UShort = 0x0001u
        private const val ADPCM: UShort = 0x0002u
        private const val IEEE_FLOAT: UShort = 0x0003u
        private const val EXTENSIBLE: UShort = 0xfffeu

        internal fun readChunkHeader(reader: HoundRead): ChunkHeader {
            val kindStr = ByteArray(4)
            reader.readInto(kindStr)
            val len = reader.readLeU32()
            val kind =
                when {
                    kindStr.contentEquals("fmt ".encodeToByteArray()) -> ChunkKind.Fmt
                    kindStr.contentEquals("fact".encodeToByteArray()) -> ChunkKind.Fact
                    kindStr.contentEquals("data".encodeToByteArray()) -> ChunkKind.Data
                    else -> ChunkKind.Unknown
                }
            return ChunkHeader(kind, len)
        }

        internal fun readFmtChunk(reader: HoundRead, chunkLen: UInt): WavSpecEx {
            if (chunkLen < 16u) {
                throw HoundError.FormatError("invalid fmt chunk size")
            }
            val formatTag = reader.readLeU16()
            val nChannels = reader.readLeU16()
            val nSamplesPerSec = reader.readLeU32()
            val nBytesPerSec = reader.readLeU32()
            val blockAlign = reader.readLeU16()
            val bitsPerSample = reader.readLeU16()

            if (nChannels == 0u.toUShort()) {
                throw HoundError.FormatError("file contains zero channels")
            }

            val bytesPerSample = (blockAlign / nChannels).toUShort()
            val maxBits = bytesPerSample.toUInt() * 8u
            if (maxBits > UShort.MAX_VALUE.toUInt() || bitsPerSample.toUInt() > maxBits) {
                throw HoundError.FormatError("sample bits exceeds size of sample")
            }

            val expectedBytesPerSec = blockAlign.toULong() * nSamplesPerSec.toULong()
            if (expectedBytesPerSec > UInt.MAX_VALUE.toULong() ||
                nBytesPerSec.toULong() != expectedBytesPerSec
            ) {
                throw HoundError.FormatError("inconsistent fmt chunk")
            }

            if (bitsPerSample % 8u != 0u) {
                throw HoundError.FormatError("bits per sample is not a multiple of 8")
            }

            if (bitsPerSample == 0u.toUShort()) {
                throw HoundError.FormatError("bits per sample is 0")
            }

            var spec =
                WavSpec(
                    channels = nChannels,
                    sampleRate = nSamplesPerSec,
                    bitsPerSample = bitsPerSample,
                    sampleFormat = SampleFormat.IntSample,
                )

            when (formatTag) {
                PCM -> readWaveFormatPcm(reader, chunkLen, spec)
                ADPCM -> throw HoundError.Unsupported()
                IEEE_FLOAT -> spec = readWaveFormatIeeeFloat(reader, chunkLen, spec)
                EXTENSIBLE -> spec = readWaveFormatExtensible(reader, chunkLen, spec)
                else -> throw HoundError.Unsupported()
            }

            return WavSpecEx(spec = spec, bytesPerSample = bytesPerSample)
        }

        private fun readWaveFormatPcm(reader: HoundRead, chunkLen: UInt, spec: WavSpec) {
            val isWaveFormatEx =
                when (chunkLen) {
                    16u -> false
                    18u -> true
                    40u -> true
                    else -> throw HoundError.FormatError("unexpected fmt chunk size")
                }

            if (isWaveFormatEx) {
                reader.readLeU16()
                when (spec.bitsPerSample.toInt()) {
                    8, 16, 24 -> Unit
                    else -> throw HoundError.FormatError("bits per sample is not 8 or 16")
                }
            }

            if (chunkLen == 40u) {
                reader.skipBytes(22)
            }
        }

        private fun readWaveFormatIeeeFloat(
            reader: HoundRead,
            chunkLen: UInt,
            spec: WavSpec,
        ): WavSpec {
            val isWaveFormatEx = chunkLen == 18u
            if (!isWaveFormatEx && chunkLen != 16u) {
                throw HoundError.FormatError("unexpected fmt chunk size")
            }
            if (isWaveFormatEx) {
                val cbSize = reader.readLeU16()
                if (cbSize != 0u.toUShort()) {
                    throw HoundError.FormatError("unexpected WAVEFORMATEX size")
                }
            }
            if (spec.bitsPerSample != 32u.toUShort()) {
                throw HoundError.FormatError("bits per sample is not 32")
            }
            return spec.copy(sampleFormat = SampleFormat.Float)
        }

        private fun readWaveFormatExtensible(
            reader: HoundRead,
            chunkLen: UInt,
            spec: WavSpec,
        ): WavSpec {
            if (chunkLen < 40u) {
                throw HoundError.FormatError("unexpected fmt chunk size")
            }
            val cbSize = reader.readLeU16()
            if (cbSize != 22u.toUShort()) {
                throw HoundError.FormatError("unexpected WAVEFORMATEXTENSIBLE size")
            }
            val validBitsPerSample = reader.readLeU16()
            reader.readLeU32()
            val subformat = ByteArray(16)
            reader.readInto(subformat)

            val sampleFormat =
                when {
                    subformat.contentEquals(KSDATAFORMAT_SUBTYPE_PCM) -> SampleFormat.IntSample
                    subformat.contentEquals(KSDATAFORMAT_SUBTYPE_IEEE_FLOAT) -> SampleFormat.Float
                    else -> throw HoundError.Unsupported()
                }

            val updatedBits =
                if (validBitsPerSample > 0u.toUShort()) {
                    validBitsPerSample
                } else {
                    spec.bitsPerSample
                }

            return spec.copy(
                bitsPerSample = updatedBits,
                sampleFormat = sampleFormat,
            )
        }

        /**
         * Attempts to create a reader that reads the WAVE format.
         */
        public fun new(reader: HoundRead): WavReader {
            readWaveHeader(reader)
            val dataHeader = readUntilData(reader)
            val specEx = dataHeader.specEx
            val dataLen = dataHeader.dataLen

            val numSamples = dataLen / specEx.bytesPerSample.toUInt()
            if (numSamples * specEx.bytesPerSample.toUInt() != dataLen) {
                throw HoundError.FormatError("data chunk length is not a multiple of sample size")
            }
            if (numSamples % specEx.spec.channels.toUInt() != 0u) {
                throw HoundError.FormatError("invalid data chunk length")
            }

            return WavReader(
                wavSpec = specEx.spec,
                bytesPerSample = specEx.bytesPerSample,
                numSamples = numSamples,
                samplesRead = 0u,
                reader = reader,
            )
        }

        /**
         * Attempts to create a reader that reads from the specified virtual file path.
         */
        public fun open(filename: String): WavReader = new(HoundVirtualFs.openRead(filename))
    }

    /** Returns information about the WAVE file. */
    public fun spec(): WavSpec = wavSpec

    /** Returns a streaming iterator over 8-bit signed integer samples. */
    public fun samplesI8(): WavSamplesI8 = WavSamplesI8(this)

    /** Returns a streaming iterator over 16-bit signed integer samples. */
    public fun samplesI16(): WavSamplesI16 = WavSamplesI16(this)

    /** Returns a streaming iterator over 32-bit signed integer samples. */
    public fun samplesI32(): WavSamplesI32 = WavSamplesI32(this)

    /** Returns a streaming iterator over 32-bit floating-point samples. */
    public fun samplesF32(): WavSamplesF32 = WavSamplesF32(this)

    /** Returns a streaming iterator over 8-bit signed integer samples, consuming this reader. */
    public fun intoSamplesI8(): WavSamplesI8 = WavSamplesI8(this)

    /** Returns a streaming iterator over 16-bit signed integer samples, consuming this reader. */
    public fun intoSamplesI16(): WavSamplesI16 = WavSamplesI16(this)

    /** Returns a streaming iterator over 32-bit signed integer samples, consuming this reader. */
    public fun intoSamplesI32(): WavSamplesI32 = WavSamplesI32(this)

    /** Returns a streaming iterator over 32-bit floating-point samples, consuming this reader. */
    public fun intoSamplesF32(): WavSamplesF32 = WavSamplesF32(this)

    /** Returns the duration of the file in samples. */
    public fun duration(): UInt = numSamples / wavSpec.channels.toUInt()

    /** Returns the number of values that the sample iterator will yield. */
    public fun len(): UInt = numSamples

    /** Destroys the [WavReader] and returns the underlying reader. */
    public fun intoInner(): HoundRead = reader

    /**
     * Seek to the given time within the file (in samples per channel).
     */
    public fun seek(time: UInt) {
        val seekable =
            reader as? HoundSeek
                ?: throw HoundError.IoError(HoundIoError("Underlying reader does not support seeking."))
        val bps = wavSpec.bitsPerSample.toLong() / 8L
        val samplePosition = time.toULong() * wavSpec.channels.toULong()
        val offsetSamples = samplePosition.toLong() - samplesRead.toLong()
        val offsetBytes = offsetSamples * bps
        seekable.seek(HoundSeekFrom.Current(offsetBytes))
        samplesRead = samplePosition.toUInt()
    }

    internal fun remainingSamples(): Int =
        if (samplesRead < numSamples) (numSamples - samplesRead).toInt() else 0

    internal fun iterSizeHint(): WavSizeHint {
        val rem = remainingSamples()
        return WavSizeHint(rem, rem)
    }

    internal fun nextSampleI8(): Byte? {
        if (samplesRead < numSamples) {
            samplesRead += 1u
            return readSampleI8(reader, wavSpec.sampleFormat, bytesPerSample, wavSpec.bitsPerSample)
        }
        return null
    }

    internal fun nextSampleI16(): Short? {
        if (samplesRead < numSamples) {
            samplesRead += 1u
            return readSampleI16(reader, wavSpec.sampleFormat, bytesPerSample, wavSpec.bitsPerSample)
        }
        return null
    }

    internal fun nextSampleI32(): Int? {
        if (samplesRead < numSamples) {
            samplesRead += 1u
            return readSampleI32(reader, wavSpec.sampleFormat, bytesPerSample, wavSpec.bitsPerSample)
        }
        return null
    }

    internal fun nextSampleF32(): Float? {
        if (samplesRead < numSamples) {
            samplesRead += 1u
            return readSampleF32(reader, wavSpec.sampleFormat, bytesPerSample, wavSpec.bitsPerSample)
        }
        return null
    }
}
