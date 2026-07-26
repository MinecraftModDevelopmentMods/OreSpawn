package com.mcmoddev.orespawn.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.CompiledOrePattern;
import com.mcmoddev.orespawn.api.OreDimensionSelector;
import com.mcmoddev.orespawn.api.OrePlacementContext;
import com.mcmoddev.orespawn.init.OreSpawnPatterns;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biome.BiomeCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.registries.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** One dynamic feature for every OreSpawn-managed ore and dimension. */
public final class OreSpawnOreGeneration extends Feature<NoneFeatureConfiguration> {
	private static final Logger LOGGER = LogManager.getLogger();
	public static final OreSpawnOreGeneration FEATURE = new OreSpawnOreGeneration();
	private static final BakedOre[] NO_ORES = new BakedOre[0];
	private static final Map<ResourceKey<Level>, BakedOre[]> EMPTY_DIMENSIONS = Collections.emptyMap();
	private static final Object CLASSIFIER_LOCK = new Object();

	private static Holder<PlacedFeature> placedFeature;
	private static volatile Map<ResourceKey<Level>, BakedOre[]> oresByDimension = EMPTY_DIMENSIONS;
	private static volatile Map<ResourceKey<Level>, Set<Block>> vanillaTakeoverOutputs = Collections.emptyMap();
	private static volatile BakedOre[] selectorOres = NO_ORES;
	private static volatile Set<Block> selectorVanillaTakeoverOutputs = Collections.emptySet();
	private static volatile BakedGeomeConfig geomeConfig;
	private static volatile GeomeGeology classifier;
	private static volatile long classifierSeed = Long.MIN_VALUE;
	private static final ThreadLocal<GenerationScratch> GENERATION_SCRATCH =
			ThreadLocal.withInitial(GenerationScratch::new);

	private OreSpawnOreGeneration() {
		super(NoneFeatureConfiguration.CODEC);
		setRegistryName(OreSpawn.MODID, "managed_ores");
	}

	public static void registerConfiguredFeatures() {
		ResourceLocation id = new ResourceLocation(OreSpawn.MODID, "managed_ores");
		Holder<ConfiguredFeature<?, ?>> configured = BuiltinRegistries.register(BuiltinRegistries.CONFIGURED_FEATURE,
				id, new ConfiguredFeature<NoneFeatureConfiguration, OreSpawnOreGeneration>(FEATURE,
						NoneFeatureConfiguration.INSTANCE));
		placedFeature = BuiltinRegistries.register(BuiltinRegistries.PLACED_FEATURE, id,
				new PlacedFeature(configured, Collections.emptyList()));
		VanillaOreFeatureGate.register();
		refreshWorldConfig();
	}

	public static void refreshWorldConfig() {
		geomeConfig = GeomeConfig.baked();
		BakedOres baked = bakeOres(WorldGeologyProfileManager.activeProfile().rootCopy(), geomeConfig);
		oresByDimension = baked.byDimension;
		vanillaTakeoverOutputs = baked.vanillaOutputs;
		selectorOres = baked.selectorOres;
		selectorVanillaTakeoverOutputs = baked.selectorVanillaOutputs;
		synchronized (CLASSIFIER_LOCK) {
			classifier = null;
			classifierSeed = Long.MIN_VALUE;
		}
		GENERATION_SCRATCH.remove();
	}

	public static void onBiomeLoading(BiomeLoadingEvent event) {
		VanillaOreFeatureGate.wrapVanillaOres(event);
		if (!WorldgenBenchmark.isVanillaBaseline()
				&& event.getCategory() != BiomeCategory.NONE && placedFeature != null) {
			event.getGeneration().getFeatures(GenerationStep.Decoration.UNDERGROUND_ORES).add(placedFeature);
		}
	}

	static boolean takesOverVanillaOre(ResourceKey<Level> dimension, Block output) {
		Set<Block> outputs = vanillaTakeoverOutputs.get(dimension);
		if (outputs == null && selectorAllows(dimension)) outputs = selectorVanillaTakeoverOutputs;
		return !WorldgenBenchmark.isVanillaBaseline() && outputs != null && outputs.contains(output);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel world = context.level();
		ResourceKey<Level> dimension = world.getLevel().dimension();
		BakedOre[] ores = oresForDimension(dimension);
		if (ores.length == 0) {
			return false;
		}

		ChunkAccess chunk = world.getChunk(context.origin());
		GenerationScratch scratch = GENERATION_SCRATCH.get();
		setCenter(scratch.cursor, chunk);
		boolean changed = generateChunk(world, chunk, world.getBiome(scratch.cursor), dimension,
				world.getSeed(), context.random(), ores, false, scratch);
		OreRetrogenManager.markGenerated(dimension, chunk.getPos());
		return changed;
	}

