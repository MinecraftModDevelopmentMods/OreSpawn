# Build scripts

`build.gradle` defines the Forge 1.16.5 target, Java toolchains, dependencies,
run setup, and resource processing. The longer checks are grouped by the work
they verify. The root applies these scripts in dependency order.

| Script | What it does |
| --- | --- |
| `verification/support.gradle` | Shared Java launcher and runtime-log checks. |
| `verification/client-and-benchmark.gradle` | Isolated development client and world-generation probes. |
| `verification/ore-sources.gradle` | Test-only future MMD provider mods and missing/restored provider checks. |
| `verification/native-ores.gradle` | Real Nether ore and tag-only fluid checks, reloads and depth edits. |
| `verification/packaged-forge.gradle` | Finished-jar checks in disposable official Forge installs. |
| `verification/performance.gradle` | Matched world-generation timings and completed-world hash comparisons. |
| `release/artifacts.gradle` | Reobfuscated jars, documentation and artifact audits, and checksums. |
| `release/publishing.gradle` | Maven coordinates, credentials, and opt-in publication. |
| `ide/eclipse.gradle` | Generated launches, Buildship setup, and production-classpath isolation. |

The fixture mods have their own source trees. They must not enter an ordinary
Eclipse run, production classpath, or release jar. After changing resources or
launch setup, run `genEclipseRuns verifyEclipseProductionClasspath` as well as
the command-line build.

Packaged checks require `packagedForgeServerRuntime` and
`packagedForgeClientRuntime` properties pointing to disposable official Forge
installs. They are not part of an ordinary `check`; the server command ends in
literal `nogui`.

Disposable runs write Forge's complete `fml.toml` defaults before startup to
avoid first-run file-watcher races. This helper only writes below `build/`
and leaves existing files alone. Configuration parsing exceptions fail the
log audit even when Forge logs them at INFO level.

`nativeOreIntegrationTest` compares the shipped quartz host tag with explicit
Netherrack hosts in fresh terrain, the exact saved world, and new chunks after
a depth edit. It also checks a tag-only fluid deposit at startup.
`oreSourceFixtureIntegrationTest` covers one/both/missing/restored providers.

The manual `runOreSourcesClient` profile uses its own `run-ore-sources` folder.
Both Eclipse launch names are generated together and checked for all required
Forge bootstrap values. Preparation never clears that folder. The Eclipse
resource-rebuild check ensures expanded mod metadata survives a Java rebuild.

Run Gradle with Java 17 and compile Minecraft code with the pinned Java 8
toolchain. A warm-cache offline build is useful for day-to-day work, but a
fresh-cache bootstrap and offline replay are separate release checks.

To compare 4.0.16 with 4.1, pass `benchmarkOreSpawnJar` with the finished
4.0.16 jar, then run the same performance task without it. Set
`benchmarkWorldHash=true` for both runs before `verifyWorldgenParity`.
