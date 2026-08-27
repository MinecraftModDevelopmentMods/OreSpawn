# Forge 1.18.2 Mavenizer compatibility fixture

`minecraft-mavenizer-0.5.21-orespawn-1.18.jar` is a build-only derivative of
MinecraftForge MinecraftMavenizer `0.5.21`, source commit
`6968241ce7a0a902cdc1c534b976e8373a423091`.

Forge 40's 1.18.2 decompiler emits a redundant explicit `Holder.Direct#value`
record accessor. The complete patched Minecraft source tree is rejected by
modern `javac`; removing that method is safe because the record generates the
same public accessor automatically. The derivative also honors
`ORESPAWN_MAVENIZER_OFFLINE=true` by adding Mavenizer's native `--offline`
argument. This closes ForgeGradle 7's failure to propagate Gradle offline mode
to the subprocess; it does not alter normal online builds.

`minecraft-1.18-holder-accessor.patch` documents the complete two-file source
change.

The fixture is used only while ForgeGradle prepares the development dependency.
It is checksum-verified by the build and excluded from every OreSpawn artifact.
MinecraftMavenizer remains licensed LGPL-2.1-only; see `LICENSE-MAVENIZER.txt`.

SHA-256:
`28A6697BDF6D9500EC0AF9C1C949B9F3ED7DDFDA649CB5CAC4BEEA2E9567570A`
