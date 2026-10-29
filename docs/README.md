# OreSpawn 4 Documentation

OreSpawn is a required NeoForge mod and a declarative world-generation engine.
The normal jar is both the compile-time and runtime dependency; there is no
shaded or embeddable engine artifact.

Start with the [developer guide](DEVELOPER_GUIDE.md) when integrating a mod,
or the [player and server guide](PLAYER_GUIDE.md) when configuring a world.
The API and provider guides give the exact Java and JSON contracts. Use the
versioning guide when choosing a matching Minecraft and loader build.

- [Player and server guide](PLAYER_GUIDE.md)
- [Developer quick start and complete integration map](DEVELOPER_GUIDE.md)
- [Configuration field reference](CONFIGURATION.md)
- [Provider JSON files](PROVIDERS.md)
- [Java API and custom patterns](API.md)
- [Ore patterns and runtime tools](FEATURES.md)
- [Templates](TEMPLATES.md)
- [Biomes and world materials](BIOMES.md)
- [Dimensions](DIMENSIONS.md)
- [Migration](MIGRATION.md)
- [Troubleshooting](TROUBLESHOOTING.md)
- [Versioning and release conventions](VERSIONS.md)

Validated examples are in `examples/`; JSON Schemas are in `schemas/`.
The provider and migration guides include OS3-compatible ranged quantities and
the `orespawn:all_except_nether_end` dimension selector.

On first load OreSpawn copies this bundle to `config/orespawn-guide/`. Existing
exported files are never overwritten. Delete an exported file if you want the
current jar to restore that file on the next launch.