	static boolean retrogen(ServerLevel level, LevelChunk chunk) {
		ResourceKey<Level> dimension = level.dimension();
		BakedOre[] ores = oresForDimension(dimension);
		if (ores.length == 0) return false;
		long seed = mix(level.getSeed(), chunk.getPos().toLong(),
				WorldGeologyProfileManager.activeProfile().generationRevision());
		GenerationScratch scratch = GENERATION_SCRATCH.get();
		setCenter(scratch.cursor, chunk);
		return generateChunk(null, chunk, level.getBiome(scratch.cursor), dimension, level.getSeed(),
				new Random(seed), ores, true, scratch);
	}

	private static void setCenter(BlockPos.MutableBlockPos cursor, ChunkAccess chunk) {
		ChunkPos chunkPos = chunk.getPos();
		cursor.set(chunkPos.getMinBlockX() + 8,
				Math.max(chunk.getMinBuildHeight(), 0), chunkPos.getMinBlockZ() + 8);
	}

	private static boolean generateChunk(WorldGenLevel world, ChunkAccess chunk, Holder<Biome> biome,
			ResourceKey<Level> dimension, long worldSeed, Random random, BakedOre[] ores,
			boolean retrogenOnly, GenerationScratch scratch) {
		ChunkPos chunkPos = chunk.getPos();
		int centerX = chunkPos.getMinBlockX() + 8;
		int centerZ = chunkPos.getMinBlockZ() + 8;
		int geome = -1;
		if (Level.OVERWORLD.equals(dimension)) {
			geome = classifier(worldSeed).classifyColumn(biome.value(), centerX, centerZ,
					scratch.geomeValues(geomeConfig.geomeCount()));
		}

		boolean changed = false;
		for (BakedOre ore : ores) {
			if (retrogenOnly && !ore.retrogen) continue;
			if (!ore.acceptsBiome(biome.value())) {
				continue;
			}
			double frequency = ore.frequency;
			if (geome >= 0) {
				frequency *= ore.geomeWeights[geome];
			}
			int attempts = attemptsForFrequency(random, frequency);
			for (int attempt = 0; attempt < attempts; attempt++) {
				changed |= placeAttempt(world, chunk, random, ore, geome, scratch);
			}
		}
		if (changed) {
			chunk.setUnsaved(true);
		}
		return changed;
	}

