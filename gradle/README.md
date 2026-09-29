# Build logic

`build.gradle` describes the Forge 1.10 project: its dependencies, Java 8
output, development runs, processed resources and ordinary jars. The applied
scripts keep the longer test, release and Eclipse workflows close to the tasks
they own. They are part of this repository, not a separate Gradle plugin.
Editable release identity (name, vendor, artifact name, license and project
links) lives in `gradle.properties`; compatibility-sensitive group and artifact
values are still checked by the release audit.

| Script | What belongs here |
| --- | --- |
| `verification/support.gradle` | Sealed old-mod fixtures, test-only Mineralogy oracle and the Forge-run helper. |
| `verification/legacy-and-migration.gradle` | Fresh/reload surface worlds, Mineralogy 3 migration and opt-in OS1/OS3 binary checks. |
| `verification/client-and-benchmark.gradle` | Rendered client check and opt-in worldgen benchmark. |
| `release/artifacts.gradle` | Reobfuscated jar, release audits and SHA-256 checksums. |
| `release/publishing.gradle` | Maven coordinates, POM and credential guards. |
| `ide/eclipse.gradle` | Processed Eclipse resources, integration launches and production-classpath checks. |

The root applies the scripts in dependency order. `oreSpawnBuild` shares a few
project values with release and Eclipse tasks; `oreSpawnVerification` shares
fixture paths and the Forge-run helper; `oreSpawnRelease` shares the audited
jar and file names with publishing. Keep a new task in its owning script and
keep existing task names stable for CI and documented commands.

The routine offline gate is `clean check build javadoc verifyReleaseArtifacts
writeReleaseChecksums`. Run `clientIntegrationProcess` separately to exercise
the editor and a rendered fresh/reloaded world, and `genEclipseRuns
verifyEclipseProductionClasspath` for normal Eclipse launches. The OS1/OS3
ABI check and worldgen benchmark are opt-in. The benchmark needs
`-PbenchmarkRunDir=...`; it is not part of every build.

Forge 1.10's Mineralogy 3 migration gate is **not** obsolete: it runs under
`check` and protects the Cyano world-profile handoff. Unlike the 1.12 build,
this branch has no Gradle packaged-Forge runtime task. The exact reobfuscated
jar is tested separately in a disposable official Forge 12 installation.
