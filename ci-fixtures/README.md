# Sealed CI fixtures

These files are immutable OreSpawn compatibility-test inputs. They make the
OS1/OS3 ABI and Sylvester migration gates reproducible in a clean GitHub Actions
checkout without adding historical mods to the production, ordinary Gradle, or
Eclipse runtime classpaths.

- `artifacts/OreSpawn_1.10.2-1.1.0.jar` is the published OS1 ABI oracle.
- `artifacts/OreSpawn-1.10.2-3.2.2.104.jar` is the published OS3 ABI oracle.
- The Base Metals and Mineralogy jars plus the sealed world archive form the
  existing Sylvester upgrade corpus.

`verifyLegacyFixtures` checks every SHA-256 before a fixture is compiled against
or copied into an isolated test run. Release-archive audits reject these files
and their probe classes from all public and development OreSpawn jars.
