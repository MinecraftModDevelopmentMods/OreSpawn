package cyano.orespawn;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.mcmoddev.orespawn.compat.LegacyOs3Bridge;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Deprecated OreSpawn 1.x compatibility facade. OS4 remains the only loaded
 * mod and the only world-generation scheduler.
 */
@Deprecated
public class OreSpawn {
	public static OreSpawn INSTANCE;
	public static final String MODID = "orespawn";
	public static final String NAME = "OreSpawn";
	public static final String VERSION = "4.0.8.110021";
	public static final List<Path> oreSpawnConfigFiles = new ArrayList<>();
	public static final List<String> additionalStoneBlocks = new ArrayList<>();
	public static boolean disableVanillaOreGen;
	public static boolean forceOreGen;
	public static boolean ignoreNonExistant;
	public static Path oreSpawnFolder;

	public OreSpawn() { INSTANCE = this; }

	public void preInit(FMLPreInitializationEvent event) {
		oreSpawnFolder = event.getModConfigurationDirectory().toPath().resolve("orespawn");
		LegacyOs3Bridge.initialize(event);
	}

	public void init(FMLInitializationEvent event) {
		for (Path config : new ArrayList<>(oreSpawnConfigFiles)) {
			try { LegacyOs3Bridge.registerOs1Config(config); }
			catch (java.io.IOException failure) { throw new IllegalStateException("Could not import " + config, failure); }
		}
		LegacyOs3Bridge.completeInitialization();
	}

	public void postInit(FMLPostInitializationEvent event) { }
}
