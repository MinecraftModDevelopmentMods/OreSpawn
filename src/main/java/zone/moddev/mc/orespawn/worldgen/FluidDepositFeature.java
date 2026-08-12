package zone.moddev.mc.orespawn.worldgen;

import zone.moddev.mc.orespawn.util.JsonCopies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.OreSpawn;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** One allocation-light feature for all provider-owned underground fluid deposits. */
public final class FluidDepositFeature {
	private static final Logger LOGGER = LogManager.getLogger();
	public static final FluidDepositFeature FEATURE = new FluidDepositFeature();
	private static final int CHUNK_WIDTH = 16;
	private static final int GENERATION_WRITE_FLAGS = 2;
	private static final BakedDeposit[] NO_DEPOSITS = new BakedDeposit[0];
	private static final Map<ResourceLocation, BakedDeposit[]> EMPTY_DIMENSIONS = Collections.emptyMap();
	private static final Object CLASSIFIER_LOCK = new Object();

	private static volatile Map<ResourceLocation, BakedDeposit[]> depositsByDimension = EMPTY_DIMENSIONS;
	private static volatile Map<ResourceLocation, BakedGeomeConfig> geomeConfigs = Collections.emptyMap();
	private static volatile Map<ResourceLocation, GeomeGeology> classifiers = Collections.emptyMap();
	private static volatile long classifierSeed = Long.MIN_VALUE;
	private static final ThreadLocal<GenerationScratch> GENERATION_SCRATCH =
			ThreadLocal.withInitial(GenerationScratch::new);

	private FluidDepositFeature() {
	}

	public static void registerConfiguredFeature() {
		refreshWorldConfig();
	}

	public static void refreshWorldConfig() {
		JsonObject profile = WorldGeologyProfileManager.activeProfile().rootCopy();
		depositsByDimension = bakeDeposits(profile);
		Map<ResourceLocation, BakedGeomeConfig> configs = new HashMap<>();
		for (ResourceLocation dimension : depositsByDimension.keySet()) {
			BakedGeomeConfig config = WorldIds.OVERWORLD.equals(dimension)
					? GeomeConfig.baked() : GeomeConfig.baked(dimension);
			if (config != null) configs.put(dimension, config);
		}
		geomeConfigs = Collections.unmodifiableMap(configs);
		synchronized (CLASSIFIER_LOCK) {
			classifiers = Collections.emptyMap();
			classifierSeed = Long.MIN_VALUE;
		}
		GENERATION_SCRATCH.remove();
	}

	static boolean hasDeposits(ResourceLocation dimension) {
		BakedDeposit[] deposits = depositsByDimension.get(dimension);
		return deposits != null && deposits.length != 0;
	}

	boolean generate(World world, Chunk chunk, Random random) {
		ResourceLocation dimension = WorldIds.dimension(world);
		BakedDeposit[] deposits = depositsByDimension.getOrDefault(dimension, NO_DEPOSITS);
		if (deposits.length == 0) return false;

		GenerationScratch scratch = GENERATION_SCRATCH.get();
		ChunkPos chunkPos = ChunkAccessCompat.position(chunk);
		int centerX = chunkPos.getXStart() + 8;
		int centerZ = chunkPos.getZStart() + 8;
		int surfaceY = chunk.getHeightValue(8, 8) - 1;
		scratch.cursor.setPos(centerX, surfaceY, centerZ);
		Biome biome = world.getBiome(scratch.cursor);
		ResourceLocation biomeId = WorldIds.biome(biome);
		BakedGeomeConfig config = geomeConfigs.get(dimension);
		int geome = -1;
		boolean geomeClassified = false;

		boolean changed = false;
		for (BakedDeposit deposit : deposits) {
			if (!deposit.acceptsBiome(biomeId)) continue;
			if (!geomeClassified && deposit.usesGeomeWeights && config != null) {
				geome = classifier(dimension, world.getSeed(), config).classifyColumn(
						biome, biomeId, centerX, centerZ, scratch.geomeValues(config.geomeCount()));
				geomeClassified = true;
			}
			double frequency = geome < 0 ? deposit.frequency : deposit.frequency * deposit.geomeWeights[geome];
			int attempts = attemptsForFrequency(random, frequency);
			for (int attempt = 0; attempt < attempts; attempt++) {
				changed |= placeDeposit(world, chunk, random, deposit, geome, config, scratch.cursor);
			}
		}
		if (changed && chunk instanceof net.minecraft.world.chunk.Chunk) {
			ChunkAccessCompat.markChanged(chunk);
		}
		return changed;
	}

