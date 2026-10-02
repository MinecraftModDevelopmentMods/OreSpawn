# Build scripts

`build.gradle` holds the project identity, Minecraft setup, dependencies and
resource processing. The smaller scripts below keep the less frequently changed
verification and release work out of the main build file.

| Script | What belongs there |
| --- | --- |
| `verification/support.gradle` | Runtime log checks and the sealed Mineralogy test oracle. |
| `verification/client-and-benchmark.gradle` | Isolated client and surface-test mods and their development runs. |
| `verification/packaged-forge.gradle` | Checks using the finished jar in an official Forge installation. |
| `verification/ore-sources.gradle` | Two small future-provider mods and their fresh, reload, missing-mod and restored-mod checks. |
| `verification/native-ores.gradle` | Managed Nether quartz, saved host compatibility, reload hashes and new-terrain-only changes. |
| `verification/performance.gradle` | Matched packaged-server worldgen timings on disposable worlds. |
| `release/artifacts.gradle` | Reobfuscated jars, artifact checks and checksums. |
| `release/publishing.gradle` | Maven publication and its credential checks. |
| `ide/eclipse.gradle` | Buildship resources, generated launches and production-classpath checks. |

The scripts share only the small `oreSpawnBuild` helper map in `build.gradle`.
When adding a task, put it beside the related checks and keep test fixtures out
of the ordinary client and server classpaths. Run `genEclipseRuns` and
`verifyEclipseProductionClasspath` after changing launch or resource setup.

Eclipse reads production resources from `build/resources/main`, not the raw
source folders. Gradle expands the Forge metadata and bundles the guides there
first. This keeps a normal Eclipse rebuild from replacing the version with a
`${version}` placeholder. `eclipseResourceRebuildTest` covers that copy step.

The packaged Forge tasks need explicit `packagedForgeServerRuntime` and
`packagedForgeClientRuntime` paths to disposable official installations. They
are opt-in: the normal `check` and `build` tasks do not start a packaged game.
Keep the final server argument as the literal `nogui` required by the runtime
gate. The cold-cache Forge bootstrap remains a separate CI check; an offline
build with a warm Gradle cache does not replace it.

Run `nativeOreIntegrationTest` with `packagedForgeServerRuntime` to check managed
quartz in a real Nether. It uses separate build-owned worlds for current defaults
and the legacy netherrack tag, checks exact-save reloads, then changes the depth
range and verifies that only new terrain uses it.

For a local sibling-mod check, pass both `packagedBaseMetalsJar` and
`packagedMineralogyJar` to the packaged client or surface test. These jars are
copied only into that disposable run; they are not build dependencies.
`packagedWorldgenPerformanceTest` uses the same optional sibling jars and
reports the three-pass Sky median against its vanilla and Cyano controls.
For a version-to-version terrain comparison, run `worldgenBenchmarkSky` once
with the 4.0.16 jar supplied as `benchmarkOreSpawnJar`, then again without that
property. Pass `benchmarkWorldHash=true` to both runs. The separate test-only
probe finishes the surrounding chunks before hashing the 81 measured chunks;
`verifyWorldgenParity` then compares complete block and biome hashes for all
three modes. Both runs need the same sibling jars and benchmark settings.

## Trying shared ores in the editor

Run `genEclipseRuns verifyEclipseProductionClasspath`, refresh the Eclipse
project, then select **runOreSourcesClient**. The same client can be started
with `gradlew runOreSourcesClient`. It uses `run-ore-sources`, separate from
the normal client's worlds and configuration, and keeps your test worlds
between launches.

This profile loads two dummy providers named Future Base Minerals Test and
Future Electric Advantage Test. They represent future MMD ports, not real
releases. Their shared Sulfur group uses blue, yellow and green wool as three
ore blocks. Open OreSpawn's world
settings, then **Rocks & Ores → Ore Sources**, to try the output modes, weights
and placement choices. One Electric Advantage output has no active rule of its
own; consolidated modes can still select it using the group's placement rule.

The dummy providers appear only in this named client and the automated
ore-source checks. They do not enter ordinary Eclipse launches or release
jars. The manual profile has no automatic world creation or shutdown probe.
