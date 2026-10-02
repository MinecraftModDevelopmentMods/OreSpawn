# Build scripts

`build.gradle` keeps the Forge 1.14.4 target, Java toolchains, repositories,
dependencies and resource setup together. The scripts below hold the longer
checks and release tasks. They are applied in dependency order, and share only
the small `oreSpawnBuild` helper map declared by the root build.

| Script | What belongs there |
| --- | --- |
| `verification/support.gradle` | Runtime-log checks and ordinary development-launch guards. |
| `verification/client-and-benchmark.gradle` | Isolated client and surface-test mods and fresh/reload development runs. |
| `verification/ore-sources.gradle` | Dummy future-provider mods, their manual client profile and fresh/reload checks. |
| `verification/native-ores.gradle` | Managed Nether quartz with tag and block hosts, reloads and new-terrain-only edits. |
| `verification/packaged-forge.gradle` | Finished-jar tests in disposable official Forge installations. |
| `verification/performance.gradle` | Matched vanilla, Cyano and Sky benchmarks and completed-world hash comparisons. |
| `release/artifacts.gradle` | Reobfuscated jars, artifact audits, documentation parity and checksums. |
| `release/publishing.gradle` | Maven publication, coordinates and credential checks. |
| `ide/eclipse.gradle` | Buildship setup, generated launches and production-classpath verification. |

Put a new check next to the work it verifies. Test mods belong in their own
source trees; do not add them to normal runs, production classpaths or release
jars. After changing launch or resource setup, run `genEclipseRuns` and
`verifyEclipseProductionClasspath` as well as the command-line build.

The packaged checks require explicit `packagedForgeServerRuntime` and
`packagedForgeClientRuntime` properties pointing to disposable official Forge
installations. They are opt-in; an ordinary `check` does not launch a packaged
game. Server invocations keep the final argument as literal `nogui`.

For a 4.0.16-versus-4.1 comparison, pass `benchmarkOreSpawnJar` with the
finished 4.0.16 jar, then run the same performance task without it. Add
`benchmarkWorldHash=true` to both runs before `verifyWorldgenParity`.

Use Java 17 to run Gradle and the pinned Java 8 toolchain to compile and run
Minecraft. A warm-cache offline build is useful for normal development, but
does not replace a fresh-cache bootstrap test.

Run `nativeOreIntegrationTest` with `packagedForgeServerRuntime` to check
managed quartz in a real Nether. Separate build-owned worlds exercise Forge
28's shipped netherrack tag and explicit block hosts. Each world is reloaded,
then given a different depth range to check that existing chunks stay unchanged.

## Trying shared ores in the editor

Run `genEclipseRuns verifyEclipseProductionClasspath`, refresh the Eclipse
project, then select **runOreSourcesClient**. You can also start it with
`gradlew runOreSourcesClient`. It uses `run-ore-sources`, separate from the
normal client's worlds and configuration, and keeps your test worlds between
launches.

The profile loads two dummy providers named Future Base Minerals Test and
Future Electric Advantage Test. They stand in for future MMD ports, not real
releases. Their shared Sulfur group uses blue, yellow and green wool as test
ore blocks. Open OreSpawn's world settings, then **Rocks & Ores → Ore Sources**,
to try the output modes, weights and placement choices. One Electric Advantage
output has no active rule of its own; consolidated modes can still select it
using the group's placement rule.

These dummy providers appear only in this named client and the automated
ore-source checks. They do not enter ordinary Eclipse launches or release jars.
The manual profile has no automatic world creation or shutdown probe.