	private static boolean placeDeposit(World world, Chunk chunk, Random random,
			BakedDeposit deposit,
			int geome, BakedGeomeConfig config, BlockPos.MutableBlockPos cursor) {
		// Keep this random-call order aligned with the original Mineralogy crude-oil feature.
		int radius = randomBetween(random, deposit.minRadius, deposit.maxRadius);
		int verticalRadius = randomBetween(random, deposit.minVerticalRadius, deposit.maxVerticalRadius);
		int dx = random.nextInt(CHUNK_WIDTH);
		int dz = random.nextInt(CHUNK_WIDTH);
		int surface = chunk.getHeightValue(dx, dz) - 1;
		int maxCenterY = Math.min(deposit.maxY,
				surface - 1 - Math.max(deposit.minSolidCover, deposit.minSolidShell) - verticalRadius);
		int minCenterY = Math.max(deposit.minY,
				0 + verticalRadius + deposit.minSolidShell);
		if (maxCenterY < minCenterY) return false;

		int centerX = ChunkAccessCompat.position(chunk).getXStart() + dx;
		int centerY = randomBetween(random, minCenterY, maxCenterY);
		int centerZ = ChunkAccessCompat.position(chunk).getZStart() + dz;
		int lobes = randomBetween(random, 1, deposit.maxLobes);
		boolean changed = false;
		for (int lobe = 0; lobe < lobes; lobe++) {
			int lobeX = centerX;
			int lobeY = centerY;
			int lobeZ = centerZ;
			if (lobe > 0) {
				lobeX += randomBetween(random, -radius / 2, radius / 2);
				lobeY += randomBetween(random, -verticalRadius, verticalRadius);
				lobeZ += randomBetween(random, -radius / 2, radius / 2);
			}
			int lobeRadius = Math.max(2, radius - random.nextInt(Math.max(1, (radius / 3) + 1)));
			int lobeVerticalRadius = Math.max(1,
					verticalRadius - random.nextInt(Math.max(1, (verticalRadius / 2) + 1)));
			changed |= placeLobe(world, chunk, deposit, geome, config, cursor, lobeX, lobeY, lobeZ,
					lobeRadius, lobeVerticalRadius);
		}
		return changed;
	}

