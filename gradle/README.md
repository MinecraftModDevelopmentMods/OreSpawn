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
| `verification/legacy-and-migration.gradle` | Surface and migration worlds, Mineralogy lineage, opt-in OS1/OS3 ABI probes. |
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
`legacyAbiIntegrationTest` and `benchmarkIntegrationProcess` remain opt-in
compatibility and performance gates. The benchmark requires `benchmarkRunDir`;
the packaged server gate requires `packagedMinecraftServerJar` and
`packagedForgeLibrariesRoot`, and its server command ends with literal `nogui`.
All gates support the existing `--offline --no-daemon` workflow.
