# Migration

Migration is non-destructive. OreSpawn writes `config/orespawn-worldgen.json`
only when that target does not already exist and retains every source file.

When `config/mineralogy-geomes.json` exists, OreSpawn imports the Mineralogy 6
profile directly, updates its schema marker, and records `migrated_from`.

Otherwise it scans `config/orespawn3/*.json` and `config/orespawn/*.json` for
legacy OreSpawn `version: "2.0"` spawn files. It converts:

- default, vein, normal cloud, precision, cluster, and under-fluid patterns;
- weighted output blocks and replacement blocks;
- numeric Overworld, Nether, and End dimensions;
- biome ID and biome-dictionary include/exclude rules;
- frequency, size, height, spread, node, fluid, and retrogen settings.

Compatible flags are also read from `config/orespawn.cfg`: vanilla/all ore
replacement, retrogen, forced retrogen, flat bedrock, and bedrock thickness.
Unknown numeric dimensions and obsolete block states are reported instead of
guessed. Review `config/orespawn-migration/migration-report.txt` after import.

Global schemas 1-3 and world schemas 1-2 are upgraded in memory and persisted
where safe. A schema-1 world held only mode/oil/formation choices and is
overlaid on the effective installed-pack profile. A schema-2 world is already
a full snapshot and preserves its geology. Existing terrain is not rewritten.

Unqualified built-in geome names remain accepted and normalize to
`orespawn:<name>` internally.
