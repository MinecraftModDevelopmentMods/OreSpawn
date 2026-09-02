# ForgeGradle 7 Mavenizer compatibility fixture

This directory contains a build-only derivative of MinecraftForge's
MinecraftMavenizer `0.5.21`. It is used only while ForgeGradle prepares the
exact Forge `26.1.2-64.0.9` development dependency and is excluded from every
OreSpawn publication artifact.

## Provenance and licence

- Upstream: <https://github.com/MinecraftForge/MinecraftMavenizer>
- Exact source commit: `6968241ce7a0a902cdc1c534b976e8373a423091`
- Upstream version: `0.5.21`
- Licence: LGPL-2.1-only; see `LICENSE-MAVENIZER.txt`
- OreSpawn derivative patch: `minecraft-mavenizer-0.5.21-orespawn-compat.patch`
- Embedded/external rule manifest: `minecraft-source-compatibility.json`

The patch adds a target-aware compatibility stage before Mavenizer recompiles
decompiled sources. Rules run only for an exact Maven artifact listed in the
manifest. Each rule requires one exact source file, one exact record
declaration and one accessor in that record. Missing, partial, duplicate or
ambiguous states fail preparation. Targets without an explicit rule set are
left unchanged.

Forge `26.1.2-64.0.9` compiles without source compatibility edits. Its explicit
zero-rule entry proves that result is intentional and target-qualified rather
than an accidental fall-through. A marker beside Mavenizer's output records
the target and manifest SHA-256. Reprocessing the same source is idempotent.

The derivative also propagates Gradle offline mode when the build sets
`ORESPAWN_MAVENIZER_OFFLINE=true`. Mavenizer, OreSpawn and Minecraft 26.1.2 all
run and compile with Java 25.

## Rebuild

1. Clone the upstream repository and detach at
   `6968241ce7a0a902cdc1c534b976e8373a423091`.
2. Apply `minecraft-mavenizer-0.5.21-orespawn-compat.patch` with `git am`.
3. Set `JAVA_HOME` to Temurin Java 25.
4. Run `./gradlew clean build --no-daemon` (or `gradlew.bat` on Windows).
5. Copy `build/libs/minecraft-mavenizer-0.5.21.jar` to
   `minecraft-mavenizer-0.5.21-orespawn-compat.jar`.
6. Run OreSpawn's `verifyMavenizerCompatibilityFixture` task. The build script
   contains the authoritative checksums and also verifies the embedded
   manifest, Java class version and licence/provenance files.

The sealed derivative was built with Eclipse Temurin `25.0.3+9` and Gradle
`9.1.0` from the upstream wrapper.
