# OreSpawn 4 Documentation

OreSpawn is a required Forge mod and a declarative world-generation engine.
The normal jar is both the compile-time and runtime dependency; there is no
shaded or embeddable engine artifact.

Choose the guide that matches what you are doing:

- [Player and server guide](PLAYER_GUIDE.md)
- [Developer quick start and complete integration map](DEVELOPER_GUIDE.md)
- [Configuration field reference](CONFIGURATION.md)
- [Provider JSON files](PROVIDERS.md)
- [Java API and custom patterns](API.md)
- [Ore patterns and runtime tools](FEATURES.md)
- [Templates](TEMPLATES.md)
- [Dimensions](DIMENSIONS.md)
- [Migration](MIGRATION.md)
- [Troubleshooting](TROUBLESHOOTING.md)
- [Compact instructions for coding agents](AGENTS.md)

Validated examples are in `examples/`; JSON Schemas are in `schemas/`.
The provider and migration guides include OS3-compatible ranged quantities and
the `orespawn:all_except_nether_end` dimension selector.

On first load OreSpawn copies this bundle to `config/orespawn-guide/`. Existing
exported files are never overwritten. Delete an exported file if you want the
current jar to restore that file on the next launch.
