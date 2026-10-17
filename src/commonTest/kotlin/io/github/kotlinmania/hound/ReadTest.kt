// port-lint: tests src/read.rs
package io.github.kotlinmania.hound

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReadTest {
    @Test
    fun durationAndLenAgree() {
        TestSamples.ensureLoaded()
        val files =
            listOf(
                "testsamples/pcmwaveformat-16bit-44100Hz-mono.wav",
                "testsamples/waveformatex-16bit-44100Hz-stereo.wav",
                "testsamples/waveformatextensible-32bit-48kHz-stereo.wav",
            )
        for (fname in files) {
            val reader = WavReader.open(fname)
            assertEquals(reader.spec().channels.toUInt() * reader.duration(), reader.len())
        }
    }

    @Test
    fun readWavPcmWaveFormatPcm() {
        TestSamples.ensureLoaded()
        val wavReader = WavReader.open("testsamples/pcmwaveformat-16bit-44100Hz-mono.wav")
        assertEquals(1u.toUShort(), wavReader.spec().channels)
        assertEquals(44100u, wavReader.spec().sampleRate)
        assertEquals(16u.toUShort(), wavReader.spec().bitsPerSample)
        assertEquals(SampleFormat.IntSample, wavReader.spec().sampleFormat)

        val samples = wavReader.samplesI16().toList().toShortArray()
        assertContentEquals(shortArrayOf(2, -3, 5, -7), samples)
    }

    @Test
    fun readWavSkipsUnknownChunks() {
        TestSamples.ensureLoaded()
        val files =
            listOf(
                "testsamples/pcmwaveformat-16bit-44100Hz-mono-extra.wav",
                "testsamples/waveformatex-16bit-44100Hz-mono-extra.wav",
            )
        for (file in files) {
            val wavReader = WavReader.open(file)
            assertEquals(1u.toUShort(), wavReader.spec().channels)
            assertEquals(44100u, wavReader.spec().sampleRate)
            assertEquals(16u.toUShort(), wavReader.spec().bitsPerSample)
            assertEquals(SampleFormat.IntSample, wavReader.spec().sampleFormat)

            val sample = wavReader.samplesI16().next()
            assertEquals(2.toShort(), sample)
        }
    }

    @Test
    fun readWav0ValidBitsFallback() {
        TestSamples.ensureLoaded()
        val wavReader = WavReader.open("testsamples/nonstandard-02.wav")
        assertEquals(2u.toUShort(), wavReader.spec().channels)
        assertEquals(48000u, wavReader.spec().sampleRate)
        assertEquals(32u.toUShort(), wavReader.spec().bitsPerSample)
        assertEquals(SampleFormat.IntSample, wavReader.spec().sampleFormat)

        val samples = wavReader.samplesI32().toList().toIntArray()
        assertContentEquals(intArrayOf(19, -229373, 33587161, -2147483497), samples)
    }

    @Test
    fun lenAndSizeHintAreCorrect() {
        TestSamples.ensureLoaded()
        val wavReader = WavReader.open("testsamples/pcmwaveformat-16bit-44100Hz-mono.wav")
        assertEquals(4u, wavReader.len())

        run {
            val samples = wavReader.samplesI16()
            assertEquals(WavSizeHint(4, 4), samples.sizeHint())
            samples.next()
            assertEquals(WavSizeHint(3, 3), samples.sizeHint())
        }

        assertEquals(4u, wavReader.len())

        run {
            val samples = wavReader.samplesI16()
            assertEquals(WavSizeHint(3, 3), samples.sizeHint())
            samples.next()
            assertEquals(WavSizeHint(2, 2), samples.sizeHint())
        }
    }

    @Test
    fun sizeHintIsExact() {
        TestSamples.ensureLoaded()
        val files =
            listOf(
                "testsamples/pcmwaveformat-16bit-44100Hz-mono.wav",
                "testsamples/waveformatex-16bit-44100Hz-stereo.wav",
                "testsamples/waveformatextensible-32bit-48kHz-stereo.wav",
            )
        for (fname in files) {
            val reader = WavReader.open(fname)
            val len = reader.len().toInt()
            val iter = reader.samplesI32()
            for (i in 0 until len) {
                val remaining = len - i
                assertEquals(WavSizeHint(remaining, remaining), iter.sizeHint())
                assertNotNull(iter.next())
            }
            assertNull(iter.next())
        }
    }

    @Test
    fun samplesEqualsIntoSamples() {
        TestSamples.ensureLoaded()
        val wavReaderVal = WavReader.open("testsamples/pcmwaveformat-8bit-44100Hz-mono.wav")
        val wavReaderRef = WavReader.open("testsamples/pcmwaveformat-8bit-44100Hz-mono.wav")

        val samplesVal = wavReaderVal.intoSamplesI16().toList()
        val samplesRef = wavReaderRef.samplesI16().toList()
        assertEquals(samplesVal, samplesRef)
    }

    @Test
    fun readWavWaveFormatExPcm() {
        TestSamples.ensureLoaded()
        val wavReader = WavReader.open("testsamples/waveformatex-16bit-44100Hz-mono.wav")
        assertEquals(1u.toUShort(), wavReader.spec().channels)
        assertEquals(44100u, wavReader.spec().sampleRate)
        assertEquals(16u.toUShort(), wavReader.spec().bitsPerSample)
        assertEquals(SampleFormat.IntSample, wavReader.spec().sampleFormat)

        val samples = wavReader.samplesI16().toList().toShortArray()
        assertContentEquals(shortArrayOf(2, -3, 5, -7), samples)
    }

    @Test
    fun readWavWaveFormatExIeeeFloat() {
        TestSamples.ensureLoaded()
        val wavReader = WavReader.open("testsamples/waveformatex-ieeefloat-44100Hz-mono.wav")
        assertEquals(1u.toUShort(), wavReader.spec().channels)
        assertEquals(44100u, wavReader.spec().sampleRate)
        assertEquals(32u.toUShort(), wavReader.spec().bitsPerSample)
        assertEquals(SampleFormat.Float, wavReader.spec().sampleFormat)

        val samples = wavReader.samplesF32().toList().toFloatArray()
        assertContentEquals(floatArrayOf(2.0f, 3.0f, -16411.0f, 1019.0f), samples)
    }

    @Test
    fun readWavStereo() {
        TestSamples.ensureLoaded()
        val wavReader = WavReader.open("testsamples/waveformatex-16bit-44100Hz-stereo.wav")
        assertEquals(2u.toUShort(), wavReader.spec().channels)
        assertEquals(44100u, wavReader.spec().sampleRate)
        assertEquals(16u.toUShort(), wavReader.spec().bitsPerSample)
        assertEquals(SampleFormat.IntSample, wavReader.spec().sampleFormat)

        val samples = wavReader.samplesI16().toList().toShortArray()
        assertContentEquals(shortArrayOf(2, -3, 5, -7, 11, -13, 17, -19), samples)
    }

    @Test
    fun readWavPcmWaveFormat8bit() {
        TestSamples.ensureLoaded()
        val wavReader = WavReader.open("testsamples/pcmwaveformat-8bit-44100Hz-mono.wav")
        assertEquals(1u.toUShort(), wavReader.spec().channels)
        assertEquals(8u.toUShort(), wavReader.spec().bitsPerSample)
        assertEquals(SampleFormat.IntSample, wavReader.spec().sampleFormat)

        val samples = wavReader.samplesI16().toList().toShortArray()
        assertContentEquals(shortArrayOf(19, -53, 89, -127), samples)
    }

    @Test
    fun readWavPcmWaveFormat24bit4byte() {
        TestSamples.ensureLoaded()
        val wavReader = WavReader.open("testsamples/pcmwaveformat-24bit-4byte-48kHz-stereo.wav")
        assertEquals(2u.toUShort(), wavReader.spec().channels)
        assertEquals(48_000u, wavReader.spec().sampleRate)
        assertEquals(24u.toUShort(), wavReader.spec().bitsPerSample)
        assertEquals(SampleFormat.IntSample, wavReader.spec().sampleFormat)

        val samples = wavReader.samplesI32().toList().toIntArray()
        assertContentEquals(intArrayOf(-96, 23_052, 8_388_607, -8_360_672), samples)
    }

    @Test
    fun readWavWaveFormatEx8bit() {
        TestSamples.ensureLoaded()
        val wavReader = WavReader.open("testsamples/waveformatex-8bit-11025Hz-mono.wav")
        assertEquals(1u.toUShort(), wavReader.spec().channels)
        assertEquals(8u.toUShort(), wavReader.spec().bitsPerSample)
        assertEquals(SampleFormat.IntSample, wavReader.spec().sampleFormat)

        val samples = wavReader.samplesI32().toList().toIntArray()
        assertContentEquals(intArrayOf(-128, -128, -128, -128), samples)
    }

    @Test
    fun readWavWaveFormatExtensiblePcm24bit() {
        TestSamples.ensureLoaded()
        val wavReader = WavReader.open("testsamples/waveformatextensible-24bit-192kHz-mono.wav")
        assertEquals(1u.toUShort(), wavReader.spec().channels)
        assertEquals(192_000u, wavReader.spec().sampleRate)
        assertEquals(24u.toUShort(), wavReader.spec().bitsPerSample)
        assertEquals(SampleFormat.IntSample, wavReader.spec().sampleFormat)

        val samples = wavReader.samplesI32().toList().toIntArray()
        assertContentEquals(intArrayOf(-17, 4_194_319, -6_291_437, 8_355_817), samples)
    }

    @Test
    fun readWavWaveFormatExtensiblePcm24bit4byte() {
        TestSamples.ensureLoaded()
        val wavReader = WavReader.open("testsamples/waveformatextensible-24bit-4byte-48kHz-stereo.wav")
        assertEquals(2u.toUShort(), wavReader.spec().channels)
        assertEquals(48_000u, wavReader.spec().sampleRate)
        assertEquals(24u.toUShort(), wavReader.spec().bitsPerSample)
        assertEquals(SampleFormat.IntSample, wavReader.spec().sampleFormat)

        val samples = wavReader.samplesI32().toList().toIntArray()
        assertContentEquals(intArrayOf(-96, 23_052, 8_388_607, -8_360_672), samples)
    }

    @Test
    fun readWav32bit() {
        TestSamples.ensureLoaded()
        val wavReader = WavReader.open("testsamples/waveformatextensible-32bit-48kHz-stereo.wav")
        assertEquals(32u.toUShort(), wavReader.spec().bitsPerSample)
        assertEquals(SampleFormat.IntSample, wavReader.spec().sampleFormat)

        val samples = wavReader.samplesI32().toList().toIntArray()
        assertContentEquals(intArrayOf(19, -229_373, 33_587_161, -2_147_483_497), samples)
    }

    @Test
    fun readWavWaveFormatExtensibleIeeeFloat() {
        TestSamples.ensureLoaded()
        val wavReader = WavReader.open("testsamples/waveformatextensible-ieeefloat-44100Hz-mono.wav")
        assertEquals(1u.toUShort(), wavReader.spec().channels)
        assertEquals(44100u, wavReader.spec().sampleRate)
        assertEquals(32u.toUShort(), wavReader.spec().bitsPerSample)
        assertEquals(SampleFormat.Float, wavReader.spec().sampleFormat)

        val samples = wavReader.samplesF32().toList().toFloatArray()
        assertContentEquals(floatArrayOf(2.0f, 3.0f, -16411.0f, 1019.0f), samples)
    }

    @Test
    fun readWavNonstandard01() {
        TestSamples.ensureLoaded()
        val wavReader = WavReader.open("testsamples/nonstandard-01.wav")
        assertEquals(24u.toUShort(), wavReader.spec().bitsPerSample)
        assertEquals(SampleFormat.IntSample, wavReader.spec().sampleFormat)

        val samples = wavReader.samplesI32().toList().toIntArray()
        assertContentEquals(intArrayOf(0, 0), samples)
    }

    @Test
    fun wideReadShouldSignalError() {
        TestSamples.ensureLoaded()
        val reader24 = WavReader.open("testsamples/waveformatextensible-24bit-192kHz-mono.wav")
        assertFailsWith<HoundError.TooWide> { reader24.samplesI8().next() }
        assertFailsWith<HoundError.TooWide> { reader24.samplesI16().next() }
        assertNotNull(reader24.samplesI32().next())

        val reader32 = WavReader.open("testsamples/waveformatextensible-32bit-48kHz-stereo.wav")
        assertFailsWith<HoundError.TooWide> { reader32.samplesI8().next() }
        assertFailsWith<HoundError.TooWide> { reader32.samplesI16().next() }
        assertNotNull(reader32.samplesI32().next())
    }

    @Test
    fun sampleFormatMismatchShouldSignalError() {
        TestSamples.ensureLoaded()
        val readerF32 = WavReader.open("testsamples/waveformatex-ieeefloat-44100Hz-mono.wav")
        assertFailsWith<HoundError.InvalidSampleFormat> { readerF32.samplesI8().next() }
        assertFailsWith<HoundError.InvalidSampleFormat> { readerF32.samplesI16().next() }
        assertFailsWith<HoundError.InvalidSampleFormat> { readerF32.samplesI32().next() }
        assertNotNull(readerF32.samplesF32().next())

        val readerI8 = WavReader.open("testsamples/pcmwaveformat-8bit-44100Hz-mono.wav")
        assertNotNull(readerI8.samplesI8().next())
        assertNotNull(readerI8.samplesI16().next())
        assertNotNull(readerI8.samplesI32().next())
        assertFailsWith<HoundError.InvalidSampleFormat> { readerI8.samplesF32().next() }
    }

    @Test
    fun fuzzCrashesShouldBeFixed() {
        TestSamples.ensureLoaded()
        val fuzzPaths = HoundVirtualFs.listFiles("testsamples/fuzz/")
        for (path in fuzzPaths) {
            val reader =
                try {
                    WavReader.open(path)
                } catch (_: HoundError) {
                    continue
                }
            when (reader.spec().sampleFormat) {
                SampleFormat.IntSample -> {
                    val iter = reader.samplesI32()
                    while (true) {
                        val s =
                            try {
                                iter.next()
                            } catch (_: HoundError) {
                                break
                            }
                        if (s == null) break
                    }
                }

                SampleFormat.Float -> {
                    val iter = reader.samplesF32()
                    while (true) {
                        val s =
                            try {
                                iter.next()
                            } catch (_: HoundError) {
                                break
                            }
                        if (s == null) break
                    }
                }
            }
        }
    }

    @Test
    fun seekIsConsistent() {
        TestSamples.ensureLoaded()
        val files =
            listOf(
                "testsamples/pcmwaveformat-16bit-44100Hz-mono.wav",
                "testsamples/waveformatex-16bit-44100Hz-stereo.wav",
                "testsamples/waveformatextensible-32bit-48kHz-stereo.wav",
            )
        for (fname in files) {
            val reader = WavReader.open(fname)
            val count = reader.samplesI32().count()
            reader.seek(0u)
            assertEquals(0u, reader.samplesRead)
            assertEquals(count, reader.samplesI32().count())

            val lastTime = reader.duration() - 1u
            val channels = reader.spec().channels.toInt()
            reader.seek(lastTime)
            run {
                val samples = reader.samplesI32()
                repeat(channels) {
                    assertNotNull(samples.next())
                }
                assertNull(samples.next())
            }

            val numSamples = reader.len()
            reader.seek(numSamples)
            assertNull(reader.samplesI32().next())
            reader.seek(UInt.MAX_VALUE / channels.toUInt())
            assertNull(reader.samplesI32().next())
        }
    }
}
