package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Preserves OreSpawn's legacy biome-dictionary vocabulary while resolving
 * world-owned biomes through the active dynamic registry.
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
		Registry<Biome> registry = activeRegistry;
		if (registry != null) {
			Set<ResourceKey<Biome>> result = new LinkedHashSet<>();
			for (Map.Entry<ResourceKey<Biome>, Biome> entry : registry.entrySet()) {
				if (registry.getHolder(entry.getKey())
						.map(holder -> matches(holder, tags(type))).orElse(false)
						|| legacyHasType(entry.getKey(), type)) {
					result.add(entry.getKey());
				}
			}
			return result;
		}
		try {
			return new LinkedHashSet<>(BiomeDictionary.getBiomes(
					BiomeDictionary.Type.getType(type)));
		} catch (RuntimeException ignored) {
			return Collections.emptySet();
		}
	}

	static Set<ResourceLocation> biomeIds(String type) {
		Set<ResourceLocation> result = new LinkedHashSet<>();
		for (ResourceKey<Biome> key : biomeKeys(type)) result.add(key.location());
		return result;
	}

	static boolean hasType(ResourceKey<Biome> key, String type) {
		Registry<Biome> registry = activeRegistry;
		if (registry != null) {
			return registry.getHolder(key)
					.map(holder -> matches(holder, tags(type))).orElse(false)
					|| legacyHasType(key, type);
		}
		return legacyHasType(key, type);
	}

	private static boolean legacyHasType(ResourceKey<Biome> key, String type) {
		try {
			return BiomeDictionary.hasType(key, BiomeDictionary.Type.getType(type));
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	static Biome biome(ResourceLocation id) {
		Registry<Biome> registry = activeRegistry;
		if (registry != null) return registry.get(id);
		try {
			Bootstrap.checkBootstrapCalled(() -> "OreSpawn biome lookup");
		} catch (IllegalArgumentException ignored) {
			return null;
		}
		return ForgeRegistries.BIOMES.getValue(id);
	}

	private static List<TagKey<Biome>> tags(String type) {
		String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
		List<TagKey<Biome>> known = TypeTags.TYPES.get(normalized);
		if (known != null) return known;
		if (normalized.isEmpty()) return Collections.emptyList();
		return Collections.singletonList(TagKey.create(Registry.BIOME_REGISTRY,
				new ResourceLocation("forge", "is_" + normalized.toLowerCase(Locale.ROOT))));
	}

	private static boolean matches(Holder<Biome> holder,
			Collection<TagKey<Biome>> tags) {
		for (TagKey<Biome> tag : tags) if (holder.is(tag)) return true;
		return false;
	}

	private static Map<String, List<TagKey<Biome>>> types() {
		Map<String, List<TagKey<Biome>>> result = new LinkedHashMap<>();
		add(result, "MOUNTAIN", BiomeTags.IS_MOUNTAIN);
		add(result, "HILLS", BiomeTags.IS_HILL);
		add(result, "OCEAN", BiomeTags.IS_OCEAN);
		add(result, "RIVER", BiomeTags.IS_RIVER);
		add(result, "BEACH", Tags.Biomes.IS_BEACH, BiomeTags.IS_BEACH);
		add(result, "SANDY", Tags.Biomes.IS_SANDY);
		add(result, "DRY", Tags.Biomes.IS_DRY);
		add(result, "WET", Tags.Biomes.IS_WET);
		add(result, "SWAMP", Tags.Biomes.IS_SWAMP);
		add(result, "SNOWY", Tags.Biomes.IS_SNOWY);
		add(result, "COLD", Tags.Biomes.IS_COLD);
		add(result, "HOT", Tags.Biomes.IS_HOT);
		add(result, "MESA", BiomeTags.IS_BADLANDS);
		add(result, "FOREST", BiomeTags.IS_FOREST);
		add(result, "PLAINS", Tags.Biomes.IS_PLAINS);
		add(result, "SAVANNA", Tags.Biomes.IS_SAVANNA);
		add(result, "CONIFEROUS", Tags.Biomes.IS_CONIFEROUS, BiomeTags.IS_TAIGA);
		add(result, "JUNGLE", BiomeTags.IS_JUNGLE);
		add(result, "LUSH", Tags.Biomes.IS_LUSH);
		add(result, "MUSHROOM", Tags.Biomes.IS_MUSHROOM);
		add(result, "PLATEAU", Tags.Biomes.IS_PLATEAU);
		add(result, "PEAK", Tags.Biomes.IS_PEAK);
		add(result, "SLOPE", Tags.Biomes.IS_SLOPE);
		add(result, "UNDERGROUND", Tags.Biomes.IS_UNDERGROUND);
		add(result, "WASTELAND", Tags.Biomes.IS_WASTELAND);
		add(result, "WATER", Tags.Biomes.IS_WATER, BiomeTags.IS_OCEAN,
				BiomeTags.IS_RIVER);
		add(result, "DENSE", Tags.Biomes.IS_DENSE);
		add(result, "SPARSE", Tags.Biomes.IS_SPARSE);
		add(result, "DEAD", Tags.Biomes.IS_DEAD);
		add(result, "MAGICAL", Tags.Biomes.IS_MAGICAL);
		add(result, "SPOOKY", Tags.Biomes.IS_SPOOKY);
		add(result, "NETHER", BiomeTags.IS_NETHER);
		add(result, "END", Tags.Biomes.IS_END);
		add(result, "VOID", Tags.Biomes.IS_VOID);
		return Collections.unmodifiableMap(result);
	}

	private static final class TypeTags {
		private static final Map<String, List<TagKey<Biome>>> TYPES = types();

		private TypeTags() {
		}
	}

	@SafeVarargs
	private static void add(Map<String, List<TagKey<Biome>>> target, String name,
			TagKey<Biome>... tags) {
		List<TagKey<Biome>> values = new ArrayList<>(tags.length);
		Collections.addAll(values, tags);
		target.put(name, Collections.unmodifiableList(values));
	}
}
