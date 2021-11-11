package com.mcmoddev.orespawn;

import com.mcmoddev.orespawn.utils.mixins.BiomeGenerationSettingsAccessor;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.BiomeGenerationSettings;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import com.mcmoddev.orespawn.world.features.Features;
import com.mcmoddev.orespawn.world.gen.configs.DefaultFeatureConfig;

@Mod("orespawn4")
public class OreSpawn {
	// Directly reference a log4j logger.
	public static final Logger LOGGER = LogManager.getFormatterLogger();

	public OreSpawn() {
		// Register the setup method for modloading
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
		MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this::start);
//		MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this::finalizeFeatureRegistration);
	}

	public void start(final FMLServerAboutToStartEvent event) {
		event.getServer().getDynamicRegistries().getRegistry(Registry.BIOME_KEY).stream().forEach(b -> {
			BiomeGenerationSettings settings = b.getGenerationSettings();
			List<List<Supplier<ConfiguredFeature<?, ?>>>> data = new LinkedList<>();
			data.addAll(settings.getFeatures());
			List<Supplier<ConfiguredFeature<?, ?>>> cc = new LinkedList<>();
			DefaultFeatureConfig.getMyFeatures().values().stream().forEach( cf -> cc.add(() -> cf));
			data.add(cc);
			settings.setFeatures(data);
		});
	}

	private void setup(final FMLCommonSetupEvent event) {
	}

	@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
	public static class RegistryEvents {
		@SubscribeEvent
		public static void featureRegistryEvent(final RegistryEvent.Register<Feature<?>> event) {
			event.getRegistry().register(Features.DEFAULT.setRegistryName("default"));
			LOGGER.info("Registered %s", Features.DEFAULT.getRegistryName());
		}
	}
}
