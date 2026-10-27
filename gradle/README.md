# Build logic

`build.gradle` defines the Forge 1.12 project, dependencies, Java 8 toolchain,
resource processing, reproducible archives, and the ordinary development runs.
Editable release identity (name, vendor, artifact name, license, and project
links) lives in `gradle.properties`. The release checks still pin compatibility
fields such as the Maven group and artifact name.
The applied scripts keep the larger qualification and release workflows close
to the tasks they configure:

| Script | Responsibility |
| --- | --- |
| `verification/support.gradle` | Sealed fixtures, Java 8 verification, Forge-run delegation, runtime log audits. |
| `verification/legacy-and-migration.gradle` | Surface and migration worlds, including both Mineralogy lineages. |
| `verification/client-and-benchmark.gradle` | Client integration and opt-in worldgen benchmark. |
| `verification/packaged-forge.gradle` | Fresh/reload test of the reobfuscated jar under installed Forge 14. |
| `release/artifacts.gradle` | API and release jars, release configuration and artifact audits, checksums. |
| `release/publishing.gradle` | Maven publication, coordinates and credential checks. |
| `ide/eclipse.gradle` | Eclipse resources, integration launches, Buildship and production classpath verification. |

The root applies these scripts in dependency order. `oreSpawnVerification` is
the small helper map shared by verification scripts; `oreSpawnBuild` and
`oreSpawnRelease` expose only values needed across the root, release and IDE
scripts. Keep new tasks in their owning script and keep their public task names
stable for CI and documented commands.

The routine offline gate runs `clean check build javadoc verifyReleaseArtifacts writeReleaseChecksums`.
Run `clientIntegrationProcess` separately for rendered client and editor coverage,
and `genEclipseRuns verifyEclipseProductionClasspath` for ordinary production-only
IDE launches.
`benchmarkIntegrationProcess` is an opt-in performance gate and requires
`benchmarkRunDir`. The packaged server gate requires `packagedMinecraftServerJar` and
`packagedForgeLibrariesRoot`, and its server command ends with literal `nogui`.
All gates support the existing `--offline --no-daemon` workflow.

The ordinary tests cover the OS3 bridge and legacy Codec linkage. Published
OS3 3.2.2 and 3.3.1 consumer binaries are also checked in separate packaged
Forge runs with the sealed 1.12 fixtures. Those checks use the original
target-native binaries, not the removed 1.10 fixture source layout.
