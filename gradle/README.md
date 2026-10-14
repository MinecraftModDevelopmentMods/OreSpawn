# Build scripts

The root `build.gradle` describes the Forge 1.20.6 target, Java toolchain,
dependencies, development runs and resources. Longer checks are split by
responsibility so a maintainer can find the relevant task without scanning
the entire build.

| Script | What it owns |
| --- | --- |
| `verification/support.gradle` | Sealed fixtures, Forge run setup, Java launch helpers and runtime-log checks. |
| `verification/client-and-benchmark.gradle` | Isolated development-client and surface-generation probes. |
| `verification/packaged-forge.gradle` | Finished-jar checks in disposable official Forge installations. |
| `verification/ore-sources.gradle` | Test-only future MMD providers and missing-provider reloads. |
| `verification/performance.gradle` | Matched world-generation checks with optional sibling mods. |
| `verification/biome-override.gradle` | New-terrain biome replacement and no-retrogen probe. |
| `verification/native-ores.gradle` | Native Nether ore takeover, saved policies and wrapper cleanup. |
| `release/artifacts.gradle` | Release jars, bundled guides, artifact audits and checksums. |
| `release/publishing.gradle` | Maven coordinates, credentials and opt-in publication. |
| `ide/eclipse.gradle` | Generated launches, Buildship setup and production-classpath isolation. |

An ordinary build does not publish anything. Before sharing a candidate, run
`check`, `build`, `javadoc`, `verifyReleaseArtifacts` and
`writeReleaseChecksums`. For Eclipse, also run `genEclipseRuns` and
`verifyEclipseProductionClasspath`.

Packaged checks take `packagedForgeServerRuntime` and
`packagedForgeClientRuntime` properties pointing to disposable official Forge
installations. They are separate from `check`; the server command ends in
literal `nogui`. Run Gradle and the pinned compilation toolchain on Java 21.
For the development-client probe, pass `-PoreSpawnClientProbe=true` so its
world and settings stay under `build/client-userdev-run`.

For manual shared-ore testing, use `runOreSourcesClient.launch` (also named
`runTestOreSourcesClient.launch`). Both inherit Forge 50's ordinary launcher
and add only the two simulated providers. Settings stay in `run-ore-sources`.
`verifyOreSourcesClientLaunch` checks their bootstrap arguments and classpath.
`oreSourcesClientIntegrationTest` checks the same setup automatically in a
separate, hidden, build-owned client. It does not change manual settings.

`nativeOreIntegrationTest` covers fresh, reloaded and edited Nether worlds.
The cold-cache CI job compiles the real target online, then forces an offline
replay. Forge 50 needs no Mavenizer source repairs; its sealed fixture records
that explicit zero-rule result.

World-hash comparisons can use `-PbenchmarkProcessorCount=2` to keep Minecraft
on one generation worker. Leave that property unset for ordinary performance
measurements, which use the machine's normal processor count.

The client, surface, native-ore and future-provider fixtures are test-only mods. They must never appear in
an ordinary Eclipse launch, production classpath or release jar.
