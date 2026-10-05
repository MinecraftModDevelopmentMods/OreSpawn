# Build scripts

`build.gradle` holds the Forge 1.15.2 target, Java toolchains, dependencies,
run setup and resource processing. The longer checks live beside the work they
verify. The root applies these scripts in dependency order through a small
`oreSpawnBuild` helper map.

| Script | Responsibility |
| --- | --- |
| `verification/support.gradle` | Forge log checks and ordinary development-run guards. |
| `verification/client-and-benchmark.gradle` | Isolated client and surface probes, including fresh/reload development runs. |
| `verification/ore-sources.gradle` | Test-only future-provider mods and missing/restored provider checks. |
| `verification/packaged-forge.gradle` | Finished-jar checks in disposable official Forge client and server installs. |
| `verification/performance.gradle` | Matched worldgen benchmarks and completed-world hash comparisons. |
| `verification/native-ores.gradle` | Managed Nether quartz with tag and explicit hosts, reload and depth edits. |
| `release/artifacts.gradle` | Reobfuscated jars, artifact and documentation audits, and checksums. |
| `release/publishing.gradle` | Maven coordinates, credentials and opt-in publication. |
| `ide/eclipse.gradle` | Generated launches, Buildship setup and production-classpath isolation. |

Keep test mods in their own source trees. They must not appear in an ordinary
Eclipse run, production classpath or release jar. After changing resources or
launch setup, run `genEclipseRuns verifyEclipseProductionClasspath` as well as
the command-line build.

Packaged checks need explicit `packagedForgeServerRuntime` and
`packagedForgeClientRuntime` properties pointing to disposable official Forge
installs. They are not part of an ordinary `check`; the server command ends in
literal `nogui`.

Run Gradle with Java 17 and compile Minecraft code with the pinned Java 8
toolchain. A warm-cache offline build is useful for day-to-day work, but it
does not replace a fresh-cache bootstrap and offline replay.

For a 4.0.16-versus-4.1 comparison, pass `benchmarkOreSpawnJar` with the
finished 4.0.16 jar, then run the same performance task without it. Add
`benchmarkWorldHash=true` to both runs before `verifyWorldgenParity`.

Run `nativeOreIntegrationTest` with `packagedForgeServerRuntime` to check
managed quartz in a real Nether. Separate build-owned worlds exercise Forge
31's shipped netherrack tag and explicit block hosts. Each world is reloaded,
then given a different depth range to check that existing chunks stay unchanged.

## Trying shared ores in the editor

Run `genEclipseRuns verifyEclipseProductionClasspath`, refresh the Eclipse
project, then select **runOreSourcesClient**. You can also start it with
`gradlew runOreSourcesClient`. It uses `run-ore-sources`, separate from the
normal client's worlds and configuration, and keeps your test worlds between
launches.

The earlier **runTestOreSourcesClient** Eclipse filename is kept as an exact
copy of this launch. Run generation refreshes both names, including the Forge
bootstrap environment, so an older Eclipse selection still works.

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
