package zone.moddev.mc.orespawn;

import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager;
import zone.moddev.mc.orespawn.init.OreSpawnPatterns;
import zone.moddev.mc.orespawn.worldgen.OreSpawnOreGeneration;
import zone.moddev.mc.orespawn.worldgen.GeomeConfig;
import zone.moddev.mc.orespawn.worldgen.GeomeDistributionSampler;
import zone.moddev.mc.orespawn.worldgen.FluidDepositFeature;
import zone.moddev.mc.orespawn.worldgen.StoneReplacer;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;
import zone.moddev.mc.orespawn.worldgen.FormationSettings.Preset;
import zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;
import zone.moddev.mc.orespawn.worldgen.WorldgenBenchmark;
import zone.moddev.mc.orespawn.worldgen.FlatBedrockFeature;
import zone.moddev.mc.orespawn.worldgen.OreRetrogenManager;
import zone.moddev.mc.orespawn.worldgen.BiomeSurfaceFeature;
import zone.moddev.mc.orespawn.worldgen.BiomeWorldgenBootstrap;
import zone.moddev.mc.orespawn.worldgen.WorldMaterialWeather;
import zone.moddev.mc.orespawn.worldgen.OreSpawnWorldGenerator;
import zone.moddev.mc.orespawn.commands.OreSpawnCommands;
import zone.moddev.mc.orespawn.documentation.DocumentationExporter;
import com.mcmoddev.orespawn.compat.LegacyOs3Bridge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = OreSpawn.MODID, name = OreSpawn.NAME, version = OreSpawn.VERSION,
		acceptedMinecraftVersions = "[1.12.2]")
public class OreSpawn {
	@Mod.Instance(OreSpawn.MODID)
	public static OreSpawn instance;

	public static final String MODID = "orespawn";
	public static final String NAME = "OreSpawn";
	public static final String VERSION = "4.0.7.112021";

	private static final Logger LOGGER = LogManager.getLogger();

	private static String getVersion() {
		Package metadata = OreSpawn.class.getPackage();
		String version = metadata == null ? null : metadata.getImplementationVersion();
		return version == null ? "DEV" : version;
	}

	public OreSpawn() {
		instance = this;
	}

