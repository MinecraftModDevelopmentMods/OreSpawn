package com.mcmoddev.orespawn.init;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.worldgen.OreSpawnOreGeneration;
import com.mcmoddev.orespawn.worldgen.FlatBedrockFeature;
import com.mcmoddev.orespawn.worldgen.OilDepositFeature;
import com.mcmoddev.orespawn.worldgen.StoneReplacer;
import com.mcmoddev.orespawn.worldgen.VanillaOreFeatureGate;

import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OreSpawn.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Features {
	@SubscribeEvent
	public static void registerFeatures(RegistryEvent.Register<Feature<?>> event) {
		event.getRegistry().register(StoneReplacer.FEATURE);
		event.getRegistry().register(OreSpawnOreGeneration.FEATURE);
		event.getRegistry().register(OilDepositFeature.FEATURE);
		event.getRegistry().register(FlatBedrockFeature.FEATURE);
		VanillaOreFeatureGate.registerFeatures(event.getRegistry());
	}

	private Features() {
		throw new IllegalAccessError("Not an instantiable class");
	}
}
