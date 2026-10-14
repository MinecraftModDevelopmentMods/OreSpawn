# Build scripts

The root `build.gradle` describes the Forge 1.19.4 target, Java toolchain,
dependencies, development runs and resources. The longer checks live in these
scripts so it is easier to find the task you need to change.

| Script | What it owns |
| --- | --- |
| `verification/support.gradle` | Sealed fixtures, Java launch helpers and runtime-log checks. |
| `verification/client-and-benchmark.gradle` | Isolated development-client and surface-generation probes. |
| `verification/ore-sources.gradle` | Test-only future MMD providers and missing-provider reloads. |
| `verification/native-ores.gradle` | Real Nether generation with tagged and explicit hosts, reloads and placement edits. |
| `verification/performance.gradle` | Matched generation timing and unchanged-world comparisons. |
| `verification/biome-override.gradle` | Exact replacement and no-retrogen checks in a disposable save. |
| `verification/packaged-forge.gradle` | Finished-jar checks in disposable official Forge installations. |
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

The future-provider and world-hash fixtures are test-only mods. They must never
appear in an ordinary Eclipse launch, production classpath or release jar.

For manual shared-ore testing, run `genEclipseRuns` and open
`runTestOreSourcesClient.launch` (or use `runOreSourcesClient`). It adds two
dummy providers in `run-ore-sources`, without clearing settings or saves.
`oreSourcesClientIntegrationTest` runs the same profile automatically in a
separate disposable directory. `nativeOreIntegrationTest` checks tag-only
quartz and fluid hosts, explicit hosts, reloads and edits affecting new terrain
only. Both server fixture gates need `packagedForgeServerRuntime`.
