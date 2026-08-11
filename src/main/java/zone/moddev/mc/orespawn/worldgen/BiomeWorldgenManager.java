package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.DimensionMaterials;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Palette;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Surface;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Choice;

import net.minecraft.util.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.gen.ChunkProviderOverworld;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Bakes and installs optional biome and dimension-material integration. */
final class BiomeWorldgenManager {
	private static final Logger LOGGER = LogManager.getLogger();
	private static volatile Map<ResourceLocation, BakedBiomeWorldgen> baked =
			Collections.emptyMap();
	private static final Map<ChunkProviderOverworld, IBlockState> ORIGINAL_DEFAULT_FLUIDS =
			new IdentityHashMap<>();
	private static final Set<ResourceLocation> WARNED_DEEP_FLUID_DIMENSIONS =
			new LinkedHashSet<>();

	private BiomeWorldgenManager() {
	}

	static void registerBiomeSourceCodec() {
		// Minecraft 1.10 biome providers are not codec-serialized.
	}

	static synchronized void apply(MinecraftServer server, WorldGeologyProfile profile) {
		BiomeFeatureInstaller.restoreAll();
		if (WorldgenBenchmark.isVanillaBaseline()) {
			for (WorldServer level : server.worlds) {
				installBiomeSource(level, null);
				installAquiferMaterials(level, null);
			}
			baked = Collections.emptyMap();
			return;
		}
		Map<ResourceLocation, BakedBiomeWorldgen> next = new LinkedHashMap<>();
		for (WorldServer level : server.worlds) {
			BakedBiomeWorldgen dimension = bake(level, profile.rootCopy());
			ResourceLocation dimensionId = WorldIds.dimension(level);
			if (dimension != null) next.put(dimensionId, dimension);
			BiomeFeatureInstaller.install(dimension, dimensionId);
			installBiomeSource(level, dimension);
			installAquiferMaterials(level, dimension == null ? null : dimension.materials);
		}
		baked = Collections.unmodifiableMap(next);
	}

	static synchronized void apply(WorldServer level, WorldGeologyProfile profile) {
		if (WorldgenBenchmark.isVanillaBaseline()) {
			installBiomeSource(level, null);
			installAquiferMaterials(level, null);
			return;
		}
		Map<ResourceLocation, BakedBiomeWorldgen> next = new LinkedHashMap<>(baked);
		BakedBiomeWorldgen dimension = bake(level, profile.rootCopy());
		ResourceLocation dimensionId = WorldIds.dimension(level);
		if (dimension == null) next.remove(dimensionId);
		else next.put(dimensionId, dimension);
		BiomeFeatureInstaller.install(dimension, dimensionId);
		installBiomeSource(level, dimension);
		installAquiferMaterials(level, dimension == null ? null : dimension.materials);
		baked = Collections.unmodifiableMap(next);
	}

	static synchronized void clear() {
		BiomeFeatureInstaller.restoreAll();
		for (Entry<ChunkProviderOverworld, IBlockState> entry : ORIGINAL_DEFAULT_FLUIDS.entrySet()) {
			setDefaultFluid(entry.getKey(), entry.getValue());
		}
		ORIGINAL_DEFAULT_FLUIDS.clear();
		WARNED_DEEP_FLUID_DIMENSIONS.clear();
		baked = Collections.emptyMap();
	}

	static BakedBiomeWorldgen get(ResourceLocation dimension) {
		return baked.get(dimension);
	}

	private static BakedBiomeWorldgen bake(WorldServer level, JsonObject root) {
		ResourceLocation dimensionId = WorldIds.dimension(level);
		Map<Biome, Surface> surfaces = new IdentityHashMap<>();
		List<Palette> palettes = bakePalettes(root, dimensionId, surfaces);
		DimensionMaterials materials = bakeMaterials(root, dimensionId);
		if (palettes.isEmpty() && surfaces.isEmpty() && materials == null) return null;
		LOGGER.info("Baked OreSpawn biome integration for '{}' with {} palettes, {} surfaces, and {} material overrides",
				dimensionId, palettes.size(), surfaces.size(), materials == null ? 0 : 1);
		return new BakedBiomeWorldgen(palettes, surfaces, materials);
	}

