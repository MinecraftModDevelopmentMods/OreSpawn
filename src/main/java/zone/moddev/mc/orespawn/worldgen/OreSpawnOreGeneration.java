package zone.moddev.mc.orespawn.worldgen;

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
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.api.CompiledOrePattern;
import zone.moddev.mc.orespawn.api.OreDimensionSelector;
import zone.moddev.mc.orespawn.api.OrePlacementContext;
import zone.moddev.mc.orespawn.init.OreSpawnPatterns;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.biome.Biome;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.WorldGenRegion;
import net.minecraft.world.gen.feature.CompositeFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.placement.IPlacementConfig;
import net.minecraft.fluid.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** One dynamic feature for every OreSpawn-managed ore and dimension. */
public final class OreSpawnOreGeneration extends ContextFeature<NoFeatureConfig> {
	private static final Logger LOGGER = LogManager.getLogger();
	public static final OreSpawnOreGeneration FEATURE = new OreSpawnOreGeneration();
	private static final BakedOre[] NO_ORES = new BakedOre[0];
	private static final Map<ResourceLocation, BakedOre[]> EMPTY_DIMENSIONS = Collections.emptyMap();
	private static final Object CLASSIFIER_LOCK = new Object();

	private static CompositeFeature<?, ?> configuredFeature;
	private static volatile Map<ResourceLocation, BakedOre[]> oresByDimension = EMPTY_DIMENSIONS;
	private static volatile Map<ResourceLocation, Set<Block>> vanillaTakeoverOutputs = Collections.emptyMap();
	private static volatile BakedOre[] selectorOres = NO_ORES;
	private static volatile Set<Block> selectorVanillaTakeoverOutputs = Collections.emptySet();
	private static volatile BakedGeomeConfig geomeConfig;
	private static volatile GeomeGeology classifier;
	private static volatile long classifierSeed = Long.MIN_VALUE;
	private static final ThreadLocal<GenerationScratch> GENERATION_SCRATCH =
			ThreadLocal.withInitial(GenerationScratch::new);

	private OreSpawnOreGeneration() {
		super();
	}

