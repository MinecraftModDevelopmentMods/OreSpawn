# MMD OreSpawn

OreSpawn 4 is a provider-driven Minecraft 1.18.2 world-generation engine. It
can place ores using six built-in patterns, build configurable geological
strata and geomes, flatten bedrock, and perform bounded ore retrogen. Installed
mods provide blocks and declarative rules; OreSpawn keeps registry and config
work out of chunk-generation loops.

This is not the unrelated mod that adds mobs and dimensions under the same
name.

OreSpawn is intentionally passive when installed alone. Terrain replacement,
ore suppression, retrogen, and flat bedrock are opt-in. Mineralogy 6 is the
first full provider and supplies its rocks, ores, oil, geomes, and defaults in
`data/mineralogy/orespawn/provider.json`.

## Integration

- Global pack profile: `config/orespawn-worldgen.json`
- Per-world snapshot: `<world>/serverconfig/orespawn-worldgen.json`
- Pack provider override: `config/<modid>-orespawn.json`
- Packaged provider: `data/<modid>/orespawn/provider.json`
- Supported Java API: `com.mcmoddev.orespawn.api`, API major `1`

Documentation, schemas, and examples are in [`docs`](docs/README.md). The same
material is packaged under `META-INF/orespawn/docs` in the normal jar, with an
agent-oriented entry point at jar-root `AGENTS.md`.

## Building

Use Java 17 and run from this checkout:

```powershell
.\gradlew.bat test build --no-daemon
.\gradlew.bat genEclipseRuns eclipse --no-daemon
```

OreSpawn is licensed under LGPL-2.1.