	private static List<Palette> bakePalettes(JsonObject root,
			ResourceLocation dimension, Map<Biome, Surface> surfaces) {
		JsonObject section = object(root, "biome_palettes");
		List<Palette> result = new ArrayList<>();
		for (Entry<String, JsonElement> paletteEntry : section.entrySet()) {
			if (!paletteEntry.getValue().isJsonObject()) continue;
			JsonObject json = paletteEntry.getValue().getAsJsonObject();
			if (!bool(json, "enabled", true)
					|| !dimension.toString().equals(string(json, "dimension", ""))) continue;
			JsonObject biomeEntries = object(json, "biomes");
			List<BakedBiomeWorldgen.Entry> entries = new ArrayList<>();
			for (Entry<String, JsonElement> biomeEntry : biomeEntries.entrySet()) {
				if (!biomeEntry.getValue().isJsonObject()) continue;
				ResourceLocation biomeId;
				try { biomeId = new ResourceLocation(biomeEntry.getKey()); }
				catch (RuntimeException e) { continue; }
				Biome biome = ForgeRegistries.BIOMES.getValue(biomeId);
				if (biome == null) {
					LOGGER.warn("Skipping missing optional OreSpawn biome '{}'", biomeId);
					continue;
				}
				JsonObject placement = biomeEntry.getValue().getAsJsonObject();
				if (!bool(placement, "enabled", true)) continue;
				Set<ResourceLocation> similar = ids(placement.get("similar_biomes"));
				Set<ResourceLocation> required = ids(placement.get("required_similar_biomes"));
				boolean missingRequired = false;
				for (ResourceLocation id : required) {
					if (ForgeRegistries.BIOMES.getValue(id) == null) {
						missingRequired = true;
						LOGGER.warn("Skipping OreSpawn biome '{}' because required similar biome '{}' is missing",
								biomeId, id);
					}
				}
				if (missingRequired) continue;
				similar.addAll(required);
				double weight = decimal(placement, "weight", 1.0D);
				if (weight <= 0.0D) continue;
				entries.add(new BakedBiomeWorldgen.Entry(biome, weight,
						Collections.unmodifiableSet(similar),
						(float) decimal(placement, "min_temperature", -2.0D),
						(float) decimal(placement, "max_temperature", 2.0D),
						(float) decimal(placement, "min_downfall", 0.0D),
						(float) decimal(placement, "max_downfall", 1.0D)));
				if (placement.has("surface") && placement.get("surface").isJsonObject()) {
					Surface surface = surface(placement.getAsJsonObject("surface"));
					if (surface != null) surfaces.put(biome, surface);
				}
			}
			if (entries.isEmpty()) continue;
			BakedBiomeWorldgen.Entry[] bakedEntries =
					entries.toArray(new BakedBiomeWorldgen.Entry[entries.size()]);
			result.add(new Palette(paletteEntry.getKey().hashCode() * 0x9E3779B97F4A7C15L,
					"replace".equals(string(json, "mode", "augment")),
					scope(string(json, "scope", "minecraft_only")),
					Math.max(1, regionBlocks(string(json, "region_size", "average")) >> 2),
					decimal(json, "coverage", 1.0D),
					decimal(json, "fallback_weight", 1.0D),
					Collections.unmodifiableSet(strings(json.get("include_namespaces"))),
					Collections.unmodifiableSet(strings(json.get("exclude_namespaces"))),
					bakedEntries, bakeChoices(json, bakedEntries)));
		}
		return result;
	}

