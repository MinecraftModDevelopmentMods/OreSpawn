package zone.moddev.mc.orespawn.api;

import java.util.function.Supplier;

import zone.moddev.mc.orespawn.OreSpawn;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/** Stable entry point for mods that register codec-backed ore patterns. */
public final class OreSpawnPatternRegistry {
	public static final ResourceLocation REGISTRY_NAME =
			new ResourceLocation(OreSpawn.MODID, "ore_pattern_types");

	private OreSpawnPatternRegistry() {
	}

	public static Registry<OrePatternType> registry() {
		return zone.moddev.mc.orespawn.init.OreSpawnPatterns.registry();
	}

	public static Supplier<Registry<OrePatternType>> registrySupplier() {
		return zone.moddev.mc.orespawn.init.OreSpawnPatterns.registrySupplier();
	}
}
