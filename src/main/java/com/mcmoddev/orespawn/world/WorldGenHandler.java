package com.mcmoddev.orespawn.world;

import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.utils.Helpers;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.WorldGenRegistries;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.IFeatureConfig;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid= Constants.MODID)
public class WorldGenHandler {

/*
	@SubscribeEvent
	public static void onBiomesLoaded(BiomeLoadingEvent ev) {
	}
*/
	public static void doFeatureRegistration() {

	}

	private static <FC extends IFeatureConfig> ConfiguredFeature<FC, ?> register(String key, ConfiguredFeature<FC, ?> configuredFeature) {
		return Registry.register(WorldGenRegistries.CONFIGURED_FEATURE, Helpers.makeInternalResourceLocation(key), configuredFeature);
	}
}