	private static Map<Biome, Choice> bakeChoices(
			JsonObject palette, BakedBiomeWorldgen.Entry[] entries) {
		boolean replace = "replace".equals(string(palette, "mode", "augment"));
		int scope = scope(string(palette, "scope", "minecraft_only"));
		Set<String> included = strings(palette.get("include_namespaces"));
		Set<String> excluded = strings(palette.get("exclude_namespaces"));
		double fallbackWeight = replace ? 0.0D
				: Math.max(0.0D, decimal(palette, "fallback_weight", 1.0D));
		Map<Biome, Choice> result = new IdentityHashMap<>();
		for (Entry<ResourceLocation, Biome> source : ForgeRegistries.BIOMES.getEntries()) {
			ResourceLocation sourceId = source.getKey();
			if (!scopeMatches(scope, included, excluded, sourceId)) continue;
			Biome biome = source.getValue();
			float temperature = biome.getTemperature();
			float downfall = biome.getRainfall();
			int count = 0;
			for (BakedBiomeWorldgen.Entry candidate : entries) {
				if (eligible(candidate, sourceId, temperature, downfall)) count++;
			}
			if (count == 0) continue;

			Biome[] outputs = new Biome[count];
			double[] cumulative = new double[count];
			double total = fallbackWeight;
			int index = 0;
			for (BakedBiomeWorldgen.Entry candidate : entries) {
				if (!eligible(candidate, sourceId, temperature, downfall)) continue;
				total += candidate.weight;
				outputs[index] = candidate.biome;
				cumulative[index] = total;
				index++;
			}
			result.put(biome, new Choice(outputs, cumulative, fallbackWeight, total,
					sourceId.hashCode()));
		}
		return result;
	}

	private static boolean scopeMatches(int scope, Set<String> included,
			Set<String> excluded, ResourceLocation source) {
		String namespace = source.getResourceDomain();
		if (excluded.contains(namespace)) return false;
		if (scope == 1) return "minecraft".equals(namespace);
		if (scope == 2) return included.contains(namespace);
		return true;
	}

	private static boolean eligible(BakedBiomeWorldgen.Entry entry,
			ResourceLocation source, float temperature, float downfall) {
		return (entry.similarBiomes.isEmpty() || entry.similarBiomes.contains(source))
				&& temperature >= entry.minTemperature && temperature <= entry.maxTemperature
				&& downfall >= entry.minDownfall && downfall <= entry.maxDownfall
				&& entry.weight > 0.0D;
	}

	private static DimensionMaterials bakeMaterials(JsonObject root, ResourceLocation dimension) {
		DimensionMaterials selected = null;
		String selectedId = null;
		for (Entry<String, JsonElement> entry : object(root, "dimension_materials").entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonObject json = entry.getValue().getAsJsonObject();
			if (!bool(json, "enabled", true)
					|| !dimension.toString().equals(string(json, "dimension", ""))) continue;
			DimensionMaterials candidate = new DimensionMaterials(
					state(json, "default_fluid", true),
					state(json, "deep_aquifer_fluid", true),
					integer(json, "deep_aquifer_max_y", -54),
					state(json, "snow_block", false),
					state(json, "ice_block", false));
			if (selected != null) {
				LOGGER.warn("Dimension material rules '{}' and '{}' both target '{}'; using '{}'",
						selectedId, entry.getKey(), dimension, selectedId);
				continue;
			}
			selected = candidate;
			selectedId = entry.getKey();
		}
		return selected;
	}

	private static void installBiomeSource(WorldServer level, BakedBiomeWorldgen config) {
		BiomeProvider original = level.provider.biomeProvider;
		while (original instanceof BiomeOverlaySource) {
			original = ((BiomeOverlaySource) original).delegate();
		}
		if (config == null || !config.hasBiomeOverlay()) {
			level.provider.biomeProvider = original;
			return;
		}
		BiomeOverlaySource overlay =
				new BiomeOverlaySource(original, config.palettes, level.getSeed());
		level.provider.biomeProvider = overlay;
	}

