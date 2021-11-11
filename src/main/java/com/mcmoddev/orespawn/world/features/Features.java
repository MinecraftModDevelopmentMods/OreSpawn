package com.mcmoddev.orespawn.world.features;

import com.mcmoddev.orespawn.world.gen.configs.DefaultFeatureConfig;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;

public class Features<FC extends IFeatureConfig> extends net.minecraftforge.registries.ForgeRegistryEntry<Feature<?>> {
	public static final DefaultFeature DEFAULT = new DefaultFeature(DefaultFeatureConfig.CODEC);
}
