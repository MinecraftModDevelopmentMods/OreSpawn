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
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Preserves OreSpawn's legacy biome-dictionary vocabulary on versions where
 * NeoForge expresses biome traits through tags.
 */
final class BiomeTypeCompatibility {
	private static final Map<String, List<TagKey<Biome>>> TYPES = types();

	private BiomeTypeCompatibility() {
	}

	static Set<String> types(Biome biome) {
		Holder<Biome> holder = zone.moddev.mc.orespawn.worldgen.BiomeRegistryAccess.holder(biome);
		Set<String> result = new LinkedHashSet<>();
		for (Map.Entry<String, List<TagKey<Biome>>> entry : TYPES.entrySet()) {
			if (matches(holder, entry.getValue())) result.add(entry.getKey());
		}
		return result;
	}

	static Set<Biome> biomes(String type) {
		Set<Biome> result = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (Biome biome : zone.moddev.mc.orespawn.worldgen.BiomeRegistryAccess.values()) {
			if (hasType(biome, type)) result.add(biome);
		}
		return result;
	}

	static Set<ResourceKey<Biome>> biomeKeys(String type) {
		Set<ResourceKey<Biome>> result = new LinkedHashSet<>();
		for (Biome biome : biomes(type)) {
			ResourceLocation id = zone.moddev.mc.orespawn.worldgen.BiomeRegistryAccess.id(biome);
			if (id != null) result.add(ResourceKey.create(Registries.BIOME, id));
		}
		return result;
	}

	static boolean hasType(ResourceKey<Biome> key, String type) {
		return zone.moddev.mc.orespawn.worldgen.BiomeRegistryAccess.holder(key)
				.map(holder -> matches(holder, tags(type))).orElse(false);
	}

	static boolean hasType(Biome biome, String type) {
		return matches(zone.moddev.mc.orespawn.worldgen.BiomeRegistryAccess.holder(biome), tags(type));
	}

	private static List<TagKey<Biome>> tags(String type) {
		String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
		List<TagKey<Biome>> known = TYPES.get(normalized);
		if (known != null) return known;
		if (normalized.isEmpty()) return Collections.emptyList();
		return Collections.singletonList(TagKey.create(Registries.BIOME,
				ResourceLocation.fromNamespaceAndPath("c", "is_" + normalized.toLowerCase(Locale.ROOT))));
	}

	private static boolean matches(Holder<Biome> holder,
			Collection<TagKey<Biome>> tags) {
		for (TagKey<Biome> tag : tags) if (holder.is(tag)) return true;
		return false;
	}

	private static Map<String, List<TagKey<Biome>>> types() {
		Map<String, List<TagKey<Biome>>> result = new LinkedHashMap<>();
		add(result, "MOUNTAIN", Tags.Biomes.IS_MOUNTAIN, BiomeTags.IS_MOUNTAIN);
		add(result, "HILLS", BiomeTags.IS_HILL);
		add(result, "OCEAN", BiomeTags.IS_OCEAN);
		add(result, "RIVER", BiomeTags.IS_RIVER);
		add(result, "BEACH", BiomeTags.IS_BEACH);
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
		add(result, "SAVANNA", BiomeTags.IS_SAVANNA);
		add(result, "CONIFEROUS", Tags.Biomes.IS_CONIFEROUS_TREE,
				Tags.Biomes.IS_TAIGA, BiomeTags.IS_TAIGA);
		add(result, "JUNGLE", BiomeTags.IS_JUNGLE);
		add(result, "LUSH", Tags.Biomes.IS_LUSH);
		add(result, "MUSHROOM", Tags.Biomes.IS_MUSHROOM);
		add(result, "PLATEAU", Tags.Biomes.IS_PLATEAU);
		add(result, "PEAK", Tags.Biomes.IS_MOUNTAIN_PEAK);
		add(result, "SLOPE", Tags.Biomes.IS_MOUNTAIN_SLOPE);
		add(result, "UNDERGROUND", Tags.Biomes.IS_UNDERGROUND, Tags.Biomes.IS_CAVE);
		add(result, "WASTELAND", Tags.Biomes.IS_WASTELAND);
		add(result, "WATER", Tags.Biomes.IS_AQUATIC, BiomeTags.IS_OCEAN,
				BiomeTags.IS_RIVER);
		add(result, "DENSE", Tags.Biomes.IS_DENSE_VEGETATION);
		add(result, "SPARSE", Tags.Biomes.IS_SPARSE_VEGETATION);
		add(result, "DEAD", Tags.Biomes.IS_DEAD);
		add(result, "MAGICAL", Tags.Biomes.IS_MAGICAL);
		add(result, "SPOOKY", Tags.Biomes.IS_SPOOKY);
		add(result, "NETHER", BiomeTags.IS_NETHER);
		add(result, "END", BiomeTags.IS_END);
		add(result, "VOID", Tags.Biomes.IS_VOID);
		return Collections.unmodifiableMap(result);
	}

	@SafeVarargs
	private static void add(Map<String, List<TagKey<Biome>>> target, String name,
			TagKey<Biome>... tags) {
		List<TagKey<Biome>> values = new ArrayList<>(tags.length);
		Collections.addAll(values, tags);
		target.put(name, Collections.unmodifiableList(values));
	}
}
