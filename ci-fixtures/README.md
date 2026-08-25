# Forge 1.12.2 CI fixtures

These exact published Mineralogy engines and the sealed generated OS3 world
make the Forge 1.12.2 migration and parity checks self-contained on hosted CI.
They are test inputs only and must never enter an OreSpawn release artifact or
an ordinary Eclipse launch.

- `Mineralogy-1.10.2-3.3.8.26.jar` is the carried 1.10 Cyano-engine oracle.
- `Mineralogy-1.12.2-3.8.0.53.jar` is the native 1.12 Cyano-engine oracle.
- `os3-331-default-source.zip` is the immutable generated-world source used by
  both legacy-lineage fresh/reload gates.

`SHA256SUMS` is authoritative. The Gradle build verifies every hash before
compiling tests or starting a migration runtime.
