# Optional Ore Sources checks

These fixtures are only for testing. Ordinary builds, Eclipse production
launches and published jars do not include them. Use fresh disposable game
directories, not a player's save or the normal development launch folder.

## Imported profiles in the editor

Build the separate editor fixture with:

```text
gradlew prepareOreSourceEditorFixture --init-script tools/qualification/editor/probe.init.gradle
```

Install the resulting `build/editor-fixtures/editor-fixtures.jar` beside the
matching OreSpawn jar in a disposable Forge 1.12.2 client. Its menu provides
four profiles: carried OS1-style settings, an OS3 import, an explicit custom
channel, and a fresh standard channel. This checks saved profile compatibility;
it does not add an OS1 loader to Minecraft 1.12.

Only the disposable profile files are saved. Normal configuration and worlds
are not opened. For an automated pass, add
`-Deditorfixtures.automated=true` to the Java arguments. The fixture checks the
real screen, source selection, Cancel, Done, management switches and file
reload, saves screenshots after rendering, and exits with `editor-results/PASS.txt`.
Repeat at scaled GUI sizes 426 by 265 and 320 by 240.

## Dedicated-server generation

Build the runtime probe with:

```text
gradlew prepareOreSourceQualification --init-script tools/qualification/ore-sources/probe.init.gradle
```

Run `python tools/qualification/ore-sources/qualify.py` with absolute paths for:

- `--java`: a Java 8 executable;
- `--runtime`: an official Forge 1.12.2-14.23.5.2859 server installation with
  its libraries, Minecraft server jar and previously accepted EULA;
- `--candidate`: the new reobfuscated OreSpawn jar;
- `--baseline`: the published 4.1.2.112021 jar;
- `--probe`: `build/ore-source-qualification/ore-source-probe.jar`;
- `--output`: a new disposable directory that does not already exist.

The script uses only Python's standard library. It runs hidden servers on
Windows, always ends server commands with literal `nogui`, and retains failures.
It checks ten profiles through fresh generation, exact reload, later generation
and a second reload. A small independent pattern supplies a fixed deposit while
ordinary iron uses the imported built-in pattern. Hybrid must reduce ordinary
iron; replacement must remove it; custom channels and the independent deposit
must remain active. It also retains a failing published control and proves an
additive published world survives the update without rewriting old terrain.
