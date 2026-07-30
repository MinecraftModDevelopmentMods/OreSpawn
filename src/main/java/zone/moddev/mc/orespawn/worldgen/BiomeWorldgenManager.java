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
import java.util.function.Supplier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.DimensionMaterials;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Palette;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Surface;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Choice;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.neoforge.registries.RegisterEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Bakes and installs optional biome and dimension-material integration. */
final class BiomeWorldgenManager {
	private static final Logger LOGGER = LogManager.getLogger();
	private static volatile Map<ResourceKey<Level>, BakedBiomeWorldgen> baked =
			Collections.emptyMap();
	private static final Map<NoiseBasedChunkGenerator, Supplier<Aquifer.FluidPicker>>
			ORIGINAL_FLUID_PICKERS = new IdentityHashMap<>();

	private BiomeWorldgenManager() {
	}

	static void registerBiomeSourceCodec(RegisterEvent event) {
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("orespawn", "profile_overlay");
		event.register(Registries.BIOME_SOURCE,
				helper -> helper.register(id, BiomeOverlaySource.CODEC));
	}

	static synchronized void apply(MinecraftServer server, WorldGeologyProfile profile) {
		BiomeFeatureInstaller.restoreAll();
		if (WorldgenBenchmark.isVanillaBaseline()) {
			for (ServerLevel level : server.getAllLevels()) {
				installBiomeSource(level, null);
				installAquiferMaterials(level, null);
			}
			baked = Collections.emptyMap();
			return;
		}
		Map<ResourceKey<Level>, BakedBiomeWorldgen> next = new LinkedHashMap<>();
		for (ServerLevel level : server.getAllLevels()) {
			BakedBiomeWorldgen dimension = bake(level, profile.rootCopy());
			if (dimension != null) next.put(level.dimension(), dimension);
			BiomeFeatureInstaller.install(dimension, level.dimension());
			installBiomeSource(level, dimension);
			installAquiferMaterials(level, dimension == null ? null : dimension.materials);
		}
		baked = Collections.unmodifiableMap(next);
	}

	static synchronized void apply(ServerLevel level, WorldGeologyProfile profile) {
		if (WorldgenBenchmark.isVanillaBaseline()) {
			installBiomeSource(level, null);
			installAquiferMaterials(level, null);
			return;
		}
		Map<ResourceKey<Level>, BakedBiomeWorldgen> next = new LinkedHashMap<>(baked);
		BakedBiomeWorldgen dimension = bake(level, profile.rootCopy());
		if (dimension == null) next.remove(level.dimension());
		else next.put(level.dimension(), dimension);
		BiomeFeatureInstaller.install(dimension, level.dimension());
		installBiomeSource(level, dimension);
		installAquiferMaterials(level, dimension == null ? null : dimension.materials);
		baked = Collections.unmodifiableMap(next);
	}

	static synchronized void clear() {
		BiomeFeatureInstaller.restoreAll();
		restoreAquiferMaterials();
		baked = Collections.emptyMap();
	}

	static BakedBiomeWorldgen get(ResourceKey<Level> dimension) {
		return baked.get(dimension);
	}

	private static BakedBiomeWorldgen bake(ServerLevel level, JsonObject root) {
		Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
		ResourceLocation dimensionId = level.dimension().location();
		Map<Holder<Biome>, Surface> surfaces = new IdentityHashMap<>();
		List<Palette> palettes = bakePalettes(root, biomes, dimensionId, surfaces);
		DimensionMaterials materials = bakeMaterials(root, dimensionId);
		if (palettes.isEmpty() && surfaces.isEmpty() && materials == null) return null;
		LOGGER.info("Baked OreSpawn biome integration for '{}' with {} palettes, {} surfaces, and {} material overrides",
				dimensionId, palettes.size(), surfaces.size(), materials == null ? 0 : 1);
		return new BakedBiomeWorldgen(palettes, surfaces, materials);
	}