	@EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		OreSpawnConfig.load(event.getSuggestedConfigurationFile());
		OreSpawnPatterns.register();
		LegacyOs3Bridge.initialize(event);
		MinecraftForge.EVENT_BUS.register(RuntimeEvents.INSTANCE);
		// Forge 1.12 posts DecorateBiomeEvent.Pre on EVENT_BUS even though the
		// event's own documentation names TERRAIN_GEN_BUS. Register the
		// deduplicated coordinator on both native buses so the early surface and
		// geology pass runs before ores, structures, and vegetation.
		MinecraftForge.EVENT_BUS.register(OreSpawnWorldGenerator.INSTANCE);
		MinecraftForge.ORE_GEN_BUS.register(OreSpawnWorldGenerator.INSTANCE);
		MinecraftForge.TERRAIN_GEN_BUS.register(OreSpawnWorldGenerator.INSTANCE);
		GameRegistry.registerWorldGenerator(OreSpawnWorldGenerator.INSTANCE, 0);
		if (event.getSide().isClient()) zone.moddev.mc.orespawn.client.ClientSetup.initialize();
	}

	@EventHandler
	public void init(FMLInitializationEvent event) {
		WorldgenIntegrationManager.initialize();
		GeomeConfig.bake();
		logGeomeSampler();
		DocumentationExporter.exportBundledGuide();
		StoneReplacer.registerConfiguredFeature();
		OreSpawnOreGeneration.registerConfiguredFeatures();
		FluidDepositFeature.registerConfiguredFeature();
		FlatBedrockFeature.registerConfiguredFeature();
		BiomeSurfaceFeature.registerConfiguredFeature();
	}

	@EventHandler
	public void processInterMod(FMLInterModComms.IMCEvent event) {
		WorldgenIntegrationManager.processImcMessages();
	}

	@EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		WorldgenIntegrationManager.processImcMessages();
		WorldgenIntegrationManager.freeze();
		GeomeConfig.bake();
		refreshGenerationConfig();
		WorldgenIntegrationManager.markFeatureReady();
	}

	@EventHandler
	public void loadComplete(final FMLLoadCompleteEvent event) {
		WorldgenIntegrationManager.freeze();
		GeomeConfig.bake();
		refreshGenerationConfig();
		WorldgenIntegrationManager.markFeatureReady();
	}

	@EventHandler
	public void serverAboutToStart(FMLServerAboutToStartEvent event) {
		WorldGeologyProfileManager.onServerAboutToStart(event);
		WorldgenBenchmark.onServerAboutToStart(event);
	}

	@EventHandler
	public void serverStarting(FMLServerStartingEvent event) {
		OreSpawnCommands.register(event);
		WorldgenBenchmark.onServerStarted(event);
	}

	@EventHandler
	public void serverStopped(FMLServerStoppedEvent event) {
		WorldGeologyProfileManager.onServerStopped(event);
		OreSpawnWorldGenerator.INSTANCE.clear();
	}

	private static void refreshGenerationConfig() {
		StoneReplacer.refreshWorldConfig();
		OreSpawnOreGeneration.refreshWorldConfig();
		FluidDepositFeature.refreshWorldConfig();
		FlatBedrockFeature.refreshWorldConfig();
		OreRetrogenManager.refreshWorldConfig();
	}

	private static void logGeomeSampler() {
		if (!Boolean.getBoolean("orespawn.geomeSampler")) {
			return;
		}

		WorldGeologyProfile original = GeomeConfig.globalProfile();
		String defaultSeed = Long.toString(Long.getLong("orespawn.geomeSamplerSeed", 19780401L));
		String[] samplerSeeds = System.getProperty("orespawn.geomeSamplerSeeds", defaultSeed).split(",");
		String profileFilter = System.getProperty("orespawn.geomeSamplerProfiles", "all");
		boolean includeBiomeAudit = Boolean.parseBoolean(
				System.getProperty("orespawn.geomeSamplerBiomeAudit", "true"));
		try {
			for (String seedText : samplerSeeds) {
				long samplerSeed = Long.parseLong(seedText.trim());
				for (Preset preset : new Preset[] {
						Preset.TINY, Preset.SMALL, Preset.AVERAGE, Preset.LARGE, Preset.HUGE }) {
					if (!samplerProfileEnabled(profileFilter, preset.configName())) {
						continue;
					}
					WorldGeologyProfile profile = original
							.withSelection(GeologyMode.GEOME, preset, preset, preset, preset, preset,
									original.placeFluidDeposits());
					logSamplerProfile("Sky " + preset.configName(), samplerSeed, profile, includeBiomeAudit);
				}
				if (samplerProfileEnabled(profileFilter, "mixed_huge")) {
					WorldGeologyProfile mixedHuge = original
							.withSelection(GeologyMode.GEOME, Preset.AVERAGE, Preset.HUGE, Preset.HUGE,
									Preset.HUGE, Preset.HUGE, original.placeFluidDeposits());
					logSamplerProfile("Sky mixed-huge", samplerSeed, mixedHuge, includeBiomeAudit);
				}
			}
		} finally {
			GeomeConfig.applyWorldProfile(original);
		}
	}

	private static boolean samplerProfileEnabled(String filter, String profile) {
		if ("all".equalsIgnoreCase(filter.trim())) {
			return true;
		}
		for (String configured : filter.split(",")) {
			if (profile.equalsIgnoreCase(configured.trim())) {
				return true;
			}
		}
		return false;
	}

	private static void logSamplerProfile(String label, long seed, WorldGeologyProfile profile,
			boolean includeBiomeAudit) {
		GeomeConfig.applyWorldProfile(profile);
		String terrainSample = System.getProperty("orespawn.geomeSamplerTerrain");
		if (terrainSample == null || terrainSample.trim().isEmpty()) {
			LOGGER.info("\n{} sampler\n{}", label,
					GeomeDistributionSampler.sample(seed, ForgeRegistries.BIOMES.getValues(), 8, 8,
							includeBiomeAudit));
			return;
		}
		try {
			LOGGER.info("\n{} sampler\n{}", label,
					GeomeDistributionSampler.sampleTerrain(seed, java.nio.file.Paths.get(terrainSample)));
		} catch (java.io.IOException e) {
			LOGGER.error("Could not replay OreSpawn terrain sample '{}'", terrainSample, e);
		}
	}

	private static final class RuntimeEvents {
		static final RuntimeEvents INSTANCE = new RuntimeEvents();

		@SubscribeEvent public void worldLoad(WorldEvent.Load event) {
			WorldGeologyProfileManager.onWorldLoad(event);
		}
		@SubscribeEvent public void chunkLoad(ChunkEvent.Load event) {
			WorldMaterialWeather.onChunkLoad(event);
		}
		@SubscribeEvent public void worldTick(TickEvent.WorldTickEvent event) {
			WorldMaterialWeather.onWorldTick(event);
		}
		@SubscribeEvent public void retrogenLoad(ChunkDataEvent.Load event) {
			OreRetrogenManager.onChunkLoad(event);
		}
		@SubscribeEvent public void retrogenSave(ChunkDataEvent.Save event) {
			OreRetrogenManager.onChunkSave(event);
		}
		@SubscribeEvent public void serverTick(TickEvent.ServerTickEvent event) {
			OreRetrogenManager.onServerTick(event);
		}
	}

}
