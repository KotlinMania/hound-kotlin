package io.github.kotlinmania.hound

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HoundTest {
    @Test
    fun u8SignConversionIsBijective() {
        for (x in 0 until 255) {
            val u = x.toUByte()
            assertEquals(u, u8FromSigned(signedFromU8(u)))
        }
        for (x in -128 until 127) {
            val s = x.toByte()
            assertEquals(s, signedFromU8(u8FromSigned(s)))
        }
    }

    @Test
    fun verifyNarrowToI8() {
        assertEquals(127.toByte(), narrowToI8(127))
        assertFailsWith<HoundError.TooWide> { narrowToI8(128) }
        assertEquals((-128).toByte(), narrowToI8(-128))
        assertFailsWith<HoundError.TooWide> { narrowToI8(-129) }
    }

    @Test
    fun verifyNarrowToI16() {
        assertEquals(32767.toShort(), narrowToI16(32767))
        assertFailsWith<HoundError.TooWide> { narrowToI16(32768) }
        assertEquals((-32768).toShort(), narrowToI16(-32768))
        assertFailsWith<HoundError.TooWide> { narrowToI16(-32769) }
    }

    @Test
    fun verifyNarrowToI24() {
        assertEquals(8_388_607, narrowToI24(8_388_607))
        assertFailsWith<HoundError.TooWide> { narrowToI24(8_388_608) }
        assertEquals(-8_388_608, narrowToI24(-8_388_608))
        assertFailsWith<HoundError.TooWide> { narrowToI24(-8_388_609) }
    }

    @Test
    fun writeReadI16IsLossless() {
        val buffer = HoundByteCursor()
        val writeSpec =
            WavSpec(
                channels = 2u,
                sampleRate = 44100u,
                bitsPerSample = 16u,
                sampleFormat = SampleFormat.IntSample,
            )

        run {
            val writer = WavWriter.new(buffer, writeSpec)
            for (s in -1024 until 1024) {
                writer.writeSampleI16(s.toShort())
            }
            writer.finalize()
        }

        run {
            buffer.setPosition(0)
            val reader = WavReader.new(buffer)
            assertEquals(writeSpec, reader.spec())
            assertEquals(2048u, reader.len())
            val iter = reader.samplesI16()
            for (expected in -1024 until 1024) {
                assertEquals(expected.toShort(), iter.next())
            }
        }
    }

    @Test
    fun writeReadI16ViaSampleWriterIsLossless() {
        val buffer = HoundByteCursor()
        val writeSpec =
            WavSpec(
                channels = 2u,
                sampleRate = 44100u,
                bitsPerSample = 16u,
                sampleFormat = SampleFormat.IntSample,
            )

        run {
            val writer = WavWriter.new(buffer, writeSpec)
            run {
                val sampleWriter = writer.getI16Writer(1024u)
                for (s in -1024 until 0) {
                    sampleWriter.writeSampleI16(s.toShort())
                }
                sampleWriter.flush()
            }
            run {
                val sampleWriter = writer.getI16Writer(1024u)
                for (s in 0 until 1024) {
                    sampleWriter.writeSampleUncheckedI16(s.toShort())
                }
                sampleWriter.flush()
            }
            writer.finalize()
        }

        run {
            buffer.setPosition(0)
            val reader = WavReader.new(buffer)
            assertEquals(writeSpec, reader.spec())
            assertEquals(2048u, reader.len())
            val iter = reader.samplesI16()
            for (expected in -1024 until 1024) {
                assertEquals(expected.toShort(), iter.next())
            }
        }
    }

    @Test
    fun writeReadI8IsLossless() {
        val buffer = HoundByteCursor()
        val writeSpec =
            WavSpec(
                channels = 16u,
                sampleRate = 48000u,
                bitsPerSample = 8u,
                sampleFormat = SampleFormat.IntSample,
            )

        run {
            val writer = WavWriter.new(buffer, writeSpec)
            for (s in -128..127) {
                writer.writeSampleI8(s.toByte())
            }
            writer.finalize()
        }

        run {
            buffer.setPosition(0)
            val reader = WavReader.new(buffer)
            assertEquals(writeSpec, reader.spec())
            assertEquals(256u, reader.len())
            val iter = reader.samplesI16()
            for (expected in -128..127) {
                assertEquals(expected.toShort(), iter.next())
            }
        }
    }

    @Test
    fun writeReadI24IsLossless() {
        val buffer = HoundByteCursor()
        val writeSpec =
            WavSpec(
                channels = 16u,
                sampleRate = 96000u,
                bitsPerSample = 24u,
                sampleFormat = SampleFormat.IntSample,
            )

        run {
            val writer = WavWriter.new(buffer, writeSpec)
            for (s in -128..127) {
                writer.writeSampleI32(s * 256 * 256)
            }
            writer.finalize()
        }

        run {
            buffer.setPosition(0)
            val reader = WavReader.new(buffer)
            assertEquals(writeSpec, reader.spec())
            assertEquals(256u, reader.len())
            val iter = reader.samplesI32()
            for (s in -128..127) {
                assertEquals(s * 256 * 256, iter.next())
            }
        }
    }

    @Test
    fun writeReadF32IsLossless() {
        val buffer = HoundByteCursor()
        val writeSpec =
            WavSpec(
                channels = 2u,
                sampleRate = 44100u,
                bitsPerSample = 32u,
                sampleFormat = SampleFormat.Float,
            )

        run {
            val writer = WavWriter.new(buffer, writeSpec)
            for (s in 1..256) {
                writer.writeSampleF32(1.0f / s.toFloat())
            }
            writer.finalize()
        }

        run {
            buffer.setPosition(0)
            val reader = WavReader.new(buffer)
            assertEquals(writeSpec, reader.spec())
            assertEquals(256u, reader.len())
            val iter = reader.samplesF32()
            for (s in 1..256) {
                val expected = Float.fromBits((1.0f / s.toFloat()).toRawBits())
                assertEquals(expected, iter.next())
            }
        }
    }

    @Test
    fun no32BpsForFloatSampleFormatPanics() {
        val buffer = HoundByteCursor()
        val writeSpec =
            WavSpec(
                channels = 2u,
                sampleRate = 44100u,
                bitsPerSample = 16u,
                sampleFormat = SampleFormat.Float,
            )
        assertFailsWith<IllegalStateException> {
            WavWriter.new(buffer, writeSpec)
        }
    }

    @Test
    fun flushShouldProduceValidFile() {
        val buffer = HoundByteCursor()
        val samples = shortArrayOf(2, 4, 5, 7, 11, 13)

        run {
            val spec =
                WavSpec(
                    channels = 2u,
                    sampleRate = 44100u,
                    bitsPerSample = 16u,
                    sampleFormat = SampleFormat.IntSample,
                )
            val writer = WavWriter.new(buffer, spec)
            for (x in samples) {
                writer.writeSampleI16(x)
            }
            writer.flush()
            writer.writeSampleI16(17)
            writer.writeSampleI16(19)
            // Do not call finalize() to verify flush checkpointing
        }

        buffer.seek(HoundSeekFrom.Start(0uL))
        val reader = WavReader.new(buffer)
        val readSamples = reader.samplesI16().toList().toShortArray()
        assertContentEquals(samples, readSamples)
    }

    @Test
    fun newAppendShouldAppend() {
        val buffer = HoundByteCursor()
        val samples = shortArrayOf(2, 5, 7, 11)
        val spec =
            WavSpec(
                channels = 2u,
                sampleRate = 44100u,
                bitsPerSample = 16u,
                sampleFormat = SampleFormat.IntSample,
            )

        run {
            val writer = WavWriter.new(buffer, spec)
            for (s in samples) {
                writer.writeSampleI16(s)
            }
            writer.finalize()
        }

        buffer.seek(HoundSeekFrom.Start(0uL))

        run {
            val writer = WavWriter.newAppend(buffer)
            assertEquals(spec, writer.spec())
            for (s in samples) {
                writer.writeSampleI16(s)
            }
            writer.finalize()
        }

        buffer.seek(HoundSeekFrom.Start(0uL))
        val reader = WavReader.new(buffer)
        val readSamples = reader.samplesI16().toList().toShortArray()
        assertContentEquals(shortArrayOf(2, 5, 7, 11, 2, 5, 7, 11), readSamples)
    }

    @Test
    fun newAppendDoesNotCorruptFiles() {
        TestSamples.ensureLoaded()
        val sampleFiles =
            listOf(
                "testsamples/pcmwaveformat-16bit-44100Hz-mono-extra.wav",
                "testsamples/pcmwaveformat-16bit-44100Hz-mono.wav",
                "testsamples/pcmwaveformat-8bit-44100Hz-mono.wav",
                "testsamples/pop.wav",
                "testsamples/waveformatex-16bit-44100Hz-mono-extra.wav",
                "testsamples/waveformatex-16bit-44100Hz-mono.wav",
                "testsamples/waveformatex-16bit-44100Hz-stereo.wav",
                "testsamples/waveformatextensible-24bit-192kHz-mono.wav",
                "testsamples/waveformatextensible-32bit-48kHz-stereo.wav",
                "testsamples/nonstandard-01.wav",
                "testsamples/nonstandard-02.wav",
                "testsamples/waveformatex-8bit-11025Hz-mono.wav",
            )

        for (fname in sampleFiles) {
            var buffer = HoundVirtualFs.readFile(fname)

            val samplesOrig: List<Int>
            val cursor1 = HoundByteCursor(buffer)
            run {
                val reader = WavReader.new(cursor1)
                samplesOrig = reader.samplesI32().toList()
            }
            buffer = cursor1.intoInner()

            val cursor2 = HoundByteCursor(buffer)
            run {
                val writer = WavWriter.newAppend(cursor2)
                writer.writeSampleI8(41)
                writer.writeSampleI8(43)
                writer.finalize()
            }
            buffer = cursor2.intoInner()

            val cursor3 = HoundByteCursor(buffer)
            val reader = WavReader.new(cursor3)
            val samplesAfter = reader.samplesI32().toList()

            assertEquals(samplesOrig, samplesAfter.subList(0, samplesOrig.size))
            assertEquals(41, samplesAfter[samplesAfter.size - 2])
            assertEquals(43, samplesAfter[samplesAfter.size - 1])
        }
    }

    private fun assertContents(fname: String, expected: ShortArray) {
        val reader = WavReader.open(fname)
        val samples = reader.samplesI16().toList().toShortArray()
        assertContentEquals(expected, samples)
    }

    @Test
    fun appendWorksOnFiles() {
        val spec =
            WavSpec(
                channels = 1u,
                sampleRate = 44100u,
                bitsPerSample = 16u,
                sampleFormat = SampleFormat.IntSample,
            )

        val writer = WavWriter.create("append.wav", spec)
        writer.writeSampleI16(11)
        writer.writeSampleI16(13)
        writer.writeSampleI16(17)
        writer.finalize()

        assertContents("append.wav", shortArrayOf(11, 13, 17))

        val len = HoundVirtualFs.fileLength("append.wav")

        val appender = WavWriter.append("append.wav")
        appender.writeSampleI16(19)
        appender.writeSampleI16(23)
        appender.finalize()

        assertEquals(len + 4uL, HoundVirtualFs.fileLength("append.wav"))
        assertContents("append.wav", shortArrayOf(11, 13, 17, 19, 23))
    }

    @Test
    fun testIntoHeaderForInfiniteFile() {
        val spec1 =
            WavSpec(
                bitsPerSample = 16u,
                channels = 1u,
                sampleFormat = SampleFormat.IntSample,
                sampleRate = 16000u,
            )
        val v1 = spec1.intoHeaderForInfiniteFile()
        val expected1 =
            byteArrayOf(
                'R'.code.toByte(),
                'I'.code.toByte(),
                'F'.code.toByte(),
                'F'.code.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                'W'.code.toByte(),
                'A'.code.toByte(),
                'V'.code.toByte(),
                'E'.code.toByte(),
                'f'.code.toByte(),
                'm'.code.toByte(),
                't'.code.toByte(),
                ' '.code.toByte(),
                0x10,
                0x00,
                0x00,
                0x00,
                0x01,
                0x00,
                0x01,
                0x00,
                0x80.toByte(),
                0x3e,
                0x00,
                0x00,
                0x00,
                0x7d,
                0x00,
                0x00,
                0x02,
                0x00,
                0x10,
                0x00,
                'd'.code.toByte(),
                'a'.code.toByte(),
                't'.code.toByte(),
                'a'.code.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
            )
        assertContentEquals(expected1, v1)

        val spec2 =
            WavSpec(
                bitsPerSample = 16u,
                channels = 10u,
                sampleFormat = SampleFormat.IntSample,
                sampleRate = 16000u,
            )
        val v2 = spec2.intoHeaderForInfiniteFile()
        val expected2 =
            byteArrayOf(
                'R'.code.toByte(),
                'I'.code.toByte(),
                'F'.code.toByte(),
                'F'.code.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                'W'.code.toByte(),
                'A'.code.toByte(),
                'V'.code.toByte(),
                'E'.code.toByte(),
                'f'.code.toByte(),
                'm'.code.toByte(),
                't'.code.toByte(),
                ' '.code.toByte(),
                0x28,
                0x00,
                0x00,
                0x00,
                0xfe.toByte(),
                0xff.toByte(),
                0x0a,
                0x00,
                0x80.toByte(),
                0x3e,
                0x00,
                0x00,
                0x00,
                0xe2.toByte(),
                0x04,
                0x00,
                0x14,
                0x00,
                0x10,
                0x00,
                0x16,
                0x00,
                0x10,
                0x00,
                0xff.toByte(),
                0x03,
                0x00,
                0x00,
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
                'd'.code.toByte(),
                'a'.code.toByte(),
                't'.code.toByte(),
                'a'.code.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
                0xFF.toByte(),
            )
        assertContentEquals(expected2, v2)
    }
}