	private static List<Palette> bakePalettes(JsonObject root, Registry<Biome> registry,
			ResourceLocation dimension, Map<Holder<Biome>, Surface> surfaces) {
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
				try { biomeId = ResourceLocation.parse(biomeEntry.getKey()); }
				catch (RuntimeException e) { continue; }
				Holder<Biome> holder = registry.getHolder(ResourceKey.create(
						Registries.BIOME, biomeId)).orElse(null);
				if (holder == null) {
					LOGGER.warn("Skipping missing optional OreSpawn biome '{}'", biomeId);
					continue;
				}
				JsonObject placement = biomeEntry.getValue().getAsJsonObject();
				if (!bool(placement, "enabled", true)) continue;
				Set<ResourceLocation> similar = ids(placement.get("similar_biomes"));
				Set<ResourceLocation> required = ids(placement.get("required_similar_biomes"));
				boolean missingRequired = false;
				for (ResourceLocation id : required) {
					if (!registry.containsKey(id)) {
						missingRequired = true;
						LOGGER.warn("Skipping OreSpawn biome '{}' because required similar biome '{}' is missing",
								biomeId, id);
					}
				}
				if (missingRequired) continue;
				similar.addAll(required);
				double weight = decimal(placement, "weight", 1.0D);
				if (weight <= 0.0D) continue;
				entries.add(new BakedBiomeWorldgen.Entry(holder, weight,
						Collections.unmodifiableSet(similar),
						(float) decimal(placement, "min_temperature", -2.0D),
						(float) decimal(placement, "max_temperature", 2.0D),
						(float) decimal(placement, "min_downfall", 0.0D),
						(float) decimal(placement, "max_downfall", 1.0D)));
				if (placement.has("surface") && placement.get("surface").isJsonObject()) {
					Surface surface = surface(placement.getAsJsonObject("surface"));
					if (surface != null) surfaces.put(holder, surface);
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
					bakedEntries, bakeChoices(registry, json, bakedEntries)));
		}
		return result;
	}

	private static Map<Biome, Choice> bakeChoices(Registry<Biome> registry,
			JsonObject palette, BakedBiomeWorldgen.Entry[] entries) {
		boolean replace = "replace".equals(string(palette, "mode", "augment"));
		int scope = scope(string(palette, "scope", "minecraft_only"));
		Set<String> included = strings(palette.get("include_namespaces"));
		Set<String> excluded = strings(palette.get("exclude_namespaces"));
		double fallbackWeight = replace ? 0.0D
				: Math.max(0.0D, decimal(palette, "fallback_weight", 1.0D));
		Map<Biome, Choice> result = new IdentityHashMap<>();
		for (Entry<ResourceKey<Biome>, Biome> source : registry.entrySet()) {
			ResourceLocation sourceId = source.getKey().location();
			if (!scopeMatches(scope, included, excluded, sourceId)) continue;
			Biome biome = source.getValue();
			float temperature = biome.getBaseTemperature();
			float downfall = biome.getModifiedClimateSettings().downfall();
			int count = 0;
			for (BakedBiomeWorldgen.Entry candidate : entries) {
				if (eligible(candidate, sourceId, temperature, downfall)) count++;
			}
			if (count == 0) continue;

			@SuppressWarnings("unchecked")
			Holder<Biome>[] outputs = new Holder[count];
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
		String namespace = source.getNamespace();
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

	private static void installBiomeSource(ServerLevel level, BakedBiomeWorldgen config) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		BiomeSource original = generator.biomeSource;
		while (original instanceof BiomeOverlaySource) {
			original = ((BiomeOverlaySource) original).delegate();
		}
		if (config == null || !config.hasBiomeOverlay()) {
			generator.biomeSource = original;
			return;
		}
		BiomeOverlaySource overlay =
				new BiomeOverlaySource(original, config.palettes, level.getSeed());
		generator.biomeSource = overlay;
	}

	private static void installAquiferMaterials(ServerLevel level, DimensionMaterials materials) {
		ChunkGenerator raw = level.getChunkSource().getGenerator();
		if (!(raw instanceof NoiseBasedChunkGenerator)) return;
		NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) raw;
		Supplier<Aquifer.FluidPicker> originalSupplier =
				ORIGINAL_FLUID_PICKERS.computeIfAbsent(generator,
						ignored -> generator.globalFluidPicker);
		if (materials == null || !materials.hasAquiferOverride()) {
			generator.globalFluidPicker = originalSupplier;
			return;
		}
		Aquifer.FluidPicker configured = new ConfiguredFluidPicker(
				originalSupplier.get(), generator.getSeaLevel(), materials);
		generator.globalFluidPicker = () -> configured;
	}

	private static void restoreAquiferMaterials() {
		for (Map.Entry<NoiseBasedChunkGenerator, Supplier<Aquifer.FluidPicker>> entry
				: ORIGINAL_FLUID_PICKERS.entrySet()) {
			entry.getKey().globalFluidPicker = entry.getValue();
		}
		ORIGINAL_FLUID_PICKERS.clear();
	}

	private static Surface surface(JsonObject json) {
		BlockState top = state(json, "top_block", false);
		BlockState filler = state(json, "filler_block", false);
		BlockState underwater = state(json, "underwater_block", false);
		BlockState ceiling = state(json, "ceiling_block", false);
		if (top == null && filler == null && underwater == null && ceiling == null) return null;
		return new Surface(top, filler, underwater, ceiling,
				Math.max(0, Math.min(16, integer(json, "filler_depth", 3))));
	}

	private static BlockState state(JsonObject json, String key, boolean fluid) {
		if (!json.has(key)) return null;
		try {
			Block block = BuiltInRegistries.BLOCK.get(
					ResourceLocation.parse(json.get(key).getAsString()));
			if (block == null || block == Blocks.AIR
					|| (fluid && block.defaultBlockState().getFluidState().isEmpty())) return null;
			return block.defaultBlockState();
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
				try { result.add(ResourceLocation.parse(value.getAsString())); }
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

	private static final class ConfiguredFluidPicker implements Aquifer.FluidPicker {
		private final Aquifer.FluidPicker original;
		private final Aquifer.FluidStatus defaultFluid;
		private final Aquifer.FluidStatus deepFluid;
		private final int deepMaxY;

		ConfiguredFluidPicker(Aquifer.FluidPicker original, int seaLevel,
				DimensionMaterials materials) {
			this.original = original;
			defaultFluid = materials.defaultFluid == null ? null
					: new Aquifer.FluidStatus(seaLevel, materials.defaultFluid);
			deepFluid = materials.deepFluid == null ? null
					: new Aquifer.FluidStatus(materials.deepFluidMaxY, materials.deepFluid);
			deepMaxY = materials.deepFluidMaxY;
		}

		Aquifer.FluidPicker original() {
			return original;
		}

		@Override
		public Aquifer.FluidStatus computeFluid(int x, int y, int z) {
			if (deepFluid != null && y < deepMaxY) return deepFluid;
			if (defaultFluid != null) return defaultFluid;
			return original.computeFluid(x, y, z);
		}
	}
}
