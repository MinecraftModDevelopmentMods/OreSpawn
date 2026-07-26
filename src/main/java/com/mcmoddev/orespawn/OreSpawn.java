package com.mcmoddev.orespawn;

import com.mcmoddev.orespawn.integration.WorldgenIntegrationManager;
import com.mcmoddev.orespawn.init.OreSpawnPatterns;
import com.mcmoddev.orespawn.worldgen.OreSpawnOreGeneration;
import com.mcmoddev.orespawn.worldgen.GeomeConfig;
import com.mcmoddev.orespawn.worldgen.GeomeDistributionSampler;
import com.mcmoddev.orespawn.worldgen.FluidDepositFeature;
import com.mcmoddev.orespawn.worldgen.StoneReplacer;
import com.mcmoddev.orespawn.worldgen.WorldGeologyProfileManager;
import com.mcmoddev.orespawn.worldgen.FormationSettings.Preset;
import com.mcmoddev.orespawn.OreSpawnConfig.GeologyMode;
import com.mcmoddev.orespawn.worldgen.WorldGeologyProfile;
import com.mcmoddev.orespawn.worldgen.WorldgenBenchmark;
import com.mcmoddev.orespawn.worldgen.FlatBedrockFeature;
import com.mcmoddev.orespawn.worldgen.OreRetrogenManager;
import com.mcmoddev.orespawn.worldgen.BiomeSurfaceFeature;
import com.mcmoddev.orespawn.worldgen.BiomeWorldgenBootstrap;
import com.mcmoddev.orespawn.worldgen.WorldMaterialWeather;
import com.mcmoddev.orespawn.commands.OreSpawnCommands;
import com.mcmoddev.orespawn.documentation.DocumentationExporter;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;

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

	public OreSpawn() {
		instance = this;
		OreSpawnConfig.register();
		OreSpawnPatterns.register(FMLJavaModLoadingContext.get().getModEventBus());

		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueInterMod);
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processInterMod);
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::loadComplete);
		MinecraftForge.EVENT_BUS.addListener(StoneReplacer::onBiomeLoading);
		MinecraftForge.EVENT_BUS.addListener(OreSpawnOreGeneration::onBiomeLoading);
		MinecraftForge.EVENT_BUS.addListener(FluidDepositFeature::onBiomeLoading);
		MinecraftForge.EVENT_BUS.addListener(FlatBedrockFeature::onBiomeLoading);
		MinecraftForge.EVENT_BUS.addListener(BiomeSurfaceFeature::onBiomeLoading);
		MinecraftForge.EVENT_BUS.addListener(WorldMaterialWeather::onChunkLoad);
		MinecraftForge.EVENT_BUS.addListener(WorldMaterialWeather::onWorldTick);
		MinecraftForge.EVENT_BUS.addListener(WorldGeologyProfileManager::onServerAboutToStart);
		MinecraftForge.EVENT_BUS.addListener(WorldGeologyProfileManager::onWorldLoad);
		MinecraftForge.EVENT_BUS.addListener(WorldGeologyProfileManager::onServerStopped);
		MinecraftForge.EVENT_BUS.addListener(OreRetrogenManager::onChunkLoad);
		MinecraftForge.EVENT_BUS.addListener(OreRetrogenManager::onChunkSave);
		MinecraftForge.EVENT_BUS.addListener(OreRetrogenManager::onServerTick);
		MinecraftForge.EVENT_BUS.addListener(OreSpawnCommands::register);
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
		logGeomeSampler();
		event.enqueueWork(() -> {
			BiomeWorldgenBootstrap.registerCodecs();
			DocumentationExporter.exportBundledGuide();
			StoneReplacer.registerConfiguredFeature();
			OreSpawnOreGeneration.registerConfiguredFeatures();
			FluidDepositFeature.registerConfiguredFeature();
			FlatBedrockFeature.registerConfiguredFeature();
			BiomeSurfaceFeature.registerConfiguredFeature();
		});
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

}
