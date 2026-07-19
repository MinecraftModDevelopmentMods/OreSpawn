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
		return !WorldgenBenchmark.isVanillaBaseline() && outputs != null && outputs.contains(output);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel world = context.level();
		ResourceKey<Level> dimension = world.getLevel().dimension();
		BakedOre[] ores = oresByDimension.getOrDefault(dimension, NO_ORES);
		if (ores.length == 0) {
			return false;
		}

		ChunkAccess chunk = world.getChunk(context.origin());
		GenerationScratch scratch = GENERATION_SCRATCH.get();
		setCenter(scratch.cursor, chunk);
		boolean changed = generateChunk(chunk, world.getBiome(scratch.cursor), dimension,
				world.getSeed(), context.random(), ores, false, scratch);
		OreRetrogenManager.markGenerated(dimension, chunk.getPos());
		return changed;
	}

	static boolean retrogen(ServerLevel level, LevelChunk chunk) {
		ResourceKey<Level> dimension = level.dimension();
		BakedOre[] ores = oresByDimension.getOrDefault(dimension, NO_ORES);
		if (ores.length == 0) return false;
		long seed = mix(level.getSeed(), chunk.getPos().toLong(),
				WorldGeologyProfileManager.activeProfile().generationRevision());
		GenerationScratch scratch = GENERATION_SCRATCH.get();
		setCenter(scratch.cursor, chunk);
		return generateChunk(chunk, level.getBiome(scratch.cursor), dimension, level.getSeed(),
				new Random(seed), ores, true, scratch);
	}

	private static void setCenter(BlockPos.MutableBlockPos cursor, ChunkAccess chunk) {
		ChunkPos chunkPos = chunk.getPos();
		cursor.set(chunkPos.getMinBlockX() + 8,
				Math.max(chunk.getMinBuildHeight(), 0), chunkPos.getMinBlockZ() + 8);
	}

	private static boolean generateChunk(ChunkAccess chunk, Holder<Biome> biome,
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
				changed |= placeAttempt(chunk, random, ore, geome, scratch);
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

	private static boolean placeAttempt(ChunkAccess chunk, Random random, BakedOre ore, int geome,
			GenerationScratch scratch) {
		int minY = Math.max(ore.minY, chunk.getMinBuildHeight());
		int maxY = Math.min(ore.maxY, chunk.getMaxBuildHeight() - 1);
		if (maxY < minY) {
			return false;
		}
		int x = chunk.getPos().getMinBlockX() + random.nextInt(16);
		int y = ore.heightDistribution == OreHeightDistribution.TRIANGLE
				? sampleTriangle(random, minY, maxY)
				: minY + random.nextInt((maxY - minY) + 1);
		int z = chunk.getPos().getMinBlockZ() + random.nextInt(16);
		scratch.patternContext.initialize(chunk, random, ore, geome, x, y, z, minY, maxY);
		return ore.pattern.place(scratch.patternContext);
	}

	private static boolean placeDefaultNode(ChunkAccess chunk, Random random, BakedOre ore, int geome,
			int x, int y, int z, GenerationScratch scratch) {
		return placeClusterNode(chunk, random, ore, geome, x, y, z, ore.quantity, scratch);
	}

	private static boolean placeUnderFluid(ChunkAccess chunk, Random random, BakedOre ore, int geome,
			int originX, int originY, int originZ, GenerationScratch scratch) {
		int minY = Math.max(ore.minY, chunk.getMinBuildHeight());
		int maxY = Math.min(ore.maxY, chunk.getMaxBuildHeight() - 1);
		for (int sample = 0; sample < 24; sample++) {
			int x = originX + random.nextInt(5) - 2;
			int y = Math.max(minY, Math.min(maxY, originY + random.nextInt(5) - 2));
			int z = originZ + random.nextInt(5) - 2;
			if (!insideChunk(chunk, x, y, z)) {
				continue;
			}
			scratch.cursor.set(x, y, z);
			if (chunk.getBlockState(scratch.cursor).getFluidState().isEmpty()) {
				continue;
			}
			while (y > minY) {
				scratch.cursor.set(x, y - 1, z);
				if (chunk.getBlockState(scratch.cursor).getFluidState().isEmpty()) {
					break;
				}
				y--;
			}
			return placeClusterNode(chunk, random, ore, geome, x, y - 1, z,
					ore.quantity, scratch);
		}
		return false;
	}

	private static boolean placeClusters(ChunkAccess chunk, Random random, BakedOre ore, int geome,
			int originX, int originY, int originZ, GenerationScratch scratch) {
		int remaining = ore.quantity;
		boolean changed = false;
		while (remaining > 0) {
			int nodeSize = Math.min(ore.nodeSize, remaining);
			int centerX = originX + centeredTriangular(random, ore.spread);
			int centerY = originY + centeredTriangular(random, ore.verticalSpread);
			int centerZ = originZ + centeredTriangular(random, ore.spread);
			changed |= placeClusterNode(chunk, random, ore, geome,
					centerX, centerY, centerZ, nodeSize, scratch);
			remaining -= nodeSize;
		}
		return changed;
	}

	private static boolean placeClusterNode(ChunkAccess chunk, Random random, BakedOre ore, int geome,
			int centerX, int centerY, int centerZ, int targetSize, GenerationScratch scratch) {
		int placed = 0;
		int orientation = random.nextInt(ClusterOffsets.ORIENTATIONS);
		int offsetBase = orientation * ClusterOffsets.COUNT;
		for (int candidate = 0; candidate < targetSize; candidate++) {
			int offset = offsetBase + candidate;
			int x = centerX + ClusterOffsets.X[offset];
			int y = centerY + ClusterOffsets.Y[offset];
			int z = centerZ + ClusterOffsets.Z[offset];
			if (!insideChunk(chunk, x, y, z)) {
				continue;
			}
			scratch.cursor.set(x, y, z);
			BlockState existing = chunk.getBlockState(scratch.cursor);
			if (ore.accepts(existing, random, geomeConfig)) {
				chunk.setBlockState(scratch.cursor, ore.outputAt(y, random), false);
				placed++;
			}
		}
		return placed > 0;
	}

	private static boolean placeCloud(ChunkAccess chunk, Random random, BakedOre ore, int geome,
			int originX, int originY, int originZ, BlockPos.MutableBlockPos cursor) {
		int placed = 0;
		int attempts = ore.quantity * 4;
		for (int attempt = 0; attempt < attempts && placed < ore.quantity; attempt++) {
			int x = originX + centeredTriangular(random, ore.spread);
			int y = originY + centeredTriangular(random, ore.verticalSpread);
			int z = originZ + centeredTriangular(random, ore.spread);
			if (!insideChunk(chunk, x, y, z)) {
				continue;
			}
			cursor.set(x, y, z);
			BlockState existing = chunk.getBlockState(cursor);
			if (ore.accepts(existing, random, geomeConfig)) {
				chunk.setBlockState(cursor, ore.outputAt(cursor.getY(), random), false);
				placed++;
			}
		}
		return placed > 0;
	}

	private static boolean insideChunk(ChunkAccess chunk, int x, int y, int z) {
		ChunkPos pos = chunk.getPos();
		return x >= pos.getMinBlockX() && x <= pos.getMaxBlockX()
				&& z >= pos.getMinBlockZ() && z <= pos.getMaxBlockZ()
				&& y >= chunk.getMinBuildHeight() && y < chunk.getMaxBuildHeight();
	}

	private static int centeredTriangular(Random random, int radius) {
		return radius <= 0 ? 0 : random.nextInt(radius + 1) - random.nextInt(radius + 1);
	}

	private static int sampleTriangle(Random random, int min, int max) {
		int range = (max - min) + 1;
		return min + ((random.nextInt(range) + random.nextInt(range)) / 2);
	}

	private static boolean placeVein(ChunkAccess chunk, Random random, BakedOre ore, int geome,
			int originX, int originY, int originZ, BlockPos.MutableBlockPos cursor) {
		float angle = random.nextFloat() * (float) Math.PI;
		double reach = ore.quantity / 8.0D;
		double startX = originX + Math.sin(angle) * reach;
		double endX = originX - Math.sin(angle) * reach;
		double startZ = originZ + Math.cos(angle) * reach;
		double endZ = originZ - Math.cos(angle) * reach;
		double startY = originY + random.nextInt(3) - 1;
		double endY = originY + random.nextInt(3) - 1;
		boolean changed = false;

		for (int step = 0; step < ore.quantity; step++) {
			double progress = ore.quantity == 1 ? 0.5D : step / (double) (ore.quantity - 1);
			double centerX = lerp(progress, startX, endX);
			double centerY = lerp(progress, startY, endY);
			double centerZ = lerp(progress, startZ, endZ);
			double scale = random.nextDouble() * ore.quantity / 16.0D;
			double diameter = (Math.sin(Math.PI * progress) + 1.0D) * scale + 1.0D;
			double radius = diameter / 2.0D;
			int minX = Math.max(chunk.getPos().getMinBlockX(), (int) Math.floor(centerX - radius));
			int maxX = Math.min(chunk.getPos().getMaxBlockX(), (int) Math.floor(centerX + radius));
			int minY = Math.max(chunk.getMinBuildHeight(), (int) Math.floor(centerY - radius));
			int maxY = Math.min(chunk.getMaxBuildHeight() - 1, (int) Math.floor(centerY + radius));
			int minZ = Math.max(chunk.getPos().getMinBlockZ(), (int) Math.floor(centerZ - radius));
			int maxZ = Math.min(chunk.getPos().getMaxBlockZ(), (int) Math.floor(centerZ + radius));
			double inverseRadius = radius <= 0.0D ? 1.0D : 1.0D / radius;

			for (int x = minX; x <= maxX; x++) {
				double dx = (x + 0.5D - centerX) * inverseRadius;
				double dx2 = dx * dx;
				if (dx2 >= 1.0D) {
					continue;
				}
				for (int y = minY; y <= maxY; y++) {
					double dy = (y + 0.5D - centerY) * inverseRadius;
					double dxy2 = dx2 + (dy * dy);
					if (dxy2 >= 1.0D) {
						continue;
					}
					for (int z = minZ; z <= maxZ; z++) {
						double dz = (z + 0.5D - centerZ) * inverseRadius;
						if (dxy2 + (dz * dz) >= 1.0D) {
							continue;
						}
						cursor.set(x, y, z);
						BlockState existing = chunk.getBlockState(cursor);
						if (ore.accepts(existing, random, geomeConfig)) {
							chunk.setBlockState(cursor, ore.outputAt(cursor.getY(), random), false);
							changed = true;
						}
					}
				}
			}
		}
		return changed;
	}

	private static BakedOres bakeOres(JsonObject profile, BakedGeomeConfig config) {
		if (!profile.has("ores") || !profile.get("ores").isJsonObject()) {
			return BakedOres.EMPTY;
		}
		boolean manageVanillaOres = bool(profile, "manage_vanilla_ores", false);
		Map<TagKey<Block>, Set<Block>> resolvedTags = new HashMap<>();
		Map<ResourceKey<Level>, List<BakedOre>> grouped = new LinkedHashMap<>();
		Map<ResourceKey<Level>, Set<Block>> vanillaOutputs = new LinkedHashMap<>();
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
			if (output == null || output == Blocks.AIR || !oreJson.has("dimensions")
					|| !oreJson.get("dimensions").isJsonObject()) {
				LOGGER.warn("Ignoring invalid OreSpawn-managed ore '{}'", oreEntry.getKey());
				continue;
			}
			BlockState deepOutput = blockState(oreJson, "deep_output", output.defaultBlockState());
			if (deepOutput == null) {
				LOGGER.warn("Ignoring OreSpawn-managed ore '{}' because its deep output is invalid",
						oreEntry.getKey());
				continue;
			}
			int deepOutputMaxY = integer(oreJson, "deep_output_max_y", -1);
			BakedOutput[] outputs = bakeOutputs(oreJson, output.defaultBlockState());

			for (Entry<String, JsonElement> dimensionEntry : oreJson.getAsJsonObject("dimensions").entrySet()) {
				if (!dimensionEntry.getValue().isJsonObject()) {
					continue;
				}
				JsonObject dimension = dimensionEntry.getValue().getAsJsonObject();
				if (!bool(dimension, "enabled", true)) {
					continue;
				}
				ResourceLocation dimensionId = resource(dimensionEntry.getKey());
				if (dimensionId == null) {
					LOGGER.warn("Ignoring invalid dimension '{}' for OreSpawn-managed ore '{}'",
							dimensionEntry.getKey(), oreEntry.getKey());
					continue;
				}
				ResourceKey<Level> dimensionKey = ResourceKey.create(Registry.DIMENSION_REGISTRY, dimensionId);
				BakedOre baked = bakeOre(output.defaultBlockState(), deepOutput, deepOutputMaxY, outputs,
						dimension, config, resolvedTags, retrogen);
				if (baked != null) {
					grouped.computeIfAbsent(dimensionKey, ignored -> new ArrayList<>()).add(baked);
					if (manageVanillaOres && suppressVanilla) {
						vanillaOutputs.computeIfAbsent(dimensionKey,
								ignored -> Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>()))
								.add(output);
					}
				} else {
					LOGGER.warn("Ignoring invalid placement rule for OreSpawn-managed ore '{}' in '{}'",
							oreEntry.getKey(), dimensionEntry.getKey());
				}
			}
		}

		Map<ResourceKey<Level>, BakedOre[]> result = new HashMap<>();
		for (Entry<ResourceKey<Level>, List<BakedOre>> entry : grouped.entrySet()) {
			result.put(entry.getKey(), entry.getValue().toArray(new BakedOre[entry.getValue().size()]));
		}
		LOGGER.info("Baked {} OreSpawn-managed ore definitions across {} dimensions",
				grouped.values().stream().mapToInt(List::size).sum(), result.size());
		Map<ResourceKey<Level>, Set<Block>> immutableVanillaOutputs = new LinkedHashMap<>();
		for (Entry<ResourceKey<Level>, Set<Block>> entry : vanillaOutputs.entrySet()) {
			immutableVanillaOutputs.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
		}
		return new BakedOres(Collections.unmodifiableMap(result),
				Collections.unmodifiableMap(immutableVanillaOutputs));
	}

	private static BakedOre bakeOre(BlockState output, BlockState deepOutput, int deepOutputMaxY,
			BakedOutput[] outputs,
			JsonObject json, BakedGeomeConfig config, Map<TagKey<Block>, Set<Block>> resolvedTags,
			boolean retrogen) {
		int minY = integer(json, "min_y", -64);
		int maxY = integer(json, "max_y", 320);
		double frequency = decimal(json, "frequency", 0.0D);
		int quantity = integer(json, "quantity", 0);
		if (minY > maxY || frequency <= 0.0D || quantity <= 0) {
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
				minY, maxY, Math.min(64.0D, frequency), Math.min(64, quantity),
				pattern, heightDistribution, spread, verticalSpread, nodeSize,
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

	private static double lerp(double value, double start, double end) {
		return start + value * (end - start);
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
		final int quantity;
		final CompiledOrePattern pattern;
		final OreHeightDistribution heightDistribution;
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
				int minY, int maxY, double frequency, int quantity,
				CompiledOrePattern pattern, OreHeightDistribution heightDistribution,
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
			this.quantity = quantity;
			this.pattern = pattern;
			this.heightDistribution = heightDistribution;
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
		static final BakedOres EMPTY = new BakedOres(EMPTY_DIMENSIONS, Collections.emptyMap());

		final Map<ResourceKey<Level>, BakedOre[]> byDimension;
		final Map<ResourceKey<Level>, Set<Block>> vanillaOutputs;

		BakedOres(Map<ResourceKey<Level>, BakedOre[]> byDimension,
				Map<ResourceKey<Level>, Set<Block>> vanillaOutputs) {
			this.byDimension = byDimension;
			this.vanillaOutputs = vanillaOutputs;
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
		private ChunkAccess chunk;
		private Random random;
		private BakedOre ore;
		private int geome;
		private int originX;
		private int originY;
		private int originZ;
		private int minY;
		private int maxY;

		PatternContext(BlockPos.MutableBlockPos cursor) {
			this.cursor = cursor;
		}

		void initialize(ChunkAccess chunk, Random random, BakedOre ore, int geome,
				int originX, int originY, int originZ, int minY, int maxY) {
			this.chunk = chunk;
			this.random = random;
			this.ore = ore;
			this.geome = geome;
			this.originX = originX;
			this.originY = originY;
			this.originZ = originZ;
			this.minY = minY;
			this.maxY = maxY;
		}

		@Override public Random random() { return random; }
		@Override public int originX() { return originX; }
		@Override public int originY() { return originY; }
		@Override public int originZ() { return originZ; }
		@Override public int minY() { return minY; }
		@Override public int maxY() { return maxY; }
		@Override public int quantity() { return ore.quantity; }
		@Override public int spread() { return ore.spread; }
		@Override public int verticalSpread() { return ore.verticalSpread; }
		@Override public int nodeSize() { return ore.nodeSize; }

		@Override
		public boolean inside(int x, int y, int z) {
			return insideChunk(chunk, x, y, z) && y >= minY && y <= maxY;
		}

		@Override
		public boolean isFluid(int x, int y, int z, Fluid fluid) {
			if (!inside(x, y, z)) return false;
			cursor.set(x, y, z);
			return chunk.getBlockState(cursor).getFluidState().getType() == fluid;
		}

		@Override
		public boolean tryPlace(int x, int y, int z) {
			if (!inside(x, y, z)) return false;
			cursor.set(x, y, z);
			BlockState existing = chunk.getBlockState(cursor);
			if (!ore.accepts(existing, random, geomeConfig)) return false;
			chunk.setBlockState(cursor, ore.outputAt(y, random), false);
			return true;
		}
	}

	private static final class ClusterOffsets {
		static final int COUNT = 32;
		static final int ORIENTATIONS = 48;
		static final byte[] X = new byte[COUNT * ORIENTATIONS];
		static final byte[] Y = new byte[COUNT * ORIENTATIONS];
		static final byte[] Z = new byte[COUNT * ORIENTATIONS];

		private static final byte[] BASE_X = {
				0, 1, -1, 0, 0, 0, 0,
				1, 1, -1, -1, 1, 1, -1, -1, 0, 0, 0, 0,
				1, 1, 1, 1, -1, -1, -1, -1,
				2, -2, 0, 0, 0
		};
		private static final byte[] BASE_Y = {
				0, 0, 0, 1, -1, 0, 0,
				1, -1, 1, -1, 0, 0, 0, 0, 1, 1, -1, -1,
				1, 1, -1, -1, 1, 1, -1, -1,
				0, 0, 2, -2, 0
		};
		private static final byte[] BASE_Z = {
				0, 0, 0, 0, 0, 1, -1,
				0, 0, 0, 0, 1, -1, 1, -1, 1, -1, 1, -1,
				1, -1, 1, -1, 1, -1, 1, -1,
				0, 0, 0, 0, 2
		};

		static {
			for (int orientation = 0; orientation < ORIENTATIONS; orientation++) {
				int permutation = orientation % 6;
				int signs = orientation / 6;
				for (int i = 0; i < COUNT; i++) {
					int a = BASE_X[i];
					int b = BASE_Y[i];
					int c = BASE_Z[i];
					int x;
					int y;
					int z;
					switch (permutation) {
						case 1: x = a; y = c; z = b; break;
						case 2: x = b; y = a; z = c; break;
						case 3: x = b; y = c; z = a; break;
						case 4: x = c; y = a; z = b; break;
						case 5: x = c; y = b; z = a; break;
						default: x = a; y = b; z = c; break;
					}
					int index = orientation * COUNT + i;
					X[index] = (byte) ((signs & 1) == 0 ? x : -x);
					Y[index] = (byte) ((signs & 2) == 0 ? y : -y);
					Z[index] = (byte) ((signs & 4) == 0 ? z : -z);
				}
			}
		}

		private ClusterOffsets() { }
	}
}
