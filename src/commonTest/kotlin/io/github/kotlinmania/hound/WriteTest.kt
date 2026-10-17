// port-lint: tests src/write.rs
package io.github.kotlinmania.hound

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WriteTest {
    @Test
    fun verifyChannelMask() {
        assertEquals(0u, channelMask(0u))
        assertEquals(1u, channelMask(1u))
        assertEquals(3u, channelMask(2u))
        assertEquals(7u, channelMask(3u))
        assertEquals(0xFu, channelMask(4u))
        assertEquals(0xFFu, channelMask(8u))
        assertEquals(0xFFFFu, channelMask(16u))
        assertEquals(0x3FFFFu, channelMask(18u))
        assertEquals(0x3FFFFu, channelMask(32u))
        assertEquals(0x3FFFFu, channelMask(64u))
        assertEquals(0x3FFFFu, channelMask(129u))
    }

    @Test
    fun shortWriteShouldSignalError() {
        val buffer = HoundByteCursor()
        val writeSpec =
            WavSpec(
                channels = 17u,
                sampleRate = 48000u,
                bitsPerSample = 8u,
                sampleFormat = SampleFormat.IntSample,
            )

        val writer = WavWriter.new(buffer, writeSpec)
        for (s in 0 until (17 * 5 - 1)) {
            writer.writeSampleI16(s.toShort())
        }
        assertFailsWith<HoundError.UnfinishedSample> {
            writer.finalize()
        }
    }

    @Test
    fun wideWriteShouldSignalError() {
        val buffer = HoundByteCursor()
        val spec8 =
            WavSpec(
                channels = 1u,
                sampleRate = 44100u,
                bitsPerSample = 8u,
                sampleFormat = SampleFormat.IntSample,
            )
        run {
            val writer = WavWriter.new(buffer, spec8)
            writer.writeSampleI8(127)
            writer.writeSampleI16(127)
            writer.writeSampleI32(127)
            assertFailsWith<HoundError.TooWide> { writer.writeSampleI16(128) }
            assertFailsWith<HoundError.TooWide> { writer.writeSampleI32(128) }
        }

        val spec16 = spec8.copy(bitsPerSample = 16u)
        run {
            val writer = WavWriter.new(buffer, spec16)
            writer.writeSampleI16(32767)
            writer.writeSampleI32(32767)
            assertFailsWith<HoundError.TooWide> { writer.writeSampleI32(32768) }
        }

        val spec24 = spec8.copy(bitsPerSample = 24u)
        run {
            val writer = WavWriter.new(buffer, spec24)
            writer.writeSampleI32(8_388_607)
            assertFailsWith<HoundError.TooWide> { writer.writeSampleI32(8_388_608) }
        }
    }

    @Test
    fun s24WavWrite() {
        TestSamples.ensureLoaded()
        val buffer = HoundByteCursor()
        val spec =
            WavSpecEx(
                spec =
                    WavSpec(
                        channels = 2u,
                        sampleRate = 48000u,
                        bitsPerSample = 24u,
                        sampleFormat = SampleFormat.IntSample,
                    ),
                bytesPerSample = 4u,
            )
        run {
            val writer = WavWriter.newWithSpecEx(buffer, spec)
            writer.writeSampleI32(-96)
            writer.writeSampleI32(23_052)
            writer.writeSampleI32(8_388_607)
            writer.writeSampleI32(-8_360_672)
            writer.finalize()
        }

        val expected = HoundVirtualFs.readFile("testsamples/waveformatextensible-24bit-4byte-48kHz-stereo.wav")
        assertContentEquals(expected, buffer.intoInner())
    }
}