	private static boolean placeLobe(World world, Chunk chunk, BakedDeposit deposit, int geome,
			BakedGeomeConfig config, BlockPos.MutableBlockPos cursor,
			int centerX, int centerY, int centerZ, int radius, int verticalRadius) {
		int chunkMinX = ChunkAccessCompat.position(chunk).getXStart();
		int chunkMinZ = ChunkAccessCompat.position(chunk).getZStart();
		int minX = Math.max(chunkMinX, centerX - radius);
		int maxX = Math.min(chunkMinX + CHUNK_WIDTH - 1, centerX + radius);
		int minY = Math.max(0, centerY - verticalRadius);
		int maxY = Math.min(256 - 1, centerY + verticalRadius);
		int minZ = Math.max(chunkMinZ, centerZ - radius);
		int maxZ = Math.min(chunkMinZ + CHUNK_WIDTH - 1, centerZ + radius);
		double inverseRadiusSquared = 1.0D / (radius * radius);
		double inverseVerticalRadiusSquared = 1.0D / (verticalRadius * verticalRadius);
		if (!hasSolidEnvelope(world, chunk, deposit, cursor, centerX, centerY, centerZ,
				radius, verticalRadius, minX, maxX, minY, maxY, minZ, maxZ,
				inverseRadiusSquared, inverseVerticalRadiusSquared)) return false;
		boolean changed = false;

		for (int x = minX; x <= maxX; x++) {
			int localX = x - chunkMinX;
			double xDistance = x - centerX;
			for (int z = minZ; z <= maxZ; z++) {
				int localZ = z - chunkMinZ;
				double zDistance = z - centerZ;
				double horizontalShape = ((xDistance * xDistance) + (zDistance * zDistance))
						* inverseRadiusSquared;
				if (horizontalShape > 1.0D) continue;
				int surface = chunk.getHeightValue(localX, localZ) - 1;
				int topLimit = surface - 1 - deposit.minSolidCover;
				for (int y = minY; y <= maxY && y <= topLimit; y++) {
					double yDistance = y - centerY;
					if (horizontalShape + ((yDistance * yDistance) * inverseVerticalRadiusSquared) > 1.0D) {
						continue;
					}
					cursor.setPos(x, y, z);
					IBlockState existing = chunk.getBlockState(cursor);
					if (deposit.accepts(existing, geome, config)) {
						cursor.setPos(x, y, z);
						// Output was validated while baking; keep a final runtime guard for registry oddities.
						if (deposit.output.getBlock() != Blocks.AIR && isFluidBlock(deposit.output)) {
							changed |= world.setBlockState(cursor.toImmutable(), deposit.output,
									GENERATION_WRITE_FLAGS);
						}
					}
				}
			}
		}
		return changed;
	}

