# Build scripts

`build.gradle` sets the Forge 1.18.2 target, Java toolchains, dependencies,
development runs, and resource processing. The longer checks live in these
scripts so each task has an obvious home. The root applies them in dependency
order.

| Script | What it owns |
| --- | --- |
| `verification/support.gradle` | Fixture paths, Java launch helpers, and runtime-log checks. |
| `verification/client-and-benchmark.gradle` | Isolated development-client and surface-generation probes. |
| `verification/ore-sources.gradle` | Test-only future MMD providers, manual client profiles and missing-provider reloads. |
| `verification/native-ores.gradle` | Managed quartz, tag-only fluids and exact-save/new-terrain checks. |
| `verification/performance.gradle` | Matched generation timing and unchanged-world comparisons. |
| `verification/biome-override.gradle` | Exact replacement and no-retrogen checks in a disposable save. |
| `verification/packaged-forge.gradle` | Finished-jar checks in disposable official Forge installations. |
| `release/artifacts.gradle` | Release jars, bundled guides, artifact audits, and checksums. |
| `release/publishing.gradle` | Maven coordinates, credentials, and opt-in publication. |
| `ide/eclipse.gradle` | Generated launches, Buildship setup, and production-classpath isolation. |

The ordinary build does not publish anything. Run `check`, `build`, `javadoc`,
`verifyReleaseArtifacts`, and `writeReleaseChecksums` before sharing a
candidate. After changing resources or launch setup, also run
`genEclipseRuns verifyEclipseProductionClasspath`.

Packaged checks use `packagedForgeServerRuntime` and
`packagedForgeClientRuntime` properties pointing to disposable official Forge
installations. They are separate from ordinary `check`; the server command
ends in literal `nogui`.

Run Gradle with Java 17 and use the pinned Java 17 compilation toolchain. A
warm-cache offline build is useful for day-to-day work, but a fresh-cache
bootstrap and offline replay are separate release checks. Test-only fixture
mods must never enter an ordinary Eclipse launch, production classpath, or
release jar.

`runOreSourcesClient.launch` and `runTestOreSourcesClient.launch` open the
same separate manual profile with simulated Base Minerals and Electric
Advantage providers. Generate them with `genEclipseRuns`, then run
`verifyOreSourcesClientLaunch`. Preparing this profile keeps its saved worlds
and settings. `oreSourcesClientIntegrationTest` exercises the launch in a
build-owned disposable directory instead.

`nativeOreIntegrationTest` checks Nether quartz with the shipped host tag
and explicit block hosts, including reload and new-terrain-only changes.
It uses `packagedForgeServerRuntime`; an optional `nativeOreSpawnJar` supplies
an older jar when reproducing a regression. Neither fixture ships in OreSpawn.