	private static long mix(long seed, long chunk, long revision) {
		long value = seed ^ (chunk * 0x9E3779B97F4A7C15L) ^ (revision * 0xBF58476D1CE4E5B9L);
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	private static boolean placeAttempt(WorldGenLevel world, ChunkAccess chunk, Random random,
			BakedOre ore, int geome,
			GenerationScratch scratch) {
		int minY = Math.max(ore.minY, chunk.getMinBuildHeight());
		int maxY = Math.min(ore.maxY, chunk.getMaxBuildHeight() - 1);
		if (maxY < minY) {
			return false;
		}
		int x = chunk.getPos().getMinBlockX() + random.nextInt(16);
		int y = ore.heightDistribution.sample(random, minY, maxY);
		int z = chunk.getPos().getMinBlockZ() + random.nextInt(16);
		int quantity = sampleQuantity(random, ore.minQuantity, ore.maxQuantity);
		scratch.patternContext.initialize(world, chunk, random, ore, geome, x, y, z, minY, maxY,
				quantity);
		return ore.pattern.place(scratch.patternContext);
	}

	static int sampleQuantity(Random random, int minQuantity, int maxQuantity) {
		return minQuantity == maxQuantity ? minQuantity
				: minQuantity + random.nextInt(maxQuantity - minQuantity + 1);
	}

	static boolean selectorAllows(ResourceKey<Level> dimension) {
		return !Level.NETHER.equals(dimension) && !Level.END.equals(dimension);
	}

	static <T> T selectRule(Map<ResourceKey<Level>, T> explicit,
			Set<ResourceKey<Level>> explicitDimensions, T selector, ResourceKey<Level> dimension) {
		return explicitDimensions.contains(dimension) ? explicit.get(dimension)
				: selectorAllows(dimension) ? selector : null;
	}

	private static BakedOre[] oresForDimension(ResourceKey<Level> dimension) {
		BakedOre[] exact = oresByDimension.get(dimension);
		return exact != null ? exact : selectorAllows(dimension) ? selectorOres : NO_ORES;
	}

	private static boolean insideChunk(ChunkAccess chunk, int x, int y, int z) {
		ChunkPos pos = chunk.getPos();
		return x >= pos.getMinBlockX() && x <= pos.getMaxBlockX()
				&& z >= pos.getMinBlockZ() && z <= pos.getMaxBlockZ()
				&& y >= chunk.getMinBuildHeight() && y < chunk.getMaxBuildHeight();
	}

	private static BakedOres bakeOres(JsonObject profile, BakedGeomeConfig config) {
		if (!profile.has("ores") || !profile.get("ores").isJsonObject()) {
			return BakedOres.EMPTY;
		}
		boolean manageVanillaOres = bool(profile, "manage_vanilla_ores", false);
		Map<TagKey<Block>, Set<Block>> resolvedTags = new HashMap<>();
		List<BakedOreRule> rules = new ArrayList<>();
		Set<ResourceKey<Level>> explicitDimensions = new HashSet<>();
		for (Entry<String, JsonElement> oreEntry : profile.getAsJsonObject("ores").entrySet()) {
			if (!oreEntry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject oreJson = oreEntry.getValue().getAsJsonObject();
			if (!bool(oreJson, "enabled", true)) {
				continue;
			}
			boolean nativeGeneration = bool(oreJson, "native_generation", false);
			boolean retrogen = bool(oreJson, "retrogen", true);
			boolean suppressVanilla = nativeGeneration || bool(oreJson, "suppress_vanilla", false);
			if (nativeGeneration && !manageVanillaOres) {
				continue;
			}
			ResourceLocation oreId = resource(string(oreJson, "block", oreEntry.getKey()));
			Block output = oreId == null ? null : ForgeRegistries.BLOCKS.getValue(oreId);
			JsonObject dimensions = objectOrEmpty(oreJson, "dimensions");
			JsonObject selectors = objectOrEmpty(oreJson, "dimension_selectors");
			if (output == null || output == Blocks.AIR || (dimensions.size() == 0 && selectors.size() == 0)) {
				reportBakeProblem("Ignoring invalid OreSpawn-managed ore '{}'", oreEntry.getKey());
				continue;
			}
			BlockState deepOutput = blockState(oreJson, "deep_output", output.defaultBlockState());
			if (deepOutput == null) {
				reportBakeProblem("Ignoring OreSpawn-managed ore '{}' because its deep output is invalid",
						oreEntry.getKey());
				continue;
			}
			int deepOutputMaxY = integer(oreJson, "deep_output_max_y", -1);
			BakedOutput[] outputs = bakeOutputs(oreJson, output.defaultBlockState());
			BakedOreRule rule = new BakedOreRule(output, manageVanillaOres && suppressVanilla);

			for (Entry<String, JsonElement> dimensionEntry : dimensions.entrySet()) {
				ResourceLocation dimensionId = resource(dimensionEntry.getKey());
				if (dimensionId == null) {
					reportBakeProblem("Ignoring invalid dimension '{}' for OreSpawn-managed ore '{}'",
							dimensionEntry.getKey(), oreEntry.getKey());
					continue;
				}
				ResourceKey<Level> dimensionKey = ResourceKey.create(Registry.DIMENSION_REGISTRY, dimensionId);
				rule.explicitDimensions.add(dimensionKey);
				explicitDimensions.add(dimensionKey);
				if (!dimensionEntry.getValue().isJsonObject()) {
					continue;
				}
				JsonObject dimension = dimensionEntry.getValue().getAsJsonObject();
				if (!bool(dimension, "enabled", true)) {
					continue;
				}
				BakedOre baked = bakeOre(output.defaultBlockState(), deepOutput, deepOutputMaxY, outputs,
						dimension, config, resolvedTags, retrogen);
				if (baked != null) {
					rule.explicit.put(dimensionKey, baked);
				} else {
					reportBakeProblem("Ignoring invalid placement rule for OreSpawn-managed ore '{}' in '{}'",
							oreEntry.getKey(), dimensionEntry.getKey());
				}
			}
			for (Entry<String, JsonElement> selectorEntry : selectors.entrySet()) {
				ResourceLocation selectorId = resource(selectorEntry.getKey());
				if (selectorId == null
						|| !OreDimensionSelector.ALL_EXCEPT_NETHER_AND_END.id().equals(selectorId)) {
					reportBakeProblem("Ignoring unknown dimension selector '{}' for OreSpawn-managed ore '{}'",
							selectorEntry.getKey(), oreEntry.getKey());
					continue;
				}
				if (!selectorEntry.getValue().isJsonObject()) continue;
				JsonObject selector = selectorEntry.getValue().getAsJsonObject();
				if (!bool(selector, "enabled", true)) continue;
				rule.selector = bakeOre(output.defaultBlockState(), deepOutput, deepOutputMaxY, outputs,
						selector, config, resolvedTags, retrogen);
				if (rule.selector == null) {
					reportBakeProblem("Ignoring invalid selector rule for OreSpawn-managed ore '{}'",
							oreEntry.getKey());
				}
			}
			rules.add(rule);
		}

		Map<ResourceKey<Level>, BakedOre[]> result = new HashMap<>();
		Map<ResourceKey<Level>, Set<Block>> vanillaOutputs = new HashMap<>();
		for (ResourceKey<Level> dimension : explicitDimensions) {
			List<BakedOre> combined = new ArrayList<>();
			Set<Block> suppressed = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
			for (BakedOreRule rule : rules) {
				BakedOre selected = selectRule(rule.explicit, rule.explicitDimensions,
						rule.selector, dimension);
				if (selected != null) {
					combined.add(selected);
					if (rule.suppressVanilla) suppressed.add(rule.output);
				}
			}
			result.put(dimension, combined.toArray(new BakedOre[combined.size()]));
			vanillaOutputs.put(dimension, Collections.unmodifiableSet(suppressed));
		}
		List<BakedOre> selectorList = new ArrayList<>();
		Set<Block> selectorOutputs = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
		for (BakedOreRule rule : rules) {
			if (rule.selector == null) continue;
			selectorList.add(rule.selector);
			if (rule.suppressVanilla) selectorOutputs.add(rule.output);
		}
		BakedOre[] selectorResult = selectorList.toArray(new BakedOre[selectorList.size()]);
		LOGGER.info("Baked {} OreSpawn-managed ore definitions across {} dimensions",
				rules.size(), result.size());
		Map<ResourceKey<Level>, Set<Block>> immutableVanillaOutputs = new LinkedHashMap<>();
		for (Entry<ResourceKey<Level>, Set<Block>> entry : vanillaOutputs.entrySet()) {
			immutableVanillaOutputs.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
		}
		return new BakedOres(Collections.unmodifiableMap(result),
				Collections.unmodifiableMap(immutableVanillaOutputs), selectorResult,
				Collections.unmodifiableSet(selectorOutputs));
	}

	private static void reportBakeProblem(String message, Object... arguments) {
		// Loading invokes provisional bakes before the server's registries and
		// data-pack tags are authoritative. Keep diagnostics available at debug
		// level then, but warn once the server-thread bake can make a real
		// validity decision.
		if (WorldGeologyProfileManager.activeServer() == null) {
			LOGGER.debug(message, arguments);
		} else {
			LOGGER.warn(message, arguments);
		}
	}

	static Holder<PlacedFeature> placedFeature() {
		return placedFeature;
	}

	private static JsonObject objectOrEmpty(JsonObject root, String key) {
		return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
	}

	private static BakedOre bakeOre(BlockState output, BlockState deepOutput, int deepOutputMaxY,
			BakedOutput[] outputs,
			JsonObject json, BakedGeomeConfig config, Map<TagKey<Block>, Set<Block>> resolvedTags,
			boolean retrogen) {
		int minY = integer(json, "min_y", -64);
		int maxY = integer(json, "max_y", 320);
		double frequency = decimal(json, "frequency", 0.0D);
		boolean hasMinQuantity = json.has("min_quantity");
		boolean hasMaxQuantity = json.has("max_quantity");
		if (hasMinQuantity != hasMaxQuantity) return null;
		int minQuantity = hasMinQuantity ? integer(json, "min_quantity", 0)
				: integer(json, "quantity", 0);
		int maxQuantity = hasMaxQuantity ? integer(json, "max_quantity", 0) : minQuantity;
		if (minY > maxY || frequency <= 0.0D || minQuantity < 1
				|| minQuantity > maxQuantity || maxQuantity > 64) {
			return null;
		}

		Map<Block, Double> hostBlocks = new IdentityHashMap<>();
		addBlocks(hostBlocks, json.get("host_blocks"));
		addTags(hostBlocks, json.get("host_tags"), resolvedTags);
		int familyMask = 0;
		if (json.has("host_families") && json.get("host_families").isJsonArray()) {
			for (JsonElement familyElement : json.getAsJsonArray("host_families")) {
				try {
					familyMask |= 1 << RockFamily.fromConfigName(familyElement.getAsString()).ordinal();
				} catch (RuntimeException ignored) {
					// Validation reports bad provider data; pack overrides are skipped here.
				}
			}
		}
		if (hostBlocks.isEmpty() && familyMask == 0) {
			return null;
		}
		CompiledOrePattern pattern;
		OreHeightDistribution heightDistribution;
		try {
			pattern = OreSpawnPatterns.decode(json);
			heightDistribution = OreHeightDistribution.fromConfigName(string(json,
					"height_distribution", OreHeightDistribution.UNIFORM.configName));
		} catch (IllegalArgumentException e) {
			return null;
		}
		int spread = boundedInteger(json, "spread", 8, 0, 64);
		int verticalSpread = boundedInteger(json, "vertical_spread", Math.max(1, spread / 2), 0, 64);
		int nodeSize = boundedInteger(json, "node_size", 4, 1, 32);
		double discardChanceOnAirExposure = Math.max(0.0D, Math.min(1.0D,
				decimal(json, "discard_chance_on_air_exposure", 0.0D)));

		double[] geomeWeights = new double[config.geomeCount()];
		java.util.Arrays.fill(geomeWeights, 1.0D);
		if (json.has("geomes") && json.get("geomes").isJsonObject()) {
			for (Entry<String, JsonElement> entry : json.getAsJsonObject("geomes").entrySet()) {
				int index = config.geomeIndex(entry.getKey());
				if (index >= 0) {
					geomeWeights[index] = Math.max(0.0D, entry.getValue().getAsDouble());
				}
			}
		}
		Set<Biome> includedBiomes = resolveBiomes(json, "biome_ids", "biome_dictionary");
		Set<Biome> excludedBiomes = resolveBiomes(json, "excluded_biome_ids", "excluded_biome_dictionary");
		return new BakedOre(output, deepOutput, deepOutputMaxY, outputs,
				minY, maxY, Math.min(64.0D, frequency), minQuantity, maxQuantity,
				pattern, heightDistribution, discardChanceOnAirExposure,
				spread, verticalSpread, nodeSize,
				hostBlocks, familyMask, geomeWeights, includedBiomes, excludedBiomes, retrogen);
	}

	private static BakedOutput[] bakeOutputs(JsonObject ore, BlockState fallback) {
		if (!ore.has("outputs") || !ore.get("outputs").isJsonArray()) {
			return new BakedOutput[] { new BakedOutput(fallback, 1.0D, Integer.MIN_VALUE, Integer.MAX_VALUE) };
		}
		List<BakedOutput> result = new ArrayList<>();
		for (JsonElement element : ore.getAsJsonArray("outputs")) {
			if (!element.isJsonObject()) continue;
			JsonObject value = element.getAsJsonObject();
			ResourceLocation id = resource(string(value, "block", ""));
			Block block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
			double weight = decimal(value, "weight", 1.0D);
			int minY = integer(value, "min_y", Integer.MIN_VALUE);
			int maxY = integer(value, "max_y", Integer.MAX_VALUE);
			if (block != null && block != Blocks.AIR && weight > 0.0D && minY <= maxY) {
				result.add(new BakedOutput(block.defaultBlockState(), weight, minY, maxY));
			}
		}
		return result.isEmpty()
				? new BakedOutput[] { new BakedOutput(fallback, 1.0D, Integer.MIN_VALUE, Integer.MAX_VALUE) }
				: result.toArray(new BakedOutput[result.size()]);
	}

	private static void addBlocks(Map<Block, Double> target, JsonElement element) {
		if (element == null || !element.isJsonArray()) {
			return;
		}
		for (JsonElement value : element.getAsJsonArray()) {
			JsonObject object = value.isJsonObject() ? value.getAsJsonObject() : null;
			ResourceLocation id = resource(object == null ? value.getAsString() : string(object, "block", ""));
			Block block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
			if (block != null && block != Blocks.AIR) {
				target.put(block, Math.max(0.0D, Math.min(1.0D,
						object == null ? 1.0D : decimal(object, "weight", 1.0D))));
			}
		}
	}

	private static void addTags(Map<Block, Double> target, JsonElement element,
			Map<TagKey<Block>, Set<Block>> resolvedTags) {
		if (element == null || !element.isJsonArray()) {
			return;
		}
		for (JsonElement value : element.getAsJsonArray()) {
			JsonObject object = value.isJsonObject() ? value.getAsJsonObject() : null;
			ResourceLocation id = resource(object == null ? value.getAsString() : string(object, "tag", ""));
			if (id == null) {
				continue;
			}
			TagKey<Block> tag = TagKey.create(Registry.BLOCK_REGISTRY, id);
			Set<Block> blocks = resolvedTags.computeIfAbsent(tag, OreSpawnOreGeneration::resolveTag);
			double weight = Math.max(0.0D, Math.min(1.0D,
					object == null ? 1.0D : decimal(object, "weight", 1.0D)));
			for (Block block : blocks) target.merge(block, weight, Math::max);
		}
	}

	private static Set<Biome> resolveBiomes(JsonObject rule, String idsKey, String dictionaryKey) {
		Set<Biome> result = Collections.newSetFromMap(new IdentityHashMap<Biome, Boolean>());
		if (rule.has(idsKey) && rule.get(idsKey).isJsonArray()) {
			for (JsonElement element : rule.getAsJsonArray(idsKey)) {
				ResourceLocation id = resource(element.getAsString());
				Biome biome = id == null ? null : ForgeRegistries.BIOMES.getValue(id);
				if (biome != null) result.add(biome);
			}
		}
		if (rule.has(dictionaryKey) && rule.get(dictionaryKey).isJsonArray()) {
			for (JsonElement element : rule.getAsJsonArray(dictionaryKey)) {
				try {
					for (ResourceKey<Biome> key : net.minecraftforge.common.BiomeDictionary.getBiomes(
							net.minecraftforge.common.BiomeDictionary.Type.getType(element.getAsString()))) {
						Biome biome = ForgeRegistries.BIOMES.getValue(key.location());
						if (biome != null) result.add(biome);
					}
				} catch (RuntimeException ignored) {
				}
			}
		}
		return result;
	}

	private static Set<Block> resolveTag(TagKey<Block> tag) {
		Set<Block> result = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
		for (Block block : ForgeRegistries.BLOCKS.getValues()) {
			if (block.defaultBlockState().is(tag)) {
				result.add(block);
			}
		}
		return result;
	}

	private static GeomeGeology classifier(long seed) {
		GeomeGeology current = classifier;
		if (current == null || classifierSeed != seed) {
			synchronized (CLASSIFIER_LOCK) {
				if (classifier == null || classifierSeed != seed) {
					classifier = new GeomeGeology(seed, geomeConfig);
					classifierSeed = seed;
				}
				current = classifier;
			}
		}
		return current;
	}

	private static int attemptsForFrequency(Random random, double frequency) {
		int attempts = (int) frequency;
		if (random.nextDouble() < frequency - attempts) {
			attempts++;
		}
		return attempts;
	}

	private static ResourceLocation resource(String value) {
		try {
			return new ResourceLocation(value);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static boolean bool(JsonObject json, String key, boolean fallback) {
		try {
			return json.has(key) ? json.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static int integer(JsonObject json, String key, int fallback) {
		try {
			return json.has(key) ? json.get(key).getAsInt() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static double decimal(JsonObject json, String key, double fallback) {
		try {
			return json.has(key) ? json.get(key).getAsDouble() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static String string(JsonObject json, String key, String fallback) {
		try {
			return json.has(key) ? json.get(key).getAsString() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static int boundedInteger(JsonObject json, String key, int fallback, int min, int max) {
		return Math.max(min, Math.min(max, integer(json, key, fallback)));
	}

	private static BlockState blockState(JsonObject json, String key, BlockState fallback) {
		if (!json.has(key)) {
			return fallback;
		}
		ResourceLocation id = resource(string(json, key, ""));
		Block block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
		return block == null || block == Blocks.AIR ? null : block.defaultBlockState();
	}

	private static final class BakedOre {
		final BlockState output;
		final BlockState deepOutput;
		final int deepOutputMaxY;
		final BakedOutput[] outputs;
		final int minY;
		final int maxY;
		final double frequency;
		final int minQuantity;
		final int maxQuantity;
		final CompiledOrePattern pattern;
		final OreHeightDistribution heightDistribution;
		final double discardChanceOnAirExposure;
		final int spread;
		final int verticalSpread;
		final int nodeSize;
		final Map<Block, Double> hostBlocks;
		final int familyMask;
		final double[] geomeWeights;
		final Set<Biome> includedBiomes;
		final Set<Biome> excludedBiomes;
		final boolean retrogen;

		BakedOre(BlockState output, BlockState deepOutput, int deepOutputMaxY, BakedOutput[] outputs,
				int minY, int maxY, double frequency, int minQuantity, int maxQuantity,
				CompiledOrePattern pattern, OreHeightDistribution heightDistribution,
				double discardChanceOnAirExposure,
				int spread, int verticalSpread, int nodeSize,
				Map<Block, Double> hostBlocks, int familyMask, double[] geomeWeights,
				Set<Biome> includedBiomes, Set<Biome> excludedBiomes, boolean retrogen) {
			this.output = output;
			this.deepOutput = deepOutput;
			this.deepOutputMaxY = deepOutputMaxY;
			this.outputs = outputs;
			this.minY = minY;
			this.maxY = maxY;
			this.frequency = frequency;
			this.minQuantity = minQuantity;
			this.maxQuantity = maxQuantity;
			this.pattern = pattern;
			this.heightDistribution = heightDistribution;
			this.discardChanceOnAirExposure = discardChanceOnAirExposure;
			this.spread = spread;
			this.verticalSpread = verticalSpread;
			this.nodeSize = nodeSize;
			this.hostBlocks = hostBlocks;
			this.familyMask = familyMask;
			this.geomeWeights = geomeWeights;
			this.includedBiomes = includedBiomes;
			this.excludedBiomes = excludedBiomes;
			this.retrogen = retrogen;
		}

		BlockState outputAt(int y, Random random) {
			if (y <= deepOutputMaxY) return deepOutput;
			double total = 0.0D;
			for (BakedOutput candidate : outputs) if (candidate.acceptsY(y)) total += candidate.weight;
			if (total <= 0.0D) return output;
			double choice = random.nextDouble() * total;
			for (BakedOutput candidate : outputs) {
				if (!candidate.acceptsY(y)) continue;
				choice -= candidate.weight;
				if (choice <= 0.0D) return candidate.state;
			}
			return output;
		}

		boolean accepts(BlockState state, Random random, BakedGeomeConfig config) {
			Double chance = hostBlocks.get(state.getBlock());
			if (chance != null) {
				return chance >= 1.0D || random.nextDouble() < chance;
			}
			RockFamily family = config.familyOf(state);
			return family != null && config.isOreReplaceable(state)
					&& (familyMask & (1 << family.ordinal())) != 0;
		}

		boolean acceptsBiome(Biome biome) {
			return !excludedBiomes.contains(biome)
					&& (includedBiomes.isEmpty() || includedBiomes.contains(biome));
		}
	}

	private static final class BakedOreRule {
		final Block output;
		final boolean suppressVanilla;
		final Map<ResourceKey<Level>, BakedOre> explicit = new HashMap<>();
		final Set<ResourceKey<Level>> explicitDimensions = new HashSet<>();
		BakedOre selector;

		BakedOreRule(Block output, boolean suppressVanilla) {
			this.output = output;
			this.suppressVanilla = suppressVanilla;
		}
	}

	private static final class BakedOutput {
		final BlockState state;
		final double weight;
		final int minY;
		final int maxY;

		BakedOutput(BlockState state, double weight, int minY, int maxY) {
			this.state = state;
			this.weight = weight;
			this.minY = minY;
			this.maxY = maxY;
		}

		boolean acceptsY(int y) {
			return y >= minY && y <= maxY;
		}
	}

	private static final class BakedOres {
		static final BakedOres EMPTY = new BakedOres(EMPTY_DIMENSIONS, Collections.emptyMap(),
				NO_ORES, Collections.emptySet());

		final Map<ResourceKey<Level>, BakedOre[]> byDimension;
		final Map<ResourceKey<Level>, Set<Block>> vanillaOutputs;
		final BakedOre[] selectorOres;
		final Set<Block> selectorVanillaOutputs;

		BakedOres(Map<ResourceKey<Level>, BakedOre[]> byDimension,
				Map<ResourceKey<Level>, Set<Block>> vanillaOutputs, BakedOre[] selectorOres,
				Set<Block> selectorVanillaOutputs) {
			this.byDimension = byDimension;
			this.vanillaOutputs = vanillaOutputs;
			this.selectorOres = selectorOres;
			this.selectorVanillaOutputs = selectorVanillaOutputs;
		}
	}

	private static final class GenerationScratch {
		final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		final PatternContext patternContext = new PatternContext(cursor);
		private double[] geomeValues = new double[0];

		double[] geomeValues(int count) {
			if (geomeValues.length != count) {
				geomeValues = new double[count];
			}
			return geomeValues;
		}
	}

	private static final class PatternContext implements OrePlacementContext {
		private final BlockPos.MutableBlockPos cursor;
		private final BlockPos.MutableBlockPos airCursor = new BlockPos.MutableBlockPos();
		private WorldGenLevel world;
		private ChunkAccess chunk;
		private Random random;
		private BakedOre ore;
		private int geome;
		private int originX;
		private int originY;
		private int originZ;
		private int minY;
		private int maxY;
		private int quantity;

		PatternContext(BlockPos.MutableBlockPos cursor) {
			this.cursor = cursor;
		}

		void initialize(WorldGenLevel world, ChunkAccess chunk, Random random, BakedOre ore, int geome,
				int originX, int originY, int originZ, int minY, int maxY, int quantity) {
			this.world = world;
			this.chunk = chunk;
			this.random = random;
			this.ore = ore;
			this.geome = geome;
			this.originX = originX;
			this.originY = originY;
			this.originZ = originZ;
			this.minY = minY;
			this.maxY = maxY;
			this.quantity = quantity;
		}

		@Override public Random random() { return random; }
		@Override public int originX() { return originX; }
		@Override public int originY() { return originY; }
		@Override public int originZ() { return originZ; }
		@Override public int minY() { return minY; }
		@Override public int maxY() { return maxY; }
		@Override public int quantity() { return quantity; }
		@Override public int spread() { return ore.spread; }
		@Override public int verticalSpread() { return ore.verticalSpread; }
		@Override public int nodeSize() { return ore.nodeSize; }

		@Override
		public boolean inside(int x, int y, int z) {
			if (y < minY || y > maxY || y < chunk.getMinBuildHeight()
					|| y >= chunk.getMaxBuildHeight()) return false;
			if (world == null) return insideChunk(chunk, x, y, z);
			cursor.set(x, y, z);
			return world.ensureCanWrite(cursor);
		}

		@Override
		public boolean isFluid(int x, int y, int z, Fluid fluid) {
			if (!inside(x, y, z)) return false;
			cursor.set(x, y, z);
			BlockState state = world == null ? chunk.getBlockState(cursor) : world.getBlockState(cursor);
			return state.getFluidState().getType() == fluid;
		}

		@Override
		public boolean tryPlace(int x, int y, int z) {
			if (!inside(x, y, z)) return false;
			cursor.set(x, y, z);
			BlockState existing = world == null ? chunk.getBlockState(cursor) : world.getBlockState(cursor);
			if (!ore.accepts(existing, random, geomeConfig)) return false;
			if (ore.discardChanceOnAirExposure > 0.0D
					&& random.nextDouble() < ore.discardChanceOnAirExposure
					&& isAdjacentToAir(x, y, z)) {
				return false;
			}
			BlockState output = ore.outputAt(y, random);
			if (world == null) chunk.setBlockState(cursor, output, false);
			else world.setBlock(cursor, output, 2);
			return true;
		}

		private boolean isAdjacentToAir(int x, int y, int z) {
			return isAir(x + 1, y, z) || isAir(x - 1, y, z)
					|| isAir(x, y + 1, z) || isAir(x, y - 1, z)
					|| isAir(x, y, z + 1) || isAir(x, y, z - 1);
		}

		private boolean isAir(int x, int y, int z) {
			if (y < chunk.getMinBuildHeight() || y >= chunk.getMaxBuildHeight()) {
				return false;
			}
			if (world == null && !insideChunk(chunk, x, y, z)) {
				return false;
			}
			airCursor.set(x, y, z);
			return (world == null ? chunk.getBlockState(airCursor) : world.getBlockState(airCursor)).isAir();
		}
	}

}
