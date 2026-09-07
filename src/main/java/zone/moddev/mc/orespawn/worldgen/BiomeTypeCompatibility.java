package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Resolves Forge 1.16 biome-dictionary entries through stable keys while a
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

	static Set<RegistryKey<Biome>> biomeKeys(String type) {
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

		Set<RegistryKey<Biome>> result = new LinkedHashSet<>();
		for (Map.Entry<RegistryKey<Biome>, Biome> entry : registry.entrySet()) {
			if (BiomeDictionary.hasType(entry.getKey(), dictionaryType)) {
				result.add(entry.getKey());
			}
		}
		return result;
	}

	static Set<ResourceLocation> biomeIds(String type) {
		Set<ResourceLocation> result = new LinkedHashSet<>();
		for (RegistryKey<Biome> key : biomeKeys(type)) {
			result.add(key.location());
		}
		return result;
	}

	static boolean hasType(RegistryKey<Biome> key, String type) {
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
		return ForgeRegistries.BIOMES.getValue(id);
	}
}
