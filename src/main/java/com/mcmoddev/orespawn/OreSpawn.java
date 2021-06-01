package com.mcmoddev.orespawn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mcmoddev.orespawn.api.os3.OS3API;
import com.mcmoddev.orespawn.api.plugin.PluginLoader;
import com.mcmoddev.orespawn.commands.AddOreCommand;
import com.mcmoddev.orespawn.commands.ClearChunkCommand;
import com.mcmoddev.orespawn.commands.DumpBiomesCommand;
import com.mcmoddev.orespawn.commands.WriteConfigsCommand;
import com.mcmoddev.orespawn.data.Config;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.data.FeatureRegistry;
import com.mcmoddev.orespawn.impl.os3.OS3APIImpl;
import com.mcmoddev.orespawn.worldgen.FlatBedrock;
import com.mcmoddev.orespawn.worldgen.OreSpawnFeatureGenerator;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLFingerprintViolationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * Main entry point for the mod, everything runs through this.
 *
 * @author DShadowWolf &lt;dshadowwolf@gmail.com&gt;
 */

@Mod(modid = Constants.MODID,
		name = Constants.NAME,
		version = Constants.VERSION,
		acceptedMinecraftVersions = "[1.12,)",
		certificateFingerprint = "@FINGERPRINT@")

public class OreSpawn {

	@Instance
	public static OreSpawn instance;

	public static final Logger LOGGER = LogManager.getLogger(Constants.MODID);
	public static final OS3API API = new OS3APIImpl();
	static final EventHandlers eventHandlers = new EventHandlers();
	public static final FeatureRegistry FEATURES = new FeatureRegistry();

	static final FlatBedrock flatBedrock = new FlatBedrock();
	
	@EventHandler
	public void onFingerprintViolation(final FMLFingerprintViolationEvent event) {
		LOGGER.warn("Invalid fingerprint detected!");
	}

	@EventHandler
	public void preInit(final FMLPreInitializationEvent ev) {
		Config.loadConfig();

		PluginLoader.INSTANCE.load(ev);

		if (Config.getBoolean(Constants.FLAT_BEDROCK)) {
			GameRegistry.registerWorldGenerator(flatBedrock, 100);
		}
		
		if (Config.getBoolean(Constants.RETROGEN_KEY)
				|| Config.getBoolean(Constants.REPLACE_VANILLA_OREGEN)
				|| Config.getBoolean(Constants.RETRO_BEDROCK)) {
			MinecraftForge.EVENT_BUS.register(eventHandlers);
			MinecraftForge.ORE_GEN_BUS.register(eventHandlers);
		}
	}

	@EventHandler
	public void init(final FMLInitializationEvent ev) {
		PluginLoader.INSTANCE.register();

		API.loadConfigFiles();
	}

	@EventHandler
	public void postInit(final FMLPostInitializationEvent ev) {
		Config.saveConfig();
		API.getAllSpawns().entrySet().stream()
		           .forEach(ent -> {
		        	   GameRegistry.registerWorldGenerator(new OreSpawnFeatureGenerator(ent.getValue(), ent.getKey()), 100);
		           });
	}

	@EventHandler
	public void onServerStarting(final FMLServerStartingEvent ev) {
		ev.registerServerCommand(new ClearChunkCommand());
		ev.registerServerCommand(new DumpBiomesCommand());
		ev.registerServerCommand(new AddOreCommand());
		ev.registerServerCommand(new WriteConfigsCommand());
	}
}
