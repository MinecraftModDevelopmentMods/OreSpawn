# Build scripts

`build.gradle` keeps the target version, NeoGradle runs, dependencies, resources
and Java defaults in one place. The smaller scripts below own the longer checks:

- `verification/support.gradle` checks sealed test fixtures, protects a
  developer's local config during tests and reviews complete runtime logs.
- `verification/contracts.gradle` checks target and workflow assumptions.
- `verification/client-and-benchmark.gradle` builds the isolated surface,
  client and benchmark probes used in development runs.
- `verification/ore-sources.gradle` builds two test-only future MMD providers
  and checks their shared ore policy on fresh and reloaded worlds.
- `verification/biome-override.gradle` checks exact biome replacements in new
  terrain without changing a previously saved chunk.
- `verification/packaged-runtime.gradle` builds Java argument vectors from an
  official NeoForge installation; every dedicated-server command ends in `nogui`.
- `verification/native-ores.gradle` checks native and imported Nether ores,
  vanilla-management OFF/ON, fluid hosts, exact reload and new-terrain edits.
- `release/artifacts.gradle` checks the three release jars, bundled guide,
  metadata and checksums.
- `release/publishing.gradle` holds the opt-in Maven publication and credential
  checks. An ordinary build never uploads anything.
- `ide/eclipse.gradle` keeps Buildship on processed production resources and
  checks that test fixtures do not enter normal Eclipse launches.

Run `clean` first, then run the normal release check in a separate invocation:
`check build javadoc verifyReleaseArtifacts writeReleaseChecksums`. Run `eclipse
verifyEclipseProductionClasspath` after changing resources or launch settings.
Refresh the project in Eclipse Buildship and start the development runs from
its Gradle Tasks view; standalone `.launch` files are not required.
The client, future-provider, biome-replacement, packaged-runtime and performance
checks are separate opt-in gates; their required runtime paths are described
beside those tasks. Use Java 25 and
the branch's configured Gradle cache for NeoForge 26.1.2.

Downloaded dependencies can use a shared Gradle home. NeoGradle's intermediate
execution results stay in `.gradle/neo-execution` inside this checkout, so a
different Minecraft branch cannot leave incompatible staged results behind.
The usual `net.neoforged.gradle.caching.cacheDirectory` property still allows an
explicit cache location when investigating the build itself.

Use `runTestOreSourcesClient` for a manual client with both future MMD provider
fixtures. It uses `run-ore-sources`, separately from ordinary `runClient`, and
never resets its saved worlds or configuration. The coloured wool in this
profile deliberately stands in for ore blocks; these mods are test fixtures.

Run `nativeOreIntegrationTest` with
`-PpackagedNeoForgeServerRuntime=<official NeoForge 21.11.45 server installation>`.
It creates its own build-owned worlds and runs the ordinary release jar. None
of the fixture classes or resources belongs in a release artifact.

When adding a task, put it next to the checks it supports and keep any shared
helper in the small `oreSpawnBuild` map in the root build. Test-only source sets
must stay out of the release jars and normal Eclipse classpath.
