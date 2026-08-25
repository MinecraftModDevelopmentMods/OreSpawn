# OreSpawn 1.14.4 CI fixtures

These immutable inputs make the legacy-Mineralogy compatibility gate
self-contained. They are test oracles only and must never enter a Gradle
dependency configuration, Eclipse launch, or published OreSpawn artifact.

`Mineralogy-1.14.4-5.0.1.jar` was reproduced from the exact historical
MinecraftMineralogy source commit
`6649bfb0fe3a71aeae729c63f1a78d97c2150caa` using Java 8 and the original
ForgeGradle 3 / Gradle 4.9 build. The build completed offline against the
shared verified cache. Its checksum is sealed in `SHA256SUMS` and validated
before the oracle is loaded through the isolated test classloader.