	public static void registerConfiguredFeatures() {
		configuredFeature = net.minecraft.world.biome.Biome.createCompositeFeature(
				FEATURE, new NoFeatureConfig(), net.minecraft.world.biome.Biome.PASSTHROUGH,
				IPlacementConfig.NO_PLACEMENT_CONFIG);
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

	static boolean takesOverVanillaOre(ResourceLocation dimension, Block output) {
		Set<Block> outputs = vanillaTakeoverOutputs.get(dimension);
		if (outputs == null && selectorAllows(dimension)) outputs = selectorVanillaTakeoverOutputs;
		return !WorldgenBenchmark.isVanillaBaseline() && outputs != null && outputs.contains(output);
	}

	static boolean hasManagedOres(ResourceLocation dimension) {
		return oresForDimension(dimension).length != 0;
	}

	static boolean needsVanillaOreGate(ResourceLocation dimension) {
		if (WorldGeologyProfileManager.activeProfile().suppressAllOreFeatures()) return true;
		Set<Block> outputs = vanillaTakeoverOutputs.get(dimension);
		if (outputs == null && selectorAllows(dimension)) outputs = selectorVanillaTakeoverOutputs;
		return outputs != null && !outputs.isEmpty();
	}

	@Override
	boolean place(FeaturePlaceContext<NoFeatureConfig> context) {
		IWorld world = context.level();
		ResourceLocation dimension = WorldIds.dimension(world);
		BakedOre[] ores = oresForDimension(dimension);
		if (ores.length == 0) {
			return false;
		}

		IChunk chunk = context.decorationChunk();
		GenerationScratch scratch = GENERATION_SCRATCH.get();
		setCenter(scratch.cursor, chunk);
		Biome biome = world.getBiome(scratch.cursor);
		ResourceLocation biomeId = WorldIds.biome(biome);
		boolean changed = generateChunk(world, chunk, biome, biomeId, dimension,
				world.getSeed(), context.random(), ores, false, scratch);
		OreRetrogenManager.markGenerated(dimension, chunk.getPos());
		return changed;
	}

	static boolean retrogen(WorldServer level, Chunk chunk) {
		ResourceLocation dimension = WorldIds.dimension(level);
		BakedOre[] ores = oresForDimension(dimension);
		if (ores.length == 0) return false;
		long seed = mix(level.getSeed(), chunk.getPos().asLong(),
				WorldGeologyProfileManager.activeProfile().generationRevision());
		GenerationScratch scratch = GENERATION_SCRATCH.get();
		setCenter(scratch.cursor, chunk);
		Biome biome = level.getBiome(scratch.cursor);
		ResourceLocation biomeId = WorldIds.biome(biome);
		return generateChunk(null, chunk, biome, biomeId, dimension, level.getSeed(),
				new Random(seed), ores, true, scratch);
	}

	private static void setCenter(BlockPos.MutableBlockPos cursor, IChunk chunk) {
		ChunkPos chunkPos = chunk.getPos();
		cursor.setPos(chunkPos.getXStart() + 8, 0, chunkPos.getZStart() + 8);
	}

	private static boolean generateChunk(IWorld world, IChunk chunk, Biome biome,
			ResourceLocation biomeId,
			ResourceLocation dimension, long worldSeed, Random random, BakedOre[] ores,
			boolean retrogenOnly, GenerationScratch scratch) {
		ChunkPos chunkPos = chunk.getPos();
		int centerX = chunkPos.getXStart() + 8;
		int centerZ = chunkPos.getZStart() + 8;
		int geome = -1;
		if (WorldIds.OVERWORLD.equals(dimension)) {
			geome = classifier(worldSeed).classifyColumn(biome, biomeId, centerX, centerZ,
					scratch.geomeValues(geomeConfig.geomeCount()));
		}

		boolean changed = false;
		for (BakedOre ore : ores) {
			if (retrogenOnly && !ore.retrogen) continue;
			if (!ore.acceptsBiome(biome, biomeId)) {
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
			if (chunk instanceof Chunk) ((Chunk) chunk).markDirty();
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

	private static boolean placeAttempt(IWorld world, IChunk chunk, Random random,
			BakedOre ore, int geome,
			GenerationScratch scratch) {
		int minY = Math.max(ore.minY, 0);
		int maxY = Math.min(ore.maxY, 256 - 1);
		if (maxY < minY) {
			return false;
		}
		int x = chunk.getPos().getXStart() + random.nextInt(16);
		int y = ore.heightDistribution.sample(random, minY, maxY);
		int z = chunk.getPos().getZStart() + random.nextInt(16);
		int quantity = sampleQuantity(random, ore.minQuantity, ore.maxQuantity);
		scratch.patternContext.initialize(world, chunk, random, ore, geome, x, y, z, minY, maxY,
				quantity);
		return ore.pattern.place(scratch.patternContext);
	}

	static int sampleQuantity(Random random, int minQuantity, int maxQuantity) {
		return minQuantity == maxQuantity ? minQuantity
				: minQuantity + random.nextInt(maxQuantity - minQuantity + 1);
	}

	static boolean selectorAllows(ResourceLocation dimension) {
		return !WorldIds.NETHER.equals(dimension) && !WorldIds.END.equals(dimension);
	}

	static <T> T selectRule(Map<ResourceLocation, T> explicit,
			Set<ResourceLocation> explicitDimensions, T selector, ResourceLocation dimension) {
		return explicitDimensions.contains(dimension) ? explicit.get(dimension)
				: selectorAllows(dimension) ? selector : null;
	}

	private static BakedOre[] oresForDimension(ResourceLocation dimension) {
		BakedOre[] exact = oresByDimension.get(dimension);
		return exact != null ? exact : selectorAllows(dimension) ? selectorOres : NO_ORES;
	}

	private static boolean insideChunk(IChunk chunk, int x, int y, int z) {
		ChunkPos pos = chunk.getPos();
		return x >= pos.getXStart() && x <= pos.getXEnd()
				&& z >= pos.getZStart() && z <= pos.getZEnd()
				&& y >= 0 && y < 256;
	}

	private static BakedOres bakeOres(JsonObject profile, BakedGeomeConfig config) {
		if (!profile.has("ores") || !profile.get("ores").isJsonObject()) {
			return BakedOres.EMPTY;
		}
		boolean manageVanillaOres = bool(profile, "manage_vanilla_ores", false);
		Map<ResourceLocation, Set<Block>> resolvedTags = new HashMap<>();
		List<BakedOreRule> rules = new ArrayList<>();
		Set<ResourceLocation> explicitDimensions = new HashSet<>();
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
			IBlockState deepOutput = blockState(oreJson, "deep_output", output.getDefaultState());
			if (deepOutput == null) {
				reportBakeProblem("Ignoring OreSpawn-managed ore '{}' because its deep output is invalid",
						oreEntry.getKey());
				continue;
			}
			int deepOutputMaxY = integer(oreJson, "deep_output_max_y", -1);
			BakedOutput[] outputs = bakeOutputs(oreJson, output.getDefaultState());
			BakedOreRule rule = new BakedOreRule(output, manageVanillaOres && suppressVanilla);

			for (Entry<String, JsonElement> dimensionEntry : dimensions.entrySet()) {
				ResourceLocation dimensionId = resource(dimensionEntry.getKey());
				if (dimensionId == null) {
					reportBakeProblem("Ignoring invalid dimension '{}' for OreSpawn-managed ore '{}'",
							dimensionEntry.getKey(), oreEntry.getKey());
					continue;
				}
				rule.explicitDimensions.add(dimensionId);
				explicitDimensions.add(dimensionId);
				if (!dimensionEntry.getValue().isJsonObject()) {
					continue;
				}
				JsonObject dimension = dimensionEntry.getValue().getAsJsonObject();
				if (!bool(dimension, "enabled", true)) {
					continue;
				}
				BakedOre baked = bakeOre(output.getDefaultState(), deepOutput, deepOutputMaxY, outputs,
						dimension, config, resolvedTags, retrogen);
				if (baked != null) {
					rule.explicit.put(dimensionId, baked);
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
				rule.selector = bakeOre(output.getDefaultState(), deepOutput, deepOutputMaxY, outputs,
						selector, config, resolvedTags, retrogen);
				if (rule.selector == null) {
					reportBakeProblem("Ignoring invalid selector rule for OreSpawn-managed ore '{}'",
							oreEntry.getKey());
				}
			}
			rules.add(rule);
		}

		Map<ResourceLocation, BakedOre[]> result = new HashMap<>();
		Map<ResourceLocation, Set<Block>> vanillaOutputs = new HashMap<>();
		for (ResourceLocation dimension : explicitDimensions) {
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
		Map<ResourceLocation, Set<Block>> immutableVanillaOutputs = new LinkedHashMap<>();
		for (Entry<ResourceLocation, Set<Block>> entry : vanillaOutputs.entrySet()) {
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

	static CompositeFeature<?, ?> configuredFeature() {
		return configuredFeature;
	}

	private static JsonObject objectOrEmpty(JsonObject root, String key) {
		return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
	}

	private static BakedOre bakeOre(IBlockState output, IBlockState deepOutput, int deepOutputMaxY,
			BakedOutput[] outputs,
			JsonObject json, BakedGeomeConfig config, Map<ResourceLocation, Set<Block>> resolvedTags,
			boolean retrogen) {
		int minY = integer(json, "min_y", 0);
		int maxY = integer(json, "max_y", 255);
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
		Set<ResourceLocation> includedBiomeIds = resolveBiomeIds(json, "biome_ids");
		Set<ResourceLocation> excludedBiomeIds = resolveBiomeIds(json, "excluded_biome_ids");
		Set<Biome> includedDictionaryBiomes = resolveBiomeDictionary(json, "biome_dictionary");
		Set<Biome> excludedDictionaryBiomes = resolveBiomeDictionary(json,
				"excluded_biome_dictionary");
		return new BakedOre(output, deepOutput, deepOutputMaxY, outputs,
				minY, maxY, Math.min(64.0D, frequency), minQuantity, maxQuantity,
				pattern, heightDistribution, discardChanceOnAirExposure,
				spread, verticalSpread, nodeSize,
				hostBlocks, familyMask, geomeWeights, includedBiomeIds, excludedBiomeIds,
				includedDictionaryBiomes, excludedDictionaryBiomes, retrogen);
	}

	private static BakedOutput[] bakeOutputs(JsonObject ore, IBlockState fallback) {
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
				result.add(new BakedOutput(block.getDefaultState(), weight, minY, maxY));
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
			Map<ResourceLocation, Set<Block>> resolvedTags) {
		if (element == null || !element.isJsonArray()) {
			return;
		}
		for (JsonElement value : element.getAsJsonArray()) {
			JsonObject object = value.isJsonObject() ? value.getAsJsonObject() : null;
			ResourceLocation id = resource(object == null ? value.getAsString() : string(object, "tag", ""));
			if (id == null) {
				continue;
			}
			Set<Block> blocks = resolvedTags.computeIfAbsent(id, OreSpawnOreGeneration::resolveTag);
			double weight = Math.max(0.0D, Math.min(1.0D,
					object == null ? 1.0D : decimal(object, "weight", 1.0D)));
			for (Block block : blocks) target.merge(block, weight, Math::max);
		}
	}

	private static Set<ResourceLocation> resolveBiomeIds(JsonObject rule, String idsKey) {
		Set<ResourceLocation> result = new HashSet<>();
		if (rule.has(idsKey) && rule.get(idsKey).isJsonArray()) {
			for (JsonElement element : rule.getAsJsonArray(idsKey)) {
				ResourceLocation id = resource(element.getAsString());
				if (id != null) result.add(id);
			}
		}
		return result;
	}

	private static Set<Biome> resolveBiomeDictionary(JsonObject rule, String dictionaryKey) {
		Set<Biome> result = Collections.newSetFromMap(new IdentityHashMap<Biome, Boolean>());
		if (rule.has(dictionaryKey) && rule.get(dictionaryKey).isJsonArray()) {
			for (JsonElement element : rule.getAsJsonArray(dictionaryKey)) {
				try {
					for (Biome biome : net.minecraftforge.common.BiomeDictionary.getBiomes(
							net.minecraftforge.common.BiomeDictionary.Type.getType(element.getAsString()))) {
						if (biome != null) result.add(biome);
					}
				} catch (RuntimeException ignored) {
				}
			}
		}
		return result;
	}

	private static Set<Block> resolveTag(ResourceLocation tag) {
		Set<Block> result = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
		result.addAll(BlockTags.getCollection().getOrCreate(tag).getAllElements());
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

	private static IBlockState blockState(JsonObject json, String key, IBlockState fallback) {
		if (!json.has(key)) {
			return fallback;
		}
		ResourceLocation id = resource(string(json, key, ""));
		Block block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
		return block == null || block == Blocks.AIR ? null : block.getDefaultState();
	}

	private static final class BakedOre {
		final IBlockState output;
		final IBlockState deepOutput;
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
		final Set<ResourceLocation> includedBiomeIds;
		final Set<ResourceLocation> excludedBiomeIds;
		final Set<Biome> includedDictionaryBiomes;
		final Set<Biome> excludedDictionaryBiomes;
		final boolean retrogen;

		BakedOre(IBlockState output, IBlockState deepOutput, int deepOutputMaxY, BakedOutput[] outputs,
				int minY, int maxY, double frequency, int minQuantity, int maxQuantity,
				CompiledOrePattern pattern, OreHeightDistribution heightDistribution,
				double discardChanceOnAirExposure,
				int spread, int verticalSpread, int nodeSize,
				Map<Block, Double> hostBlocks, int familyMask, double[] geomeWeights,
				Set<ResourceLocation> includedBiomeIds, Set<ResourceLocation> excludedBiomeIds,
				Set<Biome> includedDictionaryBiomes, Set<Biome> excludedDictionaryBiomes,
				boolean retrogen) {
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
			this.includedBiomeIds = includedBiomeIds;
			this.excludedBiomeIds = excludedBiomeIds;
			this.includedDictionaryBiomes = includedDictionaryBiomes;
			this.excludedDictionaryBiomes = excludedDictionaryBiomes;
			this.retrogen = retrogen;
		}

		IBlockState outputAt(int y, Random random) {
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

		boolean accepts(IBlockState state, Random random, BakedGeomeConfig config) {
			Double chance = hostBlocks.get(state.getBlock());
			if (chance != null) {
				return chance >= 1.0D || random.nextDouble() < chance;
			}
			RockFamily family = config.familyOf(state);
			return family != null && config.isOreReplaceable(state)
					&& (familyMask & (1 << family.ordinal())) != 0;
		}

		boolean acceptsBiome(Biome biome, ResourceLocation biomeId) {
			if (excludedBiomeIds.contains(biomeId) || excludedDictionaryBiomes.contains(biome)) {
				return false;
			}
			return (includedBiomeIds.isEmpty() && includedDictionaryBiomes.isEmpty())
					|| includedBiomeIds.contains(biomeId) || includedDictionaryBiomes.contains(biome);
		}
	}

	private static final class BakedOreRule {
		final Block output;
		final boolean suppressVanilla;
		final Map<ResourceLocation, BakedOre> explicit = new HashMap<>();
		final Set<ResourceLocation> explicitDimensions = new HashSet<>();
		BakedOre selector;

		BakedOreRule(Block output, boolean suppressVanilla) {
			this.output = output;
			this.suppressVanilla = suppressVanilla;
		}
	}

	private static final class BakedOutput {
		final IBlockState state;
		final double weight;
		final int minY;
		final int maxY;

		BakedOutput(IBlockState state, double weight, int minY, int maxY) {
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

		final Map<ResourceLocation, BakedOre[]> byDimension;
		final Map<ResourceLocation, Set<Block>> vanillaOutputs;
		final BakedOre[] selectorOres;
		final Set<Block> selectorVanillaOutputs;

		BakedOres(Map<ResourceLocation, BakedOre[]> byDimension,
				Map<ResourceLocation, Set<Block>> vanillaOutputs, BakedOre[] selectorOres,
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
		private IWorld world;
		private IChunk chunk;
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

		void initialize(IWorld world, IChunk chunk, Random random, BakedOre ore, int geome,
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
			if (y < minY || y > maxY || y < 0
					|| y >= 256) return false;
			if (world == null) return insideChunk(chunk, x, y, z);
			cursor.setPos(x, y, z);
			return world instanceof WorldGenRegion
					? ((WorldGenRegion) world).isChunkInBounds(x >> 4, z >> 4)
					: insideChunk(chunk, x, y, z);
		}

		@Override
		public boolean isFluid(int x, int y, int z, Fluid fluid) {
			if (!inside(x, y, z)) return false;
			cursor.setPos(x, y, z);
			IBlockState state = world == null ? chunk.getBlockState(cursor) : world.getBlockState(cursor);
			return state.getFluidState().getFluid() == fluid;
		}

		@Override
		public boolean tryPlace(int x, int y, int z) {
			if (!inside(x, y, z)) return false;
			cursor.setPos(x, y, z);
			IBlockState existing = world == null ? chunk.getBlockState(cursor) : world.getBlockState(cursor);
			if (!ore.accepts(existing, random, geomeConfig)) return false;
			if (ore.discardChanceOnAirExposure > 0.0D
					&& random.nextDouble() < ore.discardChanceOnAirExposure
					&& isAdjacentToAir(x, y, z)) {
				return false;
			}
			IBlockState output = ore.outputAt(y, random);
			if (world == null) chunk.setBlockState(cursor, output, false);
			else world.setBlockState(cursor, output, 2);
			return true;
		}

		private boolean isAdjacentToAir(int x, int y, int z) {
			return isAir(x + 1, y, z) || isAir(x - 1, y, z)
					|| isAir(x, y + 1, z) || isAir(x, y - 1, z)
					|| isAir(x, y, z + 1) || isAir(x, y, z - 1);
		}

		private boolean isAir(int x, int y, int z) {
			if (y < 0 || y >= 256) {
				return false;
			}
			if (world == null && !insideChunk(chunk, x, y, z)) {
				return false;
			}
			airCursor.setPos(x, y, z);
			return (world == null ? chunk.getBlockState(airCursor) : world.getBlockState(airCursor)).isAir();
		}
	}

}
