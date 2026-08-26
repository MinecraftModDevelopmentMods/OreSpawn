# OreSpawn 1.15.2 CI fixtures

These immutable inputs make the legacy-Mineralogy compatibility gate
self-contained. They are test oracles only and must never enter a Gradle
dependency configuration, Eclipse launch, or published OreSpawn artifact.

`Mineralogy-1.15.2-5.1.1.jar` was reproduced from the exact historical
MinecraftMineralogy source commit
`e1d324fd77ce33bc040cb870864238b37e34d27f` using Java 8 and the original
ForgeGradle 3 / Gradle 4.10.3 build. Its checksum is sealed in `SHA256SUMS`
and validated before the oracle is loaded through the isolated test
classloader.
