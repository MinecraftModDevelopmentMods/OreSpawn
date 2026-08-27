# OreSpawn 1.17.1 CI fixtures

These immutable inputs make the legacy-Mineralogy compatibility gate
self-contained. They are test oracles only and must never enter a Gradle
dependency configuration, Eclipse launch, or published OreSpawn artifact.

`Mineralogy-1.17.1-5.3.0.jar` was reproduced from the exact historical
MinecraftMineralogy source commit
`a732b455a08119b441f2b83b5e33e654b8aa21a4` using Java 16 and the original
ForgeGradle 5 / Gradle 7.2 build. Its checksum is sealed in `SHA256SUMS`
and validated before the oracle is loaded through the isolated test
classloader.
