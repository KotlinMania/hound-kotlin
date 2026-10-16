# hound-kotlin in Kotlin

[![GitHub link](https://img.shields.io/badge/GitHub-KotlinMania%2Fhound-kotlin-blue.svg)](https://github.com/KotlinMania/hound-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/hound-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/hound-kotlin)
[![Build status](https://img.shields.io/github/actions/workflow/status/KotlinMania/hound-kotlin/ci.yml?branch=main)](https://github.com/KotlinMania/hound-kotlin/actions)

This is a Kotlin Multiplatform line-by-line transliteration port of [`ruuda/hound`](https://github.com/ruuda/hound) (crate `hound`).

**Original Project:** This port is based on [`ruuda/hound`](https://github.com/ruuda/hound). All design credit and project intent belong to the upstream authors; this repository is a faithful port to Kotlin Multiplatform with no behavioural changes intended.

### Porting status

This is an **in-progress port**. The goal is feature parity with the upstream Rust crate while providing a native Kotlin Multiplatform API. Every Kotlin file carries a `// port-lint: source <path>` header naming its upstream Rust counterpart so the AST-distance tool can track provenance.

---

## Upstream README — `ruuda/hound`

> The text below is reproduced from [`https://github.com/ruuda/hound`](https://github.com/ruuda/hound). It is the upstream project's own description and remains under the upstream authors' authorship; links have been rewritten to absolute upstream URLs where appropriate so they continue to resolve from this repository.

Hound
=====
A wav encoding and decoding library in Rust.

[![Crates.io version][crate-img]][crate]
[![Changelog][changelog-img]](changelog.md)
[![Documentation][docs-img]][docs]

Hound can read and write the WAVE audio format, an ubiquitous format for raw,
uncompressed audio. The main motivation to write it was to test
[Claxon][claxon], a FLAC decoding library written in Rust.

Examples
--------
The following example renders a 440 Hz sine wave, and stores it as a mono wav
file with a sample rate of 44.1 kHz and 16 bits per sample.

```rust
use std::f32::consts::PI;
use std::i16;
use hound;

let spec = hound::WavSpec {
    channels: 1,
    sample_rate: 44100,
    bits_per_sample: 16,
    sample_format: hound::SampleFormat::Int,
};
let mut writer = hound::WavWriter::create("sine.wav", spec).unwrap();
for t in (0 .. 44100).map(|x| x as f32 / 44100.0) {
    let sample = (t * 440.0 * 2.0 * PI).sin();
    let amplitude = i16::MAX as f32;
    writer.write_sample((sample * amplitude) as i16).unwrap();
}
```

The file is finalized implicitly when the writer is dropped, call
`writer.finalize()` to observe errors.

The following example computes the root mean square (RMS) of an audio file with
at most 16 bits per sample.

```rust
use hound;

let mut reader = hound::WavReader::open("testsamples/pop.wav").unwrap();
let sqr_sum = reader.samples::<i16>()
                    .fold(0.0, |sqr_sum, s| {
    let sample = s.unwrap() as f64;
    sqr_sum + sample * sample
});
println!("RMS is {}", (sqr_sum / reader.len() as f64).sqrt());
```

Features
--------

|                 | Read                                                    | Write                                   |
|-----------------|---------------------------------------------------------|-----------------------------------------|
| Format          | `PCMWAVEFORMAT`, `WAVEFORMATEX`, `WAVEFORMATEXTENSIBLE` | `PCMWAVEFORMAT`, `WAVEFORMATEXTENSIBLE` |
| Encoding        | Integer PCM, IEEE Float                                 | Integer PCM, IEEE Float                 |
| Bits per sample | 8, 16, 24, 32 (integer), 32 (float)                     | 8, 16, 24, 32 (integer), 32 (float)     |

Contributing
------------
Contributions in the form of bug reports, feature requests, or pull requests are
welcome. See [contributing.md](contributing.md).

License
-------
Hound is licensed under the [Apache 2.0][apache2] license. It may be used in
free software as well as closed-source applications, both for commercial and
non-commercial use under the conditions given in the license. If you want to
use Hound in your GPLv2-licensed software, you can add an [exception][exception]
to your copyright notice. Please do not open an issue if you disagree with the
choice of license.

[crate-img]:     https://img.shields.io/crates/v/hound.svg
[crate]:         https://crates.io/crates/hound
[changelog-img]: https://img.shields.io/badge/changelog-online-blue.svg
[docs-img]:      https://img.shields.io/badge/docs-online-blue.svg
[docs]:          https://docs.rs/hound
[claxon]:        https://github.com/ruuda/claxon
[apache2]:       https://www.apache.org/licenses/LICENSE-2.0
[exception]:     https://www.gnu.org/licenses/gpl-faq.html#GPLIncompatibleLibs

---

## About this Kotlin port

### Installation

```kotlin
dependencies {
    implementation("io.github.kotlinmania:hound-kotlin:0.1.0")
}
```

### Building

```bash
./gradlew build
./gradlew test
```

### Targets

- macOS arm64
- Linux x64
- Windows mingw-x64
- iOS arm64 / simulator-arm64 (Swift export + XCFramework)
- JS (browser + Node.js)
- Wasm-JS (browser + Node.js)
- Android (API 24+)

### Porting guidelines

See [AGENTS.md](AGENTS.md) and [CLAUDE.md](CLAUDE.md) for translator discipline, port-lint header convention, and Rust → Kotlin idiom mapping.

### License

This Kotlin port is distributed under the same license terms as the upstream [`ruuda/hound`](https://github.com/ruuda/hound). See [LICENSE](LICENSE) (and any sibling `LICENSE-*` / `NOTICE` files mirrored from upstream) for the full text.

Original work copyrighted by the hound authors.  
Kotlin port: Copyright (c) 2026 Sydney Renee and The Solace Project.

### Acknowledgments

Thanks to the [`ruuda/hound`](https://github.com/ruuda/hound) maintainers and contributors for the original Rust implementation. This port reproduces their work in Kotlin Multiplatform; bug reports about upstream design or behavior should go to the upstream repository.
