# Migration

## OreSpawn 3 Compatibility On Minecraft 1.12.2

This branch ships a deprecated compatibility bridge for existing OreSpawn 3
consumer jars. It preserves the compatible public descriptors from OreSpawn
3.2.2 and 3.3.1, including `com.mcmoddev.orespawn`, `OreSpawn.API`, plugin
annotations, builders, feature hooks, replacements, and programmatic
registration handles. Existing binaries are discovered without recompilation.

The bridge scans `@OreSpawnPlugin` metadata and packaged
`assets/<modid>/<resourcePath>` definitions, translates resource and Java
registrations into OreSpawn 4 providers, and schedules custom legacy features
through one compatibility coordinator. The original OreSpawn 3 generator is
never registered alongside its translated OreSpawn 4 rule.

Migration is atomic and idempotent. Source files are never edited. Before an
OreSpawn 4 target is written, OreSpawn retains a backup and writes through a
temporary file. The deterministic report records mappings, clamping,
unsupported entries, ambiguities, and any required user action. A clean second
startup does not rewrite the result or repeat registration, retrogen, or
generation.

Minecraft 1.12 metadata states are preserved as block ID plus metadata; the
migrator does not invent post-flattening block IDs. Legacy Base Metals rules
use stable provider identities when their outputs match uniquely. Mineralogy
3 replacement rocks are accepted as ore hosts, but Mineralogy remains the
authoritative geology engine when that legacy stack is installed.

## Provider-Aware OS3 Imports

When an OreSpawn 2/3 file is named for an installed provider, the migrator now
compares each converted primary output with that provider's ore declarations.
A unique output match is written under the provider's stable rule ID, allowing
provider merging to retain the migrated user values without adding a duplicate
default. Ambiguous and unmatched outputs keep their `orespawn:legacy/...` IDs
and are called out in `orespawn-migration/migration-report.txt` for manual
review. Files owned by mods without an active provider retain the original
legacy-ID behaviour.

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

The OS3 default generator is migrated precisely: `size - variation` through
`size + variation - 1` becomes an inclusive `min_quantity`/`max_quantity`
range, while `variation: 0` remains a fixed `quantity`. Old `maxHeight` was
exclusive, so it becomes modern inclusive `max_y: maxHeight - 1`; a missing
maximum uses the old default 256 and becomes 255. Empty legacy dimension lists
become `orespawn:all_except_nether_end`, preserving their meaning of every
ordinary dimension while excluding the vanilla Nether and End. Quantities are
clamped to the supported 1-64 budget with a migration-report warning, and empty
height ranges are skipped and reported. Variation for other legacy pattern
types retains the documented approximation used by the existing importer.

Compatible flags are also read from `config/orespawn.cfg`: vanilla/all ore
replacement, retrogen, forced retrogen, flat bedrock, and bedrock thickness.
Unknown numeric dimensions and obsolete block states are reported instead of
guessed. Review `config/orespawn-migration/migration-report.txt` after import.

Global schemas 1-5 and world schemas 1-4 are upgraded in memory and persisted
where safe. A schema-1 world held only mode/oil/formation choices and is
overlaid on the effective installed-pack profile. A schema-2 world is already
a full snapshot and preserves its geology. Existing terrain is not rewritten.

Provider schemas 1-3 remain readable. Schema 4 adds `biome_palettes`,
`dimension_materials`, and automatic fresh-world template metadata. Older
profiles receive empty biome/material sections, so migration cannot change
their terrain or biome output. Auto-selected templates are never applied to an
existing world profile during migration.

The old `place_crude_oil` and singleton `oil` fields migrate to
`place_fluid_deposits` and a provider-owned rule. A valid block becomes
`<namespace>:fluid_deposit/<path>`; `minecraft:air` creates no rule. Mineralogy
profiles converge on `mineralogy:fluid_deposit/crude_oil` without duplicates.
One-time backups are written before persisted global or world migration.

Unqualified built-in geome names remain accepted and normalize to
`orespawn:<name>` internally.

Managed vanilla-ore default revisions update only rules that still exactly
match an older OreSpawn default signature. Changed frequencies, ranges,
quantities, or patterns are treated as pack/player choices and are never
rewritten.

World profiles are backed up beside the active file before an ore-default
revision is written. Revision 8 folds temporary Mineralogy-owned vanilla ore
IDs into OreSpawn's canonical IDs, preserving edited rules and removing
duplicate placement. Revision 9 recognizes the later-port deep-biased defaults
so a profile copied from a newer OreSpawn branch can still be migrated safely.

On Minecraft 1.12.2, revision 10 converts only untouched managed-ore signatures
to the target's native height range, frequency, quantity, pattern, and exposure
behavior. It also restores the separate Badlands gold rule. Hand-edited rules
and explicitly stored Custom values are preserved.
