# Migration

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
When legacy OreSpawn rules are translated, a concise player-facing summary is
also written atomically to `config/orespawn-upgrade-report.txt`; the existing
detailed rule report remains at `config/orespawn-migration/migration-report.txt`.

## Existing Mineralogy Worlds

An already-generated world without an OreSpawn per-world profile is inspected
before OreSpawn chooses any installed-pack or Create World default. OreSpawn
uses saved mod metadata from `level.dat`, with a valid `level.dat_old` as a
fallback, to distinguish these published contracts:

- Mineralogy 1.10.2 `3.3.8.26` and its `mineralogy.cfg`;
- Mineralogy 1.12.2 `3.8.0.53` and its distinct `mineralogy.cfg`;
- Mineralogy 5.0.1 through 5.4.0 and `mineralogy-common.toml`.

The resulting world profile preserves enablement, selected legacy/geome
engine, geome size, layer noise and thickness, realistic-coal behavior where
supported, exact effective rock order (including historical duplicates), and
all six white/blacklists. Saved-world identity chooses the lineage even when a
different stale config is present. Missing or malformed values use that
lineage's published defaults and are reported rather than silently broadening
the world configuration.

The human-readable result is written atomically to
`<world>/serverconfig/orespawn-upgrade-report.txt`. It identifies the saved
version and metadata source, config source, selected engine and lineage,
effective settings and outputs, missing IDs, fallbacks, and warnings. Source
configuration and existing chunks are not rewritten. Once
`orespawn-worldgen.json` exists it is authoritative and the import is not run
again. A fresh world containing stale legacy files remains on its explicit
OreSpawn/Create World settings.

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

On Minecraft 1.13.2, revision 10 converts only untouched managed-ore signatures
to the target's native height range, frequency, quantity, pattern, and exposure
behavior. It also restores the separate Badlands gold rule. Hand-edited rules
and explicitly stored Custom values are preserved.
