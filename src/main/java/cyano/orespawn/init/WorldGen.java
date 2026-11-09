package cyano.orespawn.init;

import java.io.IOException;
import java.nio.file.Path;

import com.mcmoddev.orespawn.compat.LegacyOs3Bridge;

import cyano.orespawn.worldgen.OreSpawnData;
import cyano.orespawn.worldgen.OreSpawner;

/** Deprecated OreSpawn 1.x programmatic registration facade. */
@Deprecated
public abstract class WorldGen {
	public static void loadConfig(Path path) throws IOException { LegacyOs3Bridge.registerOs1Config(path); }
	public static void init() { LegacyOs3Bridge.completeInitialization(); }
	public static void addOreSpawner(OreSpawnData spawn, Integer dimension, long hash) {
		new OreSpawner(spawn, dimension, hash);
	}
}
