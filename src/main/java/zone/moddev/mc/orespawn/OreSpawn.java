package zone.moddev.mc.orespawn;

import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager;
import zone.moddev.mc.orespawn.init.OreSpawnPatterns;
import zone.moddev.mc.orespawn.init.Features;
import zone.moddev.mc.orespawn.init.BiomeModifiers;
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
import zone.moddev.mc.orespawn.commands.OreSpawnCommands;
import zone.moddev.mc.orespawn.documentation.DocumentationExporter;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;
import net.minecraft.core.registries.BuiltInRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(OreSpawn.MODID)
public class OreSpawn {
	public static OreSpawn instance;

	public static final String MODID = "orespawn";
	public static final String NAME = "OreSpawn";
	public static final String VERSION = getVersion();

	private static final Logger LOGGER = LogManager.getLogger();

	private static String getVersion() {
		Package metadata = OreSpawn.class.getPackage();
		String version = metadata == null ? null : metadata.getImplementationVersion();
		return version == null ? "DEV" : version;
	}

	public OreSpawn(IEventBus modBus, ModContainer modContainer) {
		instance = this;
		OreSpawnConfig.register(modContainer);
		OreSpawnPatterns.register(modBus);
		Features.register(modBus);
		BiomeModifiers.register(modBus);
		modBus.addListener(BiomeWorldgenBootstrap::registerCodecs);

		modBus.addListener(this::setup);
		modBus.addListener(this::enqueueInterMod);
		modBus.addListener(this::processInterMod);
		modBus.addListener(this::loadComplete);
		NeoForge.EVENT_BUS.addListener(WorldMaterialWeather::onChunkLoad);
		NeoForge.EVENT_BUS.addListener(WorldMaterialWeather::onWorldTick);
		NeoForge.EVENT_BUS.addListener(WorldGeologyProfileManager::onServerAboutToStart);
		NeoForge.EVENT_BUS.addListener(WorldGeologyProfileManager::onWorldLoad);
		NeoForge.EVENT_BUS.addListener(WorldGeologyProfileManager::onServerStopped);
		NeoForge.EVENT_BUS.addListener(OreRetrogenManager::onChunkLoad);
		NeoForge.EVENT_BUS.addListener(OreRetrogenManager::onChunkSave);
		NeoForge.EVENT_BUS.addListener(OreRetrogenManager::onServerTick);
		NeoForge.EVENT_BUS.addListener(OreSpawnCommands::register);
		WorldgenBenchmark.register();
	}

	private void loadComplete(final FMLLoadCompleteEvent event) {
		event.enqueueWork(() -> {
			WorldgenIntegrationManager.freeze();
			GeomeConfig.bake();
			OreSpawnOreGeneration.refreshWorldConfig();
			FlatBedrockFeature.refreshWorldConfig();
			OreRetrogenManager.refreshWorldConfig();
			WorldgenIntegrationManager.markFeatureReady();
		});
	}

	private void enqueueInterMod(final InterModEnqueueEvent event) {
		// Provider mods submit WorldgenProvider values from their own enqueue event.
	}

	private void processInterMod(final InterModProcessEvent event) {
		// Files are authoritative, so scan them before accepting API submissions.
		WorldgenIntegrationManager.initialize();
		WorldgenIntegrationManager.processImcMessages();
		GeomeConfig.bake();
		OreSpawnOreGeneration.refreshWorldConfig();
		FlatBedrockFeature.refreshWorldConfig();
		OreRetrogenManager.refreshWorldConfig();
	}

	private void setup(final FMLCommonSetupEvent event) {
		OreSpawnConfig.bake();
		WorldgenIntegrationManager.initialize();
		GeomeConfig.bake();
		event.enqueueWork(() -> {
			DocumentationExporter.exportBundledGuide();
			StoneReplacer.registerConfiguredFeature();
			OreSpawnOreGeneration.registerConfiguredFeatures();
			FluidDepositFeature.registerConfiguredFeature();
			FlatBedrockFeature.registerConfiguredFeature();
			BiomeSurfaceFeature.registerConfiguredFeature();
		});
	}

	public static void runGeomeSamplerIfRequested() {
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
					GeomeDistributionSampler.sample(seed, zone.moddev.mc.orespawn.worldgen.BiomeRegistryAccess.values(), 8, 8,
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

}