	private static boolean hasSolidEnvelope(World world, Chunk chunk,
			BakedDeposit deposit, BlockPos.MutableBlockPos cursor,
			int centerX, int centerY, int centerZ, int radius, int verticalRadius,
			int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
			double inverseRadiusSquared, double inverseVerticalRadiusSquared) {
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				double xDistance = x - centerX;
				double zDistance = z - centerZ;
				double horizontalShape = ((xDistance * xDistance) + (zDistance * zDistance))
						* inverseRadiusSquared;
				if (horizontalShape > 1.0D) continue;
				for (int y = minY; y <= maxY; y++) {
					if (!insideLobe(chunk, deposit, centerX, centerY, centerZ,
							radius, verticalRadius, x, y, z,
							inverseRadiusSquared, inverseVerticalRadiusSquared)) continue;
					cursor.setPos(x, y, z);
					if (!isSealingState(world.getBlockState(cursor), deposit.output)) return false;
					if (!sealedBoundary(world, chunk, deposit, cursor, centerX, centerY, centerZ,
							radius, verticalRadius, x, y, z, -1, 0, 0, deposit.minSolidShell,
							inverseRadiusSquared, inverseVerticalRadiusSquared)
							|| !sealedBoundary(world, chunk, deposit, cursor, centerX, centerY, centerZ,
									radius, verticalRadius, x, y, z, 1, 0, 0, deposit.minSolidShell,
									inverseRadiusSquared, inverseVerticalRadiusSquared)
							|| !sealedBoundary(world, chunk, deposit, cursor, centerX, centerY, centerZ,
									radius, verticalRadius, x, y, z, 0, -1, 0, deposit.minSolidShell,
									inverseRadiusSquared, inverseVerticalRadiusSquared)
							|| !sealedBoundary(world, chunk, deposit, cursor, centerX, centerY, centerZ,
									radius, verticalRadius, x, y, z, 0, 1, 0,
									Math.max(deposit.minSolidShell, deposit.minSolidCover),
									inverseRadiusSquared, inverseVerticalRadiusSquared)
							|| !sealedBoundary(world, chunk, deposit, cursor, centerX, centerY, centerZ,
									radius, verticalRadius, x, y, z, 0, 0, -1, deposit.minSolidShell,
									inverseRadiusSquared, inverseVerticalRadiusSquared)
							|| !sealedBoundary(world, chunk, deposit, cursor, centerX, centerY, centerZ,
									radius, verticalRadius, x, y, z, 0, 0, 1, deposit.minSolidShell,
									inverseRadiusSquared, inverseVerticalRadiusSquared)) return false;
				}
			}
		}
		return true;
	}

	private static boolean sealedBoundary(World world, Chunk chunk,
			BakedDeposit deposit, BlockPos.MutableBlockPos cursor,
			int centerX, int centerY, int centerZ, int radius, int verticalRadius,
			int x, int y, int z, int stepX, int stepY, int stepZ, int thickness,
			double inverseRadiusSquared, double inverseVerticalRadiusSquared) {
		if (thickness == 0 || insideLobe(chunk, deposit, centerX, centerY, centerZ,
				radius, verticalRadius, x + stepX, y + stepY, z + stepZ,
				inverseRadiusSquared, inverseVerticalRadiusSquared)) return true;
		for (int offset = 1; offset <= thickness; offset++) {
			int boundaryX = x + (stepX * offset);
			int boundaryZ = z + (stepZ * offset);
			if (!availableForRead(world, boundaryX, boundaryZ)) return false;
			cursor.setPos(boundaryX, y + (stepY * offset), boundaryZ);
			if (!isSealingState(world.getBlockState(cursor), deposit.output)) return false;
		}
		return true;
	}

	private static boolean availableForRead(World world, int x, int z) {
		return world.isBlockLoaded(new BlockPos(x, 0, z), false);
	}

	private static boolean insideLobe(Chunk chunk, BakedDeposit deposit,
			int centerX, int centerY, int centerZ, int radius, int verticalRadius,
			int x, int y, int z, double inverseRadiusSquared, double inverseVerticalRadiusSquared) {
		int chunkMinX = ChunkAccessCompat.position(chunk).getXStart();
		int chunkMinZ = ChunkAccessCompat.position(chunk).getZStart();
		if (x < chunkMinX || x >= chunkMinX + CHUNK_WIDTH
				|| z < chunkMinZ || z >= chunkMinZ + CHUNK_WIDTH
				|| y < 0 || y >= 256) return false;
		double xDistance = x - centerX;
		double yDistance = y - centerY;
		double zDistance = z - centerZ;
		double shape = ((xDistance * xDistance) + (zDistance * zDistance)) * inverseRadiusSquared
				+ ((yDistance * yDistance) * inverseVerticalRadiusSquared);
		if (shape > 1.0D) return false;
		int surface = chunk.getHeightValue(x - chunkMinX, z - chunkMinZ) - 1;
		return y <= surface - 1 - deposit.minSolidCover;
	}

	static boolean isSealingState(IBlockState state, IBlockState output) {
		return state.getBlock() == output.getBlock()
				|| (state.getMaterial().blocksMovement() && !isFluidBlock(state));
	}

	private static boolean isFluidBlock(IBlockState state) {
		return state.getMaterial().isLiquid() || state.getBlock() instanceof IFluidBlock;
	}

	private static Map<ResourceLocation, BakedDeposit[]> bakeDeposits(JsonObject profile) {
		if (!bool(profile, "place_fluid_deposits", true)
				|| !profile.has("fluid_deposits") || !profile.get("fluid_deposits").isJsonObject()) {
			return EMPTY_DIMENSIONS;
		}
		Map<ResourceLocation, Set<Block>> resolvedTags = new HashMap<>();
		Map<ResourceLocation, List<BakedDeposit>> grouped = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> depositEntry
				: profile.getAsJsonObject("fluid_deposits").entrySet()) {
			if (!depositEntry.getValue().isJsonObject()) continue;
			JsonObject deposit = depositEntry.getValue().getAsJsonObject();
			if (!bool(deposit, "enabled", true)) continue;
			Block output = block(string(deposit, "block", ""));
			IBlockState outputState = output == null ? null : state(output, integer(deposit, "metadata", 0));
			if (output == null || output == Blocks.AIR || !isFluidBlock(outputState)
					|| !deposit.has("dimensions") || !deposit.get("dimensions").isJsonObject()) {
				LOGGER.warn("Ignoring invalid fluid deposit '{}'", depositEntry.getKey());
				continue;
			}
			for (Map.Entry<String, JsonElement> dimensionEntry
					: deposit.getAsJsonObject("dimensions").entrySet()) {
				if (!dimensionEntry.getValue().isJsonObject()) continue;
				JsonObject rule = dimensionEntry.getValue().getAsJsonObject();
				if (!bool(rule, "enabled", true)) continue;
				ResourceLocation dimensionId = resource(dimensionEntry.getKey());
				if (dimensionId == null) continue;
				BakedGeomeConfig config = WorldIds.OVERWORLD.equals(dimensionId)
						? GeomeConfig.baked() : GeomeConfig.baked(dimensionId);
				BakedDeposit baked = bakeDeposit(outputState, rule, config,
						resolvedTags);
				if (baked != null) {
					grouped.computeIfAbsent(dimensionId, ignored -> new ArrayList<>()).add(baked);
				} else {
					LOGGER.warn("Ignoring invalid fluid deposit '{}' in '{}'",
							depositEntry.getKey(), dimensionEntry.getKey());
				}
			}
		}
		Map<ResourceLocation, BakedDeposit[]> result = new HashMap<>();
		for (Map.Entry<ResourceLocation, List<BakedDeposit>> entry : grouped.entrySet()) {
			result.put(entry.getKey(), entry.getValue().toArray(new BakedDeposit[entry.getValue().size()]));
		}
		LOGGER.info("Baked {} fluid deposit definitions across {} dimensions",
				grouped.values().stream().mapToInt(List::size).sum(), result.size());
		return Collections.unmodifiableMap(result);
	}

	private static BakedDeposit bakeDeposit(IBlockState output, JsonObject rule, BakedGeomeConfig config,
			Map<ResourceLocation, Set<Block>> resolvedTags) {
		int minY = integer(rule, "min_y", -48);
		int maxY = integer(rule, "max_y", 48);
		double frequency = decimal(rule, "frequency", 0.0D);
		int minRadius = integer(rule, "min_radius", 5);
		int maxRadius = integer(rule, "max_radius", 12);
		int minVertical = integer(rule, "min_vertical_radius", 2);
		int maxVertical = integer(rule, "max_vertical_radius", 5);
		int maxLobes = integer(rule, "max_lobes", 4);
		int cover = integer(rule, "min_solid_cover", 2);
		int shell = integer(rule, "min_solid_shell", 1);
		if (minY > maxY || frequency < 0.0D || frequency > 64.0D
				|| minRadius < 1 || minRadius > maxRadius || maxRadius > 64
				|| minVertical < 1 || minVertical > maxVertical || maxVertical > 64
				|| maxLobes < 1 || maxLobes > 16 || cover < 0 || cover > 64
				|| shell < 0 || shell > 64) return null;

		Set<Block> hosts = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
		Set<IBlockState> hostStates = new HashSet<>();
		addBlocks(hosts, hostStates, rule.get("host_blocks"));
		addTags(hosts, rule.get("host_tags"), resolvedTags);
		int familyMask = familyMask(rule.get("host_families"));
		if (hosts.isEmpty() && hostStates.isEmpty() && (familyMask == 0 || config == null)) return null;
		int geomeCount = config == null ? 0 : config.geomeCount();
		double[] geomeWeights = new double[geomeCount];
		java.util.Arrays.fill(geomeWeights, 1.0D);
		boolean usesGeomeWeights = config != null && rule.has("geomes")
				&& rule.get("geomes").isJsonObject() && rule.getAsJsonObject("geomes").entrySet().size() > 0;
		if (usesGeomeWeights) {
			for (Map.Entry<String, JsonElement> entry : rule.getAsJsonObject("geomes").entrySet()) {
				int index = config.geomeIndex(entry.getKey());
				if (index >= 0) geomeWeights[index] = Math.max(0.0D, entry.getValue().getAsDouble());
			}
		}
		Set<ResourceLocation> included = resolveBiomes(rule, "biome_ids", "biome_dictionary");
		Set<ResourceLocation> excluded = resolveBiomes(rule, "excluded_biome_ids", "excluded_biome_dictionary");
		return new BakedDeposit(output, minY, maxY, frequency, minRadius, maxRadius,
				minVertical, maxVertical, maxLobes, cover, shell, hosts, hostStates, familyMask,
				geomeWeights, usesGeomeWeights, included, excluded);
	}

	private static int familyMask(JsonElement element) {
		if (element == null || !element.isJsonArray()) return 0;
		int result = 0;
		for (JsonElement value : element.getAsJsonArray()) {
			try {
				result |= 1 << RockFamily.fromConfigName(value.getAsString()).ordinal();
			} catch (RuntimeException ignored) { }
		}
		return result;
	}

	private static void addBlocks(Set<Block> target, Set<IBlockState> exact, JsonElement element) {
		if (element == null || !element.isJsonArray()) return;
		for (JsonElement value : element.getAsJsonArray()) {
			Block block = block(value.isJsonObject()
					? string(value.getAsJsonObject(), "block", "") : value.getAsString());
			if (block != null && block != Blocks.AIR) {
				JsonObject object = value.isJsonObject() ? value.getAsJsonObject() : null;
				if (object != null && object.has("metadata")) exact.add(state(block, integer(object, "metadata", 0)));
				else target.add(block);
			}
		}
	}

	private static void addTags(Set<Block> target, JsonElement element,
			Map<ResourceLocation, Set<Block>> resolvedTags) {
		if (element == null || !element.isJsonArray()) return;
		for (JsonElement value : element.getAsJsonArray()) {
			ResourceLocation id = resource(value.isJsonObject()
					? string(value.getAsJsonObject(), "tag", "") : value.getAsString());
			if (id == null) continue;
			target.addAll(resolvedTags.computeIfAbsent(id, FluidDepositFeature::resolveTag));
		}
	}

	private static Set<Block> resolveTag(ResourceLocation tag) {
		Set<Block> result = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
		String path = tag.getResourcePath();
		if ("stone".equals(path) || "base_stone_overworld".equals(path)) result.add(Blocks.STONE);
		if ("netherrack".equals(path) || "base_stone_nether".equals(path)) result.add(Blocks.NETHERRACK);
		for (ItemStack stack : OreDictionary.getOres(path, false)) {
			Block block = Block.getBlockFromItem(stack.getItem());
			if (block != null && block != Blocks.AIR) result.add(block);
		}
		return result;
	}

	static Set<ResourceLocation> resolveBiomes(JsonObject rule, String idsKey, String dictionaryKey) {
		Set<ResourceLocation> result = new HashSet<>();
		if (rule.has(idsKey) && rule.get(idsKey).isJsonArray()) {
			for (JsonElement element : rule.getAsJsonArray(idsKey)) {
				ResourceLocation id = resource(element.getAsString());
				if (id != null) result.add(id);
			}
		}
		if (rule.has(dictionaryKey) && rule.get(dictionaryKey).isJsonArray()) {
			for (JsonElement element : rule.getAsJsonArray(dictionaryKey)) {
				try {
					for (Biome biome : net.minecraftforge.common.BiomeDictionary.getBiomesForType(
							net.minecraftforge.common.BiomeDictionary.Type.getType(element.getAsString()))) {
						ResourceLocation id = WorldIds.biome(biome);
						if (id != null) result.add(id);
					}
				} catch (RuntimeException ignored) { }
			}
		}
		return result;
	}

	private static GeomeGeology classifier(ResourceLocation dimension, long seed,
			BakedGeomeConfig config) {
		Map<ResourceLocation, GeomeGeology> current = classifiers;
		GeomeGeology result = classifierSeed == seed ? current.get(dimension) : null;
		if (result == null) {
			synchronized (CLASSIFIER_LOCK) {
				if (classifierSeed != seed) {
					classifiers = Collections.emptyMap();
					classifierSeed = seed;
				}
				result = classifiers.get(dimension);
				if (result == null) {
					Map<ResourceLocation, GeomeGeology> updated = new HashMap<>(classifiers);
					result = new GeomeGeology(seed, config);
					updated.put(dimension, result);
					classifiers = Collections.unmodifiableMap(updated);
				}
			}
		}
		return result;
	}

	private static int attemptsForFrequency(Random random, double frequency) {
		int attempts = (int) frequency;
		if (random.nextDouble() < frequency - attempts) attempts++;
		return attempts;
	}

	private static int randomBetween(Random random, int min, int max) {
		return max <= min ? min : min + random.nextInt((max - min) + 1);
	}

	private static Block block(String value) {
		ResourceLocation id = resource(value);
		return id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
	}

	private static IBlockState state(Block block, int metadata) {
		try { return block.getStateFromMeta(Math.max(0, Math.min(15, metadata))); }
		catch (RuntimeException ignored) { return block.getDefaultState(); }
	}

	private static ResourceLocation resource(String value) {
		try { return new ResourceLocation(value); } catch (RuntimeException ignored) { return null; }
	}

	private static boolean bool(JsonObject json, String key, boolean fallback) {
		try { return json.has(key) ? json.get(key).getAsBoolean() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static int integer(JsonObject json, String key, int fallback) {
		try { return json.has(key) ? json.get(key).getAsInt() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static double decimal(JsonObject json, String key, double fallback) {
		try { return json.has(key) ? json.get(key).getAsDouble() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static String string(JsonObject json, String key, String fallback) {
		try { return json.has(key) ? json.get(key).getAsString() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static final class BakedDeposit {
		final IBlockState output;
		final int minY;
		final int maxY;
		final double frequency;
		final int minRadius;
		final int maxRadius;
		final int minVerticalRadius;
		final int maxVerticalRadius;
		final int maxLobes;
		final int minSolidCover;
		final int minSolidShell;
		final Set<Block> hostBlocks;
		final Set<IBlockState> hostStates;
		final int familyMask;
		final double[] geomeWeights;
		final boolean usesGeomeWeights;
		final Set<ResourceLocation> includedBiomes;
		final Set<ResourceLocation> excludedBiomes;

		BakedDeposit(IBlockState output, int minY, int maxY, double frequency,
				int minRadius, int maxRadius, int minVerticalRadius, int maxVerticalRadius,
				int maxLobes, int minSolidCover, int minSolidShell,
				Set<Block> hostBlocks, Set<IBlockState> hostStates, int familyMask,
				double[] geomeWeights, boolean usesGeomeWeights,
				Set<ResourceLocation> includedBiomes, Set<ResourceLocation> excludedBiomes) {
			this.output = output;
			this.minY = minY;
			this.maxY = maxY;
			this.frequency = frequency;
			this.minRadius = minRadius;
			this.maxRadius = maxRadius;
			this.minVerticalRadius = minVerticalRadius;
			this.maxVerticalRadius = maxVerticalRadius;
			this.maxLobes = maxLobes;
			this.minSolidCover = minSolidCover;
			this.minSolidShell = minSolidShell;
			this.hostBlocks = hostBlocks;
			this.hostStates = hostStates;
			this.familyMask = familyMask;
			this.geomeWeights = geomeWeights;
			this.usesGeomeWeights = usesGeomeWeights;
			this.includedBiomes = includedBiomes;
			this.excludedBiomes = excludedBiomes;
		}

		boolean accepts(IBlockState state, int geome, BakedGeomeConfig config) {
			if (hostStates.contains(state) || hostBlocks.contains(state.getBlock())) return true;
			if (config == null || familyMask == 0) return false;
			RockFamily family = config.familyOf(state);
			return family != null && (familyMask & (1 << family.ordinal())) != 0;
		}

		boolean acceptsBiome(ResourceLocation biome) {
			if (biome == null) return includedBiomes.isEmpty() && excludedBiomes.isEmpty();
			return !excludedBiomes.contains(biome)
					&& (includedBiomes.isEmpty() || includedBiomes.contains(biome));
		}
	}

	private static final class GenerationScratch {
		final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		private double[] geomeValues = new double[0];
		double[] geomeValues(int count) {
			if (geomeValues.length != count) geomeValues = new double[count];
			return geomeValues;
		}
	}
}
