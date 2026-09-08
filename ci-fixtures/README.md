# Forge 1.11.2 CI fixtures

These exact published Mineralogy engines and the sealed generated OS3 world
make the Forge 1.11.2 migration and parity checks self-contained on hosted CI.
They are test inputs only and must never enter an OreSpawn release artifact or
an ordinary Eclipse launch.

- `Mineralogy-1.10.2-3.3.8.26.jar` is the carried 1.10 Cyano-engine oracle.
- `Mineralogy-1.11.2-3.3.0.jar` is the native 1.11 Cyano-engine oracle rebuilt
  from exact Mineralogy source commit
  `727fec4c8fd9d874bf480c74ccd868804021137b`.
- `Mineralogy-1.12.2-3.8.0.53.jar` is the native 1.12 Cyano-engine oracle.
- `OreSpawn-1.11.2-3.2.2.jar` is the target-native OS3 ABI fixture rebuilt
  from dormant-branch commit
  `67ea7aebe766f00c1b7fe46488a5e0ab31e5cc6c`.
- `OreSpawn_1.10.2-1.1.0.jar` is the inherited published OS1 ABI fixture.
- `os3-331-default-source.zip` is the immutable generated-world source used by
  the legacy-lineage fresh/reload gates.

Both 1.11 jars were built with their historical Gradle 4.9 wrappers and exact
Temurin 8.0.502+7. The source trees were detached at the commits above. Only
obsolete CurseGradle/Sonar configuration was removed from the disposable
build scripts; no production source or resource was changed. The resulting
ordinary `jar` output was copied here, hashed, and is loaded only by isolated
test class loaders or explicit compatibility tasks.

`SHA256SUMS` is authoritative. The Gradle build verifies every hash before
compiling tests or starting a migration runtime.
