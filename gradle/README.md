# Build scripts

The root `build.gradle` describes the Forge 1.20.1 target, Java toolchain,
dependencies, development runs and resources. The longer checks live in these
scripts so it is easier to find the task you need to change.

| Script | What it owns |
| --- | --- |
| `verification/support.gradle` | Sealed fixtures, Java launch helpers and runtime-log checks. |
| `verification/client-and-benchmark.gradle` | Isolated development-client and surface-generation probes. |
| `verification/packaged-forge.gradle` | Finished-jar checks in disposable official Forge installations. |
| `verification/ore-sources.gradle` | Test-only future MMD providers and missing-provider reloads. |
| `verification/native-ores.gradle` | Real Nether ore/fluid host, exact-reload and shutdown checks. |
| `verification/performance.gradle` | Matched world-generation checks with optional sibling mods. |
| `verification/biome-override.gradle` | New-terrain biome replacement and no-retrogen probe. |
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
literal `nogui`. Run Gradle and the pinned compilation toolchain on Java 17.

For manual shared-ore testing, run `genEclipseRuns` and open
`runTestOreSourcesClient.launch` (or its `runOreSourcesClient.launch` alias).
It uses `run-ore-sources` and keeps settings and saves separate from the
ordinary client. `oreSourcesClientIntegrationTest` checks that same launch
automatically in a disposable world.

Use `nativeOreIntegrationTest` with the packaged server runtime to compare
tagged and explicit Nether hosts, then reload and edit only new terrain.
`eclipseResourceRebuildTest` checks metadata after the resource-copy step
that Eclipse performs during a rebuild.

The client, surface and future-provider fixtures are test-only mods. They must never appear in
an ordinary Eclipse launch, production classpath or release jar.