	private static void installAquiferMaterials(WorldServer level, DimensionMaterials materials) {
		IChunkGenerator raw = level.getChunkProvider().chunkGenerator;
		if (!(raw instanceof ChunkProviderOverworld)) {
			if (materials != null && materials.defaultFluid != null
					&& WARNED_DEEP_FLUID_DIMENSIONS.add(WorldIds.dimension(level))) {
				LOGGER.warn("Dimension '{}' requests a generator fluid override that Forge 1.10 does not expose; preserving the profile value",
						WorldIds.dimension(level));
			}
			return;
		}
		ChunkProviderOverworld generator = (ChunkProviderOverworld) raw;
		IBlockState original = ORIGINAL_DEFAULT_FLUIDS.computeIfAbsent(generator,
				ignored -> generator.oceanBlock);
		IBlockState selected = materials != null && materials.defaultFluid != null
				? materials.defaultFluid : original;
		setDefaultFluid(generator, selected);
		if (materials != null && materials.deepFluid != null
				&& !materials.deepFluid.equals(selected)
				&& materials.deepFluidMaxY >= 0
				&& WARNED_DEEP_FLUID_DIMENSIONS.add(WorldIds.dimension(level))) {
			LOGGER.warn("Dimension '{}' requests a distinct deep aquifer fluid below Y {}, but Minecraft 1.10.2 only exposes one generator fluid; preserving the profile value and using default_fluid for generation",
					WorldIds.dimension(level), materials.deepFluidMaxY);
		}
	}

	private static void setDefaultFluid(ChunkProviderOverworld generator, IBlockState fluid) {
		generator.oceanBlock = fluid;
	}

	private static Surface surface(JsonObject json) {
		IBlockState top = state(json, "top_block", false);
		IBlockState filler = state(json, "filler_block", false);
		IBlockState underwater = state(json, "underwater_block", false);
		IBlockState ceiling = state(json, "ceiling_block", false);
		if (top == null && filler == null && underwater == null && ceiling == null) return null;
		return new Surface(top, filler, underwater, ceiling,
				Math.max(0, Math.min(16, integer(json, "filler_depth", 3))));
	}

	private static IBlockState state(JsonObject json, String key, boolean fluid) {
		if (!json.has(key)) return null;
		try {
			Block block = ForgeRegistries.BLOCKS.getValue(
					new ResourceLocation(json.get(key).getAsString()));
			if (block == null || block == Blocks.AIR
					|| (fluid && !(block instanceof IFluidBlock)
							&& !block.getDefaultState().getMaterial().isLiquid())) return null;
			int metadata = integer(json, key + "_metadata", 0);
			try { return block.getStateFromMeta(Math.max(0, Math.min(15, metadata))); }
			catch (RuntimeException ignored) { return block.getDefaultState(); }
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static int scope(String value) {
		if ("minecraft_only".equals(value)) return 1;
		if ("selected_namespaces".equals(value)) return 2;
		return 0;
	}

	private static int regionBlocks(String value) {
		switch (value) {
		case "tiny": return 128;
		case "small": return 256;
		case "large": return 1024;
		case "huge": return 2048;
		default: return 512;
		}
	}

	private static Set<ResourceLocation> ids(JsonElement element) {
		Set<ResourceLocation> result = new LinkedHashSet<>();
		if (element != null && element.isJsonArray()) {
			for (JsonElement value : element.getAsJsonArray()) {
				try { result.add(new ResourceLocation(value.getAsString())); }
				catch (RuntimeException ignored) { }
			}
		}
		return result;
	}

	private static Set<String> strings(JsonElement element) {
		Set<String> result = new LinkedHashSet<>();
		if (element != null && element.isJsonArray()) {
			for (JsonElement value : element.getAsJsonArray()) result.add(value.getAsString());
		}
		return result;
	}

	private static JsonObject object(JsonObject root, String key) {
		return root.has(key) && root.get(key).isJsonObject()
				? root.getAsJsonObject(key) : new JsonObject();
	}

	private static String string(JsonObject root, String key, String fallback) {
		return root.has(key) ? root.get(key).getAsString() : fallback;
	}

	private static boolean bool(JsonObject root, String key, boolean fallback) {
		return root.has(key) ? root.get(key).getAsBoolean() : fallback;
	}

	private static int integer(JsonObject root, String key, int fallback) {
		return root.has(key) ? root.get(key).getAsInt() : fallback;
	}

	private static double decimal(JsonObject root, String key, double fallback) {
		return root.has(key) ? root.get(key).getAsDouble() : fallback;
	}

}
