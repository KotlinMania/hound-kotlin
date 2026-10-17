#if canImport(Testing)
import Testing
import Hound

@Suite("Hound Swift Export Tests")
struct HoundExportTests {
    @Test("Hound swift module imported cleanly")
    func testSwiftModuleLoads() throws {
        let spec = WavSpec(channels: 2, sampleRate: 44100, bitsPerSample: 16, sampleFormat: .IntSample)
        #expect(spec.channels == 2)
        #expect(spec.sampleRate == 44100)
        #expect(spec.bitsPerSample == 16)
    }
}
#elseif canImport(XCTest)
import XCTest
import Hound

final class HoundExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        let spec = WavSpec(channels: 2, sampleRate: 44100, bitsPerSample: 16, sampleFormat: .IntSample)
        XCTAssertEqual(spec.channels, 2)
        XCTAssertEqual(spec.sampleRate, 44100)
        XCTAssertEqual(spec.bitsPerSample, 16)
    }
}
#endif
