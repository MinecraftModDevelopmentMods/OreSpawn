package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Resolves Forge 1.17 biome-dictionary entries through stable keys while a
 * server-owned dynamic biome registry is active.
 */
final class BiomeTypeCompatibility {
	private static volatile Registry<Biome> activeRegistry;

	private BiomeTypeCompatibility() {
	}

	static void useRegistry(Registry<Biome> registry) {
		activeRegistry = registry;
	}

	static void clearRegistry() {
		activeRegistry = null;
	}

	static Set<ResourceKey<Biome>> biomeKeys(String type) {
		BiomeDictionary.Type dictionaryType;
		try {
			dictionaryType = BiomeDictionary.Type.getType(type);
		} catch (RuntimeException ignored) {
			return Collections.emptySet();
		}

		Registry<Biome> registry = activeRegistry;
		if (registry == null) {
			return new LinkedHashSet<>(BiomeDictionary.getBiomes(dictionaryType));
		}

		Set<ResourceKey<Biome>> result = new LinkedHashSet<>();
		for (Map.Entry<ResourceKey<Biome>, Biome> entry : registry.entrySet()) {
			if (BiomeDictionary.hasType(entry.getKey(), dictionaryType)) {
				result.add(entry.getKey());
			}
		}
		return result;
	}

	static Set<ResourceLocation> biomeIds(String type) {
		Set<ResourceLocation> result = new LinkedHashSet<>();
		for (ResourceKey<Biome> key : biomeKeys(type)) {
			result.add(key.location());
		}
		return result;
	}

	static boolean hasType(ResourceKey<Biome> key, String type) {
		try {
			return BiomeDictionary.hasType(key, BiomeDictionary.Type.getType(type));
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	static Biome biome(ResourceLocation id) {
		Registry<Biome> registry = activeRegistry;
		if (registry != null) {
			return registry.get(id);
		}
		try {
			Bootstrap.checkBootstrapCalled(() -> "OreSpawn biome lookup");
		} catch (IllegalArgumentException ignored) {
			return null;
		}
		return ForgeRegistries.BIOMES.getValue(id);
	}
}
