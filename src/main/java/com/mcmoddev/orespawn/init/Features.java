package com.mcmoddev.orespawn.init;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.worldgen.OreSpawnOreGeneration;
import com.mcmoddev.orespawn.worldgen.FlatBedrockFeature;
import com.mcmoddev.orespawn.worldgen.FluidDepositFeature;
import com.mcmoddev.orespawn.worldgen.StoneReplacer;
import com.mcmoddev.orespawn.worldgen.BiomeSurfaceFeature;
import com.mcmoddev.orespawn.worldgen.VanillaOreFeatureGate;

import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class Features {
	private static final DeferredRegister<Feature<?>> FEATURES =
			DeferredRegister.create(ForgeRegistries.FEATURES, OreSpawn.MODID);

	static {
		FEATURES.register("stone_replacer", () -> StoneReplacer.FEATURE);
		FEATURES.register("matching_stone_gate", StoneReplacer::matchingStoneGateFeature);
		FEATURES.register("managed_ores", () -> OreSpawnOreGeneration.FEATURE);
		FEATURES.register("fluid_deposits", () -> FluidDepositFeature.FEATURE);
		FEATURES.register("flat_bedrock", () -> FlatBedrockFeature.FEATURE);
		FEATURES.register("biome_surfaces", () -> BiomeSurfaceFeature.FEATURE);
		VanillaOreFeatureGate.registerFeatures(FEATURES);
	}

	public static void register(IEventBus bus) {
		FEATURES.register(bus);
	}

	private Features() {
		throw new IllegalAccessError("Not an instantiable class");
	}
}
