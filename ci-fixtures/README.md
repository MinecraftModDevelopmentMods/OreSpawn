# OreSpawn 1.16.5 CI fixtures

These immutable inputs make the legacy-Mineralogy compatibility gate
self-contained. They are test oracles only and must never enter a Gradle
dependency configuration, Eclipse launch, or published OreSpawn artifact.

`Mineralogy-1.16.5-5.2.0.jar` was reproduced from the exact historical
MinecraftMineralogy source commit
`15508b27ee16e9005f21fd8c661a4350eee391d5` using Java 8 and the original
ForgeGradle 5 / Gradle 7.3.3 build. Its checksum is sealed in `SHA256SUMS`
and validated before the oracle is loaded through the isolated test
classloader.
