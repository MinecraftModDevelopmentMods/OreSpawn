# Build scripts

`build.gradle` defines the Forge 1.17.1 target, Java toolchains, dependencies,
development runs, and resource processing. The longer checks live in small
scripts so a maintainer can find the relevant task without searching one long
build file. The root applies them in dependency order.

| Script | Responsibility |
| --- | --- |
| `verification/support.gradle` | Shared Java launchers, fixture paths, and runtime-log checks. |
| `verification/client-and-benchmark.gradle` | Isolated development-client and world-generation probes. |
| `verification/ore-sources.gradle` | Test-only future MMD providers and missing-provider reloads. |
| `verification/native-ores.gradle` | Managed Nether quartz, tag-only fluids, reloads and new-terrain edits. |
| `verification/performance.gradle` | Matched world-generation timings and completed-world hashes. |
| `verification/packaged-forge.gradle` | Finished-jar checks in disposable official Forge installs. |
| `release/artifacts.gradle` | Release jars, documentation parity, artifact audits, and checksums. |
| `release/publishing.gradle` | Maven coordinates, credentials, and opt-in publication. |
| `ide/eclipse.gradle` | Generated launches, Buildship setup, and production-classpath isolation. |

The ordinary build does not publish anything. Run `check`, `build`, `javadoc`,
`verifyReleaseArtifacts`, and `writeReleaseChecksums` before sharing a candidate.
After changing resources or launch setup, also run
`genEclipseRuns verifyEclipseProductionClasspath`.

`prepareOreSourcesClient genEclipseRuns` also writes the two manual shared-ore
launch names. Their modules, assets and mappings come from Forge 37's ordinary
client launch, with one merged output per mod. Only the automatic client test
loads the shutdown probe. Manual settings and saves are preserved.

`nativeOreIntegrationTest` uses the official packaged server to compare the
shipped Nether host tag with the same explicit blocks. It checks fresh
generation, an exact-save reload and a depth edit in new chunks. For a
regression comparison, `nativeOreSpawnJar` may point at an older candidate;
it changes only the jar copied into these disposable test runs.
The independent tag/block comparison uses one generation worker to avoid
Forge 37's parallel-decoration variation. Ordinary runtime and performance
tests retain their normal worker count; exact-save reload checks still require
unchanged hashes.

Disposable build-owned runs start with a complete Forge `fml.toml` so
NightConfig cannot read a half-written first-run correction. Preparation
preserves existing files and refuses directories outside `build`. Manual
profiles are never seeded. Runtime checks reject parsing exceptions even when
Forge prints them at INFO level.

Packaged checks require `packagedForgeServerRuntime` and
`packagedForgeClientRuntime` properties pointing to disposable official Forge
installs. They are not part of ordinary `check`; the server command ends in
literal `nogui`.

Run Gradle with Java 17 and compile Minecraft code with the pinned Java 16
toolchain. A warm-cache offline build is useful for day-to-day work, but a
fresh-cache bootstrap and offline replay are separate release checks.

The fixture mods have separate source trees. They must not enter an ordinary
Eclipse launch, production classpath, or release jar. To compare 4.0.16 with
4.1, pass `benchmarkOreSpawnJar` with the finished 4.0.16 jar and run the same
performance task without it. Set `benchmarkWorldHash=true` for both before
`verifyWorldgenParity`.
