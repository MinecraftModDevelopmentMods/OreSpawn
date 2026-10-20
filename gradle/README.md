# Build scripts

The root `build.gradle` describes the Forge 1.21.11 target, Java toolchain,
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
| `verification/native-ores.gradle` | Native Nether ores, tag/explicit hosts and shutdown cleanup. |
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

The client, surface and future-provider fixtures are test-only mods. They must
never appear in an ordinary Eclipse launch, production classpath or release jar.

`runOreSourcesClient` is the separate manual GUI profile; its launch alias is
`runTestOreSourcesClient`. `oreSourcesClientIntegrationTest` runs that scenario
automatically in a disposable directory. Both inherit the ordinary Forge 61
launcher rather than maintaining a second set of bootstrap arguments.

Keep first-run test configuration under `build`. Do not change a developer's
existing settings to make a probe pass. Runtime log checks cover configuration
parsing failures as well as error-level messages and crash reports.
