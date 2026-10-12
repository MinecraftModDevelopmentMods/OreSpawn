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
- `verification/native-ores.gradle` checks managed Nether quartz, tag and
  explicit hosts, new-terrain edits and shutdown cleanup in a packaged server.
- `release/artifacts.gradle` checks the three release jars, bundled guide,
  metadata and checksums.
- `release/publishing.gradle` holds the opt-in Maven publication and credential
  checks. An ordinary build never uploads anything.
- `ide/eclipse.gradle` keeps Buildship on processed production resources and
  checks that test fixtures do not enter normal Eclipse launches.

Run `clean` separately, then `check build javadoc verifyReleaseArtifacts
writeReleaseChecksums`. After changing resources or launch settings, run
`eclipse verifyEclipseProductionClasspath`. Buildship supplies NeoGradle's
Eclipse import marker automatically; from a terminal, also pass
`-Declipse.application=org.eclipse.jdt.ls.core.id1` (quote this argument in
PowerShell). This regenerates the native launches rather than validating stale
files from an earlier import.
The client, future-provider, biome-replacement, packaged-runtime and performance
checks are separate opt-in gates; their required runtime paths are described
beside those tasks. Use Java 25 and
the branch's configured Gradle cache for NeoForge 26.3.

`nativeOreIntegrationTest` requires
`-PpackagedNeoForgeServerRuntime=<official server installation>`. To test a
different compatible loader, also pass `-PpackagedNeoForgeVersion=<version>`.
Every packaged server command ends in literal `nogui`.

`oreSourcesClientIntegrationProcess` opens the editors and a fresh/reloaded
world with the two future-provider fixtures. For manual testing, use
`runOreSourcesClient.launch` or `runTestOreSourcesClient.launch`; both use
`run-ore-sources`, leaving the ordinary profile alone. Only fixture jars are
refreshed there; configs and saves are not replaced.

Both isolated client checks inspect Minecraft's queued text as well as opening
the screens. They cover the biome directory and compact detail page at
426x265 and 320x240, so a working button cannot hide an invisible list label.
They also send native mouse events through the screen to check biome selection,
scrolling and dragging, compact navigation, and the shared-ore output checkboxes.
Main-menu checks measure the native tab and fallback screen against their
footer boundaries, with terrain, fluid and template controls on and off.

When adding a task, put it next to the checks it supports and keep any shared
helper in the small `oreSpawnBuild` map in the root build. Test-only source sets
must stay out of the release jars and normal Eclipse classpath.
