# Build scripts

The root `build.gradle` describes the Forge 26.1.2 target, Java toolchain,
dependencies, development runs and resources. Longer checks are split by
responsibility so maintainers can find the relevant task quickly.

| Script | What it owns |
| --- | --- |
| `verification/support.gradle` | Sealed fixtures, Forge run setup, Java launch helpers and runtime-log checks. |
| `verification/client-and-benchmark.gradle` | Isolated development-client and surface-generation probes. |
| `verification/packaged-forge.gradle` | Finished-jar checks in disposable official Forge installations. |
| `verification/ore-sources.gradle` | Test-only future MMD providers and missing-provider reloads. |
| `verification/performance.gradle` | Matched world-generation checks with optional sibling mods. |
| `verification/biome-override.gradle` | New-terrain biome replacement and no-retrogen probe. |
| `verification/native-ores.gradle` | Managed Nether quartz, tag/block hosts, reloads and wrapper cleanup. |
| `release/artifacts.gradle` | Release jars, bundled guides, artifact audits and checksums. |
| `release/publishing.gradle` | Maven coordinates, credentials and opt-in publication. |
| `ide/eclipse.gradle` | Generated launches, Buildship setup and production-classpath isolation. |

An ordinary build does not publish anything. Before sharing a candidate, run
`check`, `build`, `javadoc`, `verifyReleaseArtifacts` and
`writeReleaseChecksums`. For Eclipse, also run `genEclipseRuns` and
`verifyEclipseProductionClasspath`.

Packaged checks take `packagedForgeServerRuntime` and
`packagedForgeClientRuntime` properties pointing to disposable official Forge
installations. They are separate from `check`; server commands end in literal
`nogui`. Gradle and compilation both use the pinned Temurin Java 25 toolchain.

The client, surface and future-provider fixtures are test-only mods. They must
never appear in an ordinary Eclipse launch, production classpath or release jar.

`runOreSourcesClient` (also `runTestOreSourcesClient`) opens the shared-ore
manual profile without clearing its saves. `oreSourcesClientIntegrationTest`
uses a separate build-owned directory for the automated rendered-world check.
Both inherit Forge's generated launcher arguments rather than maintaining a
second launcher setup.

The cold-cache CI gate compiles the target and its resources, then repeats
that work offline with forced tasks. It checks that Mavenizer's qualification
markers remain unchanged. Release checksums declare their jar inputs so a
changed artifact cannot leave an old SHA256SUMS file behind.

For generation diagnostics, `benchmarkProcessors=2` gives Minecraft one chunk
worker and `benchmarkRunLabel=serial-` keeps those worlds separate. Leave the
processor count unset for normal performance measurements. Neither property
changes a profile, ore rule or comparison threshold.
