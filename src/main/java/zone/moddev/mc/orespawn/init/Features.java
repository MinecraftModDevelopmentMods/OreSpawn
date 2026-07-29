package zone.moddev.mc.orespawn.init;

import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.worldgen.OreSpawnOreGeneration;
import zone.moddev.mc.orespawn.worldgen.FlatBedrockFeature;
import zone.moddev.mc.orespawn.worldgen.FluidDepositFeature;
import zone.moddev.mc.orespawn.worldgen.StoneReplacer;
import zone.moddev.mc.orespawn.worldgen.BiomeSurfaceFeature;
import zone.moddev.mc.orespawn.worldgen.VanillaOreFeatureGate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Features {
	private static final DeferredRegister<Feature<?>> FEATURES =
			DeferredRegister.create(BuiltInRegistries.FEATURE, OreSpawn.MODID);

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
