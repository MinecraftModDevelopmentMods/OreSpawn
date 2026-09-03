# OreSpawn 1.21.1 CI fixtures

These immutable inputs make the legacy-Mineralogy compatibility gate
self-contained. They are test oracles only and must never enter a Gradle
dependency configuration, Eclipse launch, or published OreSpawn artifact.

Minecraft 1.21.1 has no published Mineralogy lineage of its own. The last
published legacy engine, `Mineralogy-1.18.2-5.4.0.jar`, was reproduced from the exact historical
MinecraftMineralogy source commit
`6675bac3cb9c1df138ce9b359c0b47d7a797cdfc` using Java 17 and the original
ForgeGradle 6 / Gradle 8.8 build. Its checksum is sealed in `SHA256SUMS`
and validated before the oracle is loaded through the isolated test
classloader as the mandatory legacy-configuration oracle for this target.
