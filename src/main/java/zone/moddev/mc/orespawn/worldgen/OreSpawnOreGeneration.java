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
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.api.CompiledOrePattern;
import zone.moddev.mc.orespawn.api.GeologySampler;
import zone.moddev.mc.orespawn.api.OreDimensionSelector;
import zone.moddev.mc.orespawn.api.OreGenerationContext;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.orespawn.init.OreSpawnPatterns;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** One dynamic feature for every OreSpawn-managed ore and dimension. */
public final class OreSpawnOreGeneration {
	/** Forge 1.10 worldgen write: send the update without triggering neighbour updates. */
	private static final int GENERATION_WRITE_FLAGS = 2;
	private static final Logger LOGGER = LogManager.getLogger();
	public static final OreSpawnOreGeneration FEATURE = new OreSpawnOreGeneration();
	private static final BakedOre[] NO_ORES = new BakedOre[0];
	private static final Map<ResourceLocation, BakedOre[]> EMPTY_DIMENSIONS = Collections.emptyMap();
	private static final Object CLASSIFIER_LOCK = new Object();

	private static volatile Map<ResourceLocation, BakedOre[]> oresByDimension = EMPTY_DIMENSIONS;
	private static volatile Map<ResourceLocation, Set<Block>> vanillaTakeoverOutputs = Collections.emptyMap();
	private static volatile Map<ResourceLocation, Map<Block, Double>> backgroundGenerationScales =
			Collections.emptyMap();
	private static volatile Map<ResourceLocation, Map<ResourceLocation, Double>> materialBackgroundGenerationScales =
			Collections.emptyMap();
	private static volatile BakedOre[] selectorOres = NO_ORES;
	private static volatile Set<Block> selectorVanillaTakeoverOutputs = Collections.emptySet();
	private static volatile Map<Block, Double> selectorBackgroundGenerationScales = Collections.emptyMap();
	private static volatile Map<ResourceLocation, Double> selectorMaterialBackgroundGenerationScales =
			Collections.emptyMap();
	private static volatile BakedGeomeConfig geomeConfig;
	private static volatile GeomeGeology classifier;
	private static volatile long classifierSeed = Long.MIN_VALUE;
	private static final ThreadLocal<GenerationScratch> GENERATION_SCRATCH =
			ThreadLocal.withInitial(GenerationScratch::new);

	private OreSpawnOreGeneration() {
	}

	public static void registerConfiguredFeatures() {
		refreshWorldConfig();
	}

	public static void refreshWorldConfig() {
		geomeConfig = GeomeConfig.baked();
		BakedOres baked = bakeOres(WorldGeologyProfileManager.activeProfile().rootCopy(), geomeConfig);
		oresByDimension = baked.byDimension;
		vanillaTakeoverOutputs = baked.vanillaOutputs;
		backgroundGenerationScales = baked.backgroundGenerationScales;
		materialBackgroundGenerationScales = baked.materialBackgroundGenerationScales;
		selectorOres = baked.selectorOres;
		selectorVanillaTakeoverOutputs = baked.selectorVanillaOutputs;
		selectorBackgroundGenerationScales = baked.selectorBackgroundGenerationScales;
		selectorMaterialBackgroundGenerationScales = baked.selectorMaterialBackgroundGenerationScales;
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

	static boolean allowsVanillaOre(ResourceLocation dimension, Block output, long worldSeed,
			int chunkX, int chunkZ) {
		if (takesOverVanillaOre(dimension, output)) return false;
		double scale = backgroundScale(dimension, output);
		if (scale >= 1.0D) return true;
		if (scale <= 0.0D) return false;
		ResourceLocation outputId = ForgeRegistries.BLOCKS.getKey(output);
		return passesBackgroundScale(dimension, outputId, worldSeed, chunkX, chunkZ, scale);
	}

	static boolean passesBackgroundScale(ResourceLocation dimension, ResourceLocation outputId,
			long worldSeed, int chunkX, int chunkZ, double scale) {
		if (scale >= 1.0D) return true;
		if (scale <= 0.0D) return false;
		long identity = ((long) dimension.toString().hashCode() << 32)
				^ (outputId == null ? 0L : outputId.toString().hashCode() & 0xFFFFFFFFL);
		long chunk = ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
		long value = mix(worldSeed, chunk, identity);
		double sample = (double) (value >>> 11) * 0x1.0p-53;
		return sample < scale;
	}

	static boolean hasManagedOres(ResourceLocation dimension) {
		return oresForDimension(dimension).length != 0;
	}

	static boolean needsVanillaOreGate(ResourceLocation dimension) {
		if (WorldGeologyProfileManager.activeProfile().suppressAllOreFeatures()) return true;
		Set<Block> outputs = vanillaTakeoverOutputs.get(dimension);
		if (outputs == null && selectorAllows(dimension)) outputs = selectorVanillaTakeoverOutputs;
		Map<Block, Double> scales = scalesForDimension(dimension);
		return (outputs != null && !outputs.isEmpty()) || !scales.isEmpty();
	}

	boolean generate(World world, Chunk chunk, Random random) {
		ResourceLocation dimension = WorldIds.dimension(world);
		BakedOre[] ores = oresForDimension(dimension);
		if (ores.length == 0) {
			return false;
		}

		GenerationScratch scratch = GENERATION_SCRATCH.get();
		setCenter(scratch.cursor, chunk);
		Biome biome = world.getBiome(scratch.cursor);
		ResourceLocation biomeId = WorldIds.biome(biome);
		WorldServer level = world instanceof WorldServer ? (WorldServer) world : null;
		boolean changed = generateChunk(level, world, chunk, biome, biomeId, dimension,
				world.getSeed(), random, ores, false, scratch);
		OreRetrogenManager.markGenerated(dimension, ChunkAccessCompat.position(chunk));
		return changed;
	}

	static boolean retrogen(WorldServer level, Chunk chunk) {
		ResourceLocation dimension = WorldIds.dimension(level);
		BakedOre[] ores = oresForDimension(dimension);
		if (ores.length == 0) return false;
		long seed = mix(level.getSeed(), chunkKey(ChunkAccessCompat.position(chunk)),
				WorldGeologyProfileManager.activeProfile().generationRevision());
		GenerationScratch scratch = GENERATION_SCRATCH.get();
		setCenter(scratch.cursor, chunk);
		Biome biome = level.getBiome(scratch.cursor);
		ResourceLocation biomeId = WorldIds.biome(biome);
		return generateChunk(level, null, chunk, biome, biomeId, dimension, level.getSeed(),
				new Random(seed), ores, true, scratch);
	}

	private static void setCenter(BlockPos.MutableBlockPos cursor, Chunk chunk) {
		ChunkPos chunkPos = ChunkAccessCompat.position(chunk);
		cursor.setPos(chunkPos.getXStart() + 8, 0, chunkPos.getZStart() + 8);
	}

	private static boolean generateChunk(WorldServer level, World world, Chunk chunk, Biome biome,
			ResourceLocation biomeId,
			ResourceLocation dimension, long worldSeed, Random random, BakedOre[] ores,
			boolean retrogenOnly, GenerationScratch scratch) {
		ChunkPos chunkPos = ChunkAccessCompat.position(chunk);
		int centerX = chunkPos.getXStart() + 8;
		int centerZ = chunkPos.getZStart() + 8;
		Optional<GeologySampler> geologySampler = scratch.geologySampler(level, dimension);
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
			ResourceLocation scalingMaterial = ore.materialDeclared ? ore.material : null;
			double scale = channelBackgroundScale(scalingMaterial, ore.channel,
					materialBackgroundScale(dimension, scalingMaterial),
					backgroundScale(dimension, ore.output.getBlock()));
			double frequency = scaledManagedFrequency(ore.frequency, ore.backgroundController, scale);
			if (geome >= 0) {
				frequency *= ore.geomeWeights[geome];
			}
			int attempts = attemptsForFrequency(random, frequency);
			for (int attempt = 0; attempt < attempts; attempt++) {
				changed |= placeAttempt(world, chunk, random, ore, geome, dimension,
						worldSeed, attempt, geologySampler, scratch);
			}
		}
		if (changed) {
			ChunkAccessCompat.markChanged(chunk);
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

	static long ordinaryOutputIdentity(ChunkPos chunk, int attemptIndex,
			ResourceLocation ruleId, ResourceLocation channel) {
		return mix(chunkKey(chunk), attemptIndex,
				((long) ruleId.hashCode() << 32) ^ (channel.hashCode() & 0xFFFFFFFFL));
	}

	static double outputSelectionSample(long worldSeed, ResourceLocation dimension,
			ResourceLocation material, ResourceLocation channel, long outputIdentity) {
		long value = mix(worldSeed, outputIdentity,
				((long) material.hashCode() << 32)
						^ (channel.hashCode() & 0xFFFFFFFFL) ^ dimension.hashCode());
		return (double) (value >>> 11) * 0x1.0p-53;
	}

	private static boolean placeAttempt(World world, Chunk chunk, Random random,
			BakedOre ore, int geome, ResourceLocation dimension, long worldSeed,
			int attemptIndex,
			Optional<GeologySampler> geologySampler,
			GenerationScratch scratch) {
		int minY = Math.max(ore.minY, 0);
		int maxY = Math.min(ore.maxY, 256 - 1);
		if (maxY < minY) {
			return false;
		}
		int x = ChunkAccessCompat.position(chunk).getXStart() + random.nextInt(16);
		int y = ore.heightDistribution.sample(random, minY, maxY);
		int z = ChunkAccessCompat.position(chunk).getZStart() + random.nextInt(16);
		int quantity = sampleQuantity(random, ore.minQuantity, ore.maxQuantity);
		scratch.patternContext.initialize(world, chunk, random, ore, geome, dimension, worldSeed,
				attemptIndex, geologySampler, x, y, z, minY, maxY, quantity);
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

	private static Map<Block, Double> scalesForDimension(ResourceLocation dimension) {
		Map<Block, Double> exact = backgroundGenerationScales.get(dimension);
		return exact != null ? exact
				: selectorAllows(dimension) ? selectorBackgroundGenerationScales : Collections.emptyMap();
	}

	private static double backgroundScale(ResourceLocation dimension, Block output) {
		Double scale = scalesForDimension(dimension).get(output);
		return scale == null ? 1.0D : scale.doubleValue();
	}

	private static double materialBackgroundScale(ResourceLocation dimension, ResourceLocation material) {
		if (material == null) return 1.0D;
		Map<ResourceLocation, Double> exact = materialBackgroundGenerationScales.get(dimension);
		if (exact == null && selectorAllows(dimension)) exact = selectorMaterialBackgroundGenerationScales;
		Double scale = exact == null ? null : exact.get(material);
		return scale == null ? 1.0D : scale.doubleValue();
	}

	private static boolean insideChunk(Chunk chunk, int x, int y, int z) {
		return insideChunk(ChunkAccessCompat.position(chunk), x, y, z);
	}

	static boolean insideChunk(ChunkPos pos, int x, int y, int z) {
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
		Map<String, BakedOutputBundle> outputBundles = new LinkedHashMap<>();
		Set<ResourceLocation> explicitDimensions = new HashSet<>();
		for (Entry<String, JsonElement> oreEntry : profile.getAsJsonObject("ores").entrySet()) {
			if (!oreEntry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject oreJson = oreEntry.getValue().getAsJsonObject();
			boolean oreEnabled = bool(oreJson, "enabled", true);
			boolean nativeGeneration = bool(oreJson, "native_generation", false);
			boolean retrogen = bool(oreJson, "retrogen", true);
			boolean suppressVanilla = nativeGeneration || bool(oreJson, "suppress_vanilla", false);
			ResourceLocation oreId = resource(string(oreJson, "block", oreEntry.getKey()));
			Block output = oreId == null ? null : ForgeRegistries.BLOCKS.getValue(oreId);
			JsonObject dimensions = objectOrEmpty(oreJson, "dimensions");
			JsonObject selectors = objectOrEmpty(oreJson, "dimension_selectors");
			if (output == null || output == Blocks.AIR || (dimensions.entrySet().size() == 0 && selectors.entrySet().size() == 0)) {
				reportBakeProblem("Ignoring invalid OreSpawn-managed ore '{}'", oreEntry.getKey());
				continue;
			}
			IBlockState outputState = state(output, integer(oreJson, "metadata", 0));
			IBlockState deepOutput = blockState(oreJson, "deep_output", "deep_output_metadata", outputState);
			if (deepOutput == null) {
				reportBakeProblem("Ignoring OreSpawn-managed ore '{}' because its deep output is invalid",
						oreEntry.getKey());
				continue;
			}
			int deepOutputMaxY = integer(oreJson, "deep_output_max_y", -1);
			BakedOutput[] outputs = bakeOutputs(oreJson, outputState);
			ResourceLocation ruleId = resource(oreEntry.getKey());
			if (ruleId == null) ruleId = oreId;
			outputBundles.put(ruleId.toString(), new BakedOutputBundle(
					outputState, deepOutput, deepOutputMaxY, outputs));
			if (!oreEnabled || (nativeGeneration && !manageVanillaOres)) continue;
			ResourceLocation material = OreSourcePolicies.material(profile, oreJson,
					new ItemStack(output, 1, integer(oreJson, "metadata", 0)));
			boolean materialDeclared = resource(string(oreJson, "material", "")) != null;
			BakedOreRule rule = new BakedOreRule(ruleId, output, material, materialDeclared,
					manageVanillaOres && suppressVanilla);

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
				BakedOre baked = bakeOre(ruleId, material, materialDeclared, dimensionId,
						outputState, deepOutput,
						deepOutputMaxY, outputs, dimension, config, resolvedTags, retrogen);
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
				rule.selector = bakeOre(ruleId, material, materialDeclared, selectorId,
						outputState, deepOutput,
						deepOutputMaxY, outputs, selector, config, resolvedTags, retrogen);
				if (rule.selector == null) {
					reportBakeProblem("Ignoring invalid selector rule for OreSpawn-managed ore '{}'",
							oreEntry.getKey());
				}
			}
			rules.add(rule);
		}

		Map<ResourceLocation, BakedOre[]> result = new HashMap<>();
		Map<ResourceLocation, Set<Block>> vanillaOutputs = new HashMap<>();
		Map<ResourceLocation, Map<Block, Double>> generationScales = new HashMap<>();
		Map<ResourceLocation, Map<ResourceLocation, Double>> materialScales = new HashMap<>();
		for (ResourceLocation dimension : explicitDimensions) {
			List<BakedOre> combined = new ArrayList<>();
			Set<Block> suppressed = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
			Map<Block, Double> scales = new IdentityHashMap<>();
			Map<ResourceLocation, Double> semanticScales = new HashMap<>();
			for (BakedOreRule rule : rules) {
				BakedOre selected = selectRule(rule.explicit, rule.explicitDimensions,
						rule.selector, dimension);
				if (selected != null) {
					combined.add(selected);
					if (rule.suppressVanilla) suppressed.add(rule.output);
					if (selected.backgroundController) {
						mergeBackgroundScale(scales, rule.output, selected.backgroundGenerationScale);
						if (rule.materialDeclared) mergeBackgroundScale(semanticScales,
								rule.material, selected.backgroundGenerationScale);
					}
				}
			}
			combined = arbitrate(profile, combined, outputBundles);
			result.put(dimension, combined.toArray(new BakedOre[combined.size()]));
			vanillaOutputs.put(dimension, Collections.unmodifiableSet(suppressed));
			generationScales.put(dimension, Collections.unmodifiableMap(scales));
			materialScales.put(dimension, Collections.unmodifiableMap(semanticScales));
		}
		List<BakedOre> selectorList = new ArrayList<>();
		Set<Block> selectorOutputs = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
		Map<Block, Double> selectorScales = new IdentityHashMap<>();
		Map<ResourceLocation, Double> selectorMaterialScales = new HashMap<>();
		for (BakedOreRule rule : rules) {
			if (rule.selector == null) continue;
			selectorList.add(rule.selector);
			if (rule.suppressVanilla) selectorOutputs.add(rule.output);
			if (rule.selector.backgroundController) {
				mergeBackgroundScale(selectorScales, rule.output,
						rule.selector.backgroundGenerationScale);
				if (rule.materialDeclared) mergeBackgroundScale(selectorMaterialScales,
						rule.material, rule.selector.backgroundGenerationScale);
			}
		}
		selectorList = arbitrate(profile, selectorList, outputBundles);
		BakedOre[] selectorResult = selectorList.toArray(new BakedOre[selectorList.size()]);
		LOGGER.info("Baked {} OreSpawn-managed ore definitions across {} dimensions",
				rules.size(), result.size());
		Map<ResourceLocation, Set<Block>> immutableVanillaOutputs = new LinkedHashMap<>();
		for (Entry<ResourceLocation, Set<Block>> entry : vanillaOutputs.entrySet()) {
			immutableVanillaOutputs.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
		}
		return new BakedOres(Collections.unmodifiableMap(result),
				Collections.unmodifiableMap(immutableVanillaOutputs),
				Collections.unmodifiableMap(generationScales),
				Collections.unmodifiableMap(materialScales), selectorResult,
				Collections.unmodifiableSet(selectorOutputs),
				Collections.unmodifiableMap(selectorScales),
				Collections.unmodifiableMap(selectorMaterialScales));
	}

	private static List<BakedOre> arbitrate(JsonObject profile, List<BakedOre> original,
			Map<String, BakedOutputBundle> outputBundles) {
		if (!profile.has(OreSourcePolicies.SECTION)
				|| !profile.get(OreSourcePolicies.SECTION).isJsonObject()) return original;
		JsonObject policies = profile.getAsJsonObject(OreSourcePolicies.SECTION);
		Map<String, List<BakedOre>> groups = new LinkedHashMap<>();
		for (BakedOre ore : original) {
			if (ore.material != null) groups.computeIfAbsent(
					OreSourcePolicies.key(ore.material, ore.policyDomain),
					ignored -> new ArrayList<>()).add(ore);
		}
		Map<String, List<BakedOre>> replacements = new HashMap<>();
		for (Entry<String, List<BakedOre>> entry : groups.entrySet()) {
			BakedOre first = entry.getValue().get(0);
			String key = entry.getKey();
			if (!policies.has(key) || !policies.get(key).isJsonObject()) continue;
			JsonObject policy = policies.getAsJsonObject(key);
			if (!"consolidated".equals(string(policy, "mode", "keep_separate"))) continue;
			Map<String, Double> weights = positiveWeights(objectOrEmpty(policy, "outputs"));
			List<BakedSource> sources = new ArrayList<>();
			Set<String> sourceIds = new HashSet<>();
			for (Entry<String, Double> selectedOutput : weights.entrySet()) {
				BakedOutputBundle bundle = outputBundles.get(selectedOutput.getKey());
				if (bundle == null) bundle = candidateBundle(policy, selectedOutput.getKey());
				if (bundle != null && sourceIds.add(selectedOutput.getKey())) {
					sources.add(new BakedSource(selectedOutput.getKey(), bundle,
							selectedOutput.getValue().doubleValue()));
				}
			}
			if (sources.isEmpty()) {
				replacements.put(key, Collections.emptyList());
				continue;
			}
			Map<ResourceLocation, ResourceLocation> available = new LinkedHashMap<>();
			for (BakedOre candidate : entry.getValue()) available.put(candidate.ruleId, candidate.channel);
			Map<ResourceLocation, ResourceLocation> placements = selectedPlacementRules(
					profile, first.material, first.policyDomain, available);
			List<BakedOre> selected = new ArrayList<>();
			for (Entry<ResourceLocation, ResourceLocation> placement : placements.entrySet()) {
				ResourceLocation channel = placement.getKey();
				ResourceLocation source = placement.getValue();
				for (BakedOre candidate : entry.getValue()) {
					if (channel.equals(candidate.channel) && source.equals(candidate.ruleId)) {
						selected.add(candidate.withSources(sources));
						break;
					}
				}
			}
			replacements.put(key, selected);
		}
		if (replacements.isEmpty()) return original;
		List<BakedOre> result = new ArrayList<>();
		Set<String> emitted = new HashSet<>();
		for (BakedOre ore : original) {
			String groupKey = ore.material == null ? null
					: OreSourcePolicies.key(ore.material, ore.policyDomain);
			List<BakedOre> replacement = groupKey == null ? null : replacements.get(groupKey);
			if (replacement == null) result.add(ore);
			else if (emitted.add(groupKey)) result.addAll(replacement);
		}
		return result;
	}

	/** Returns null for the behaviour-preserving separate mode. */
	static Map<ResourceLocation, ResourceLocation> selectedPlacementRules(JsonObject profile,
			ResourceLocation material, ResourceLocation domain,
			Map<ResourceLocation, ResourceLocation> availableSourceChannels) {
		if (!profile.has(OreSourcePolicies.SECTION)
				|| !profile.get(OreSourcePolicies.SECTION).isJsonObject()) return null;
		JsonObject policies = profile.getAsJsonObject(OreSourcePolicies.SECTION);
		String key = OreSourcePolicies.key(material, domain);
		if (!policies.has(key) || !policies.get(key).isJsonObject()) return null;
		JsonObject policy = policies.getAsJsonObject(key);
		if (!"consolidated".equals(string(policy, "mode", "keep_separate"))) return null;
		Map<ResourceLocation, ResourceLocation> result = new LinkedHashMap<>();
		for (Entry<String, JsonElement> placement : objectOrEmpty(
				policy, "placement_sources").entrySet()) {
			ResourceLocation channel = resource(placement.getKey());
			ResourceLocation source;
			try { source = resource(placement.getValue().getAsString()); }
			catch (RuntimeException invalid) { continue; }
			if (channel != null && source != null && channel.equals(availableSourceChannels.get(source))) {
				result.put(channel, source);
			}
		}
		return Collections.unmodifiableMap(result);
	}

	static int weightedIndex(double sample, double... weights) {
		double total = 0.0D;
		for (double weight : weights) if (Double.isFinite(weight) && weight > 0.0D) total += weight;
		if (total <= 0.0D) return -1;
		double choice = weightedChoice(sample, total);
		int last = -1;
		for (int index = 0; index < weights.length; index++) {
			double weight = weights[index];
			if (!Double.isFinite(weight) || weight <= 0.0D) continue;
			last = index;
			choice -= weight;
			if (choice < 0.0D) return index;
		}
		return last;
	}

	private static double weightedChoice(double sample, double total) {
		return Math.max(0.0D, Math.min(Math.nextDown(1.0D), sample)) * total;
	}

	private static Map<String, Double> positiveWeights(JsonObject values) {
		Map<String, Double> result = new LinkedHashMap<>();
		for (Entry<String, JsonElement> entry : values.entrySet()) {
			try {
				double value = entry.getValue().getAsDouble();
				if (Double.isFinite(value) && value > 0.0D) result.put(entry.getKey(), value);
			} catch (RuntimeException ignored) { }
		}
		return result;
	}

	private static BakedOutputBundle candidateBundle(JsonObject policy, String sourceId) {
		if (!policy.has("candidates") || !policy.get("candidates").isJsonArray()) return null;
		for (JsonElement element : policy.getAsJsonArray("candidates")) {
			if (!element.isJsonObject()) continue;
			JsonObject candidate = element.getAsJsonObject();
			if (!sourceId.equals(string(candidate, "source_id", ""))
					|| !bool(candidate, "loaded", false)
					|| bool(candidate, "enrichment", false)) continue;
			ResourceLocation id = resource(string(candidate, "registry_id", ""));
			Block block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
			if (block == null || block == Blocks.AIR) return null;
			IBlockState state = state(block, integer(candidate, "metadata", 0));
			return new BakedOutputBundle(state, state, -1,
					new BakedOutput[] { new BakedOutput(state, 1.0D,
							Integer.MIN_VALUE, Integer.MAX_VALUE) });
		}
		return null;
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

	static double scaledManagedFrequency(double frequency, boolean controller, double scale) {
		return controller ? frequency : frequency * scale;
	}

	static double channelBackgroundScale(ResourceLocation material, ResourceLocation channel,
			double materialScale, double legacyBlockScale) {
		if (material == null) return legacyBlockScale;
		return OreSourcePolicies.STANDARD.equals(channel) ? materialScale : 1.0D;
	}

	static <T> void mergeBackgroundScale(Map<T, Double> scales, T output, double scale) {
		Double previous = scales.get(output);
		if (previous == null || scale < previous.doubleValue()) scales.put(output, scale);
	}

	private static JsonObject objectOrEmpty(JsonObject root, String key) {
		return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
	}

	private static BakedOre bakeOre(ResourceLocation ruleId, ResourceLocation material,
			boolean materialDeclared, ResourceLocation policyDomain,
			IBlockState output, IBlockState deepOutput, int deepOutputMaxY,
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
		Map<IBlockState, Double> hostStates = new HashMap<>();
		addBlocks(hostBlocks, hostStates, json.get("host_blocks"));
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
		if (!hasHostTargets(hostBlocks, hostStates, familyMask)) {
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
		boolean backgroundController = json.has("background_generation_scale");
		double backgroundGenerationScale = backgroundController
				? Math.max(0.0D, Math.min(1.0D,
						decimal(json, "background_generation_scale", 1.0D))) : 1.0D;

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
		return new BakedOre(ruleId, material, materialDeclared, policyDomain,
				OreSourcePolicies.placementChannel(json),
				output, deepOutput, deepOutputMaxY, outputs,
				minY, maxY, Math.min(64.0D, frequency), minQuantity, maxQuantity,
				pattern, heightDistribution, discardChanceOnAirExposure,
				spread, verticalSpread, nodeSize,
				hostBlocks, hostStates, familyMask, geomeWeights, includedBiomeIds, excludedBiomeIds,
				includedDictionaryBiomes, excludedDictionaryBiomes, retrogen,
				backgroundController, backgroundGenerationScale);
	}

	static boolean hasHostTargets(Map<Block, Double> blocks,
			Map<IBlockState, Double> states, int familyMask) {
		return !blocks.isEmpty() || !states.isEmpty() || familyMask != 0;
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
				result.add(new BakedOutput(state(block, integer(value, "metadata", 0)), weight, minY, maxY));
			}
		}
		return result.isEmpty()
				? new BakedOutput[] { new BakedOutput(fallback, 1.0D, Integer.MIN_VALUE, Integer.MAX_VALUE) }
				: result.toArray(new BakedOutput[result.size()]);
	}

	private static void addBlocks(Map<Block, Double> target, Map<IBlockState, Double> exact,
			JsonElement element) {
		if (element == null || !element.isJsonArray()) {
			return;
		}
		for (JsonElement value : element.getAsJsonArray()) {
			JsonObject object = value.isJsonObject() ? value.getAsJsonObject() : null;
			ResourceLocation id = resource(object == null ? value.getAsString() : string(object, "block", ""));
			Block block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
			if (block != null && block != Blocks.AIR) {
				double weight = Math.max(0.0D, Math.min(1.0D,
						object == null ? 1.0D : decimal(object, "weight", 1.0D)));
				if (object != null && object.has("metadata")) {
					exact.put(state(block, integer(object, "metadata", 0)), weight);
				} else {
					target.put(block, weight);
				}
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
					for (Biome biome : net.minecraftforge.common.BiomeDictionary.getBiomesForType(
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
		String path = tag.getResourcePath();
		if ("stone".equals(path) || "base_stone_overworld".equals(path)) result.add(Blocks.STONE);
		if ("netherrack".equals(path) || "base_stone_nether".equals(path)) result.add(Blocks.NETHERRACK);
		for (ItemStack stack : OreDictionary.getOres(path, false)) {
			Block block = Block.getBlockFromItem(stack.getItem());
			if (block != null && block != Blocks.AIR) result.add(block);
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

	private static IBlockState blockState(JsonObject json, String key, String metadataKey,
			IBlockState fallback) {
		if (!json.has(key)) {
			return fallback;
		}
		ResourceLocation id = resource(string(json, key, ""));
		Block block = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
		return block == null || block == Blocks.AIR ? null : state(block, integer(json, metadataKey, 0));
	}

	private static IBlockState state(Block block, int metadata) {
		try { return block.getStateFromMeta(Math.max(0, Math.min(15, metadata))); }
		catch (RuntimeException ignored) { return block.getDefaultState(); }
	}

	private static final class BakedOre {
		final ResourceLocation ruleId;
		final ResourceLocation material;
		final boolean materialDeclared;
		final ResourceLocation policyDomain;
		final ResourceLocation channel;
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
		final Map<IBlockState, Double> hostStates;
		final int familyMask;
		final double[] geomeWeights;
		final Set<ResourceLocation> includedBiomeIds;
		final Set<ResourceLocation> excludedBiomeIds;
		final Set<Biome> includedDictionaryBiomes;
		final Set<Biome> excludedDictionaryBiomes;
		final boolean retrogen;
		final boolean backgroundController;
		final double backgroundGenerationScale;
		final BakedOutputBundle outputBundle;
		final BakedSource[] arbitrationSources;

		BakedOre(ResourceLocation ruleId, ResourceLocation material, boolean materialDeclared,
				ResourceLocation policyDomain, ResourceLocation channel,
				IBlockState output, IBlockState deepOutput, int deepOutputMaxY, BakedOutput[] outputs,
				int minY, int maxY, double frequency, int minQuantity, int maxQuantity,
				CompiledOrePattern pattern, OreHeightDistribution heightDistribution,
				double discardChanceOnAirExposure,
				int spread, int verticalSpread, int nodeSize,
				Map<Block, Double> hostBlocks, Map<IBlockState, Double> hostStates,
				int familyMask, double[] geomeWeights,
				Set<ResourceLocation> includedBiomeIds, Set<ResourceLocation> excludedBiomeIds,
				Set<Biome> includedDictionaryBiomes, Set<Biome> excludedDictionaryBiomes,
				boolean retrogen, boolean backgroundController,
				double backgroundGenerationScale) {
			this(ruleId, material, materialDeclared, policyDomain, channel,
					output, deepOutput, deepOutputMaxY, outputs,
					minY, maxY, frequency, minQuantity, maxQuantity, pattern, heightDistribution,
					discardChanceOnAirExposure, spread, verticalSpread, nodeSize, hostBlocks, hostStates,
					familyMask, geomeWeights, includedBiomeIds, excludedBiomeIds,
					includedDictionaryBiomes, excludedDictionaryBiomes, retrogen, backgroundController,
					backgroundGenerationScale, new BakedSource[0]);
		}

		private BakedOre(ResourceLocation ruleId, ResourceLocation material, boolean materialDeclared,
				ResourceLocation policyDomain, ResourceLocation channel,
				IBlockState output, IBlockState deepOutput, int deepOutputMaxY, BakedOutput[] outputs,
				int minY, int maxY, double frequency, int minQuantity, int maxQuantity,
				CompiledOrePattern pattern, OreHeightDistribution heightDistribution,
				double discardChanceOnAirExposure,
				int spread, int verticalSpread, int nodeSize,
				Map<Block, Double> hostBlocks, Map<IBlockState, Double> hostStates,
				int familyMask, double[] geomeWeights,
				Set<ResourceLocation> includedBiomeIds, Set<ResourceLocation> excludedBiomeIds,
				Set<Biome> includedDictionaryBiomes, Set<Biome> excludedDictionaryBiomes,
				boolean retrogen, boolean backgroundController,
				double backgroundGenerationScale, BakedSource[] arbitrationSources) {
			this.ruleId = ruleId;
			this.material = material;
			this.materialDeclared = materialDeclared;
			this.policyDomain = policyDomain;
			this.channel = channel;
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
			this.hostStates = hostStates;
			this.familyMask = familyMask;
			this.geomeWeights = geomeWeights;
			this.includedBiomeIds = includedBiomeIds;
			this.excludedBiomeIds = excludedBiomeIds;
			this.includedDictionaryBiomes = includedDictionaryBiomes;
			this.excludedDictionaryBiomes = excludedDictionaryBiomes;
			this.retrogen = retrogen;
			this.backgroundController = backgroundController;
			this.backgroundGenerationScale = backgroundGenerationScale;
			this.outputBundle = new BakedOutputBundle(output, deepOutput, deepOutputMaxY, outputs);
			this.arbitrationSources = arbitrationSources;
		}

		BakedOre withSources(List<BakedSource> sources) {
			return new BakedOre(ruleId, material, materialDeclared, policyDomain, channel,
					output, deepOutput, deepOutputMaxY, outputs,
					minY, maxY, frequency, minQuantity, maxQuantity, pattern, heightDistribution,
					discardChanceOnAirExposure, spread, verticalSpread, nodeSize, hostBlocks, hostStates,
					familyMask, geomeWeights, includedBiomeIds, excludedBiomeIds,
					includedDictionaryBiomes, excludedDictionaryBiomes, retrogen, backgroundController,
					backgroundGenerationScale, sources.toArray(new BakedSource[sources.size()]));
		}

		BakedOutputBundle outputSource(long worldSeed, ResourceLocation dimension, long identity) {
			if (arbitrationSources.length == 0) return outputBundle;
			double total = 0.0D;
			for (BakedSource source : arbitrationSources) total += source.weight;
			double choice = weightedChoice(outputSelectionSample(
					worldSeed, dimension, material, channel, identity), total);
			for (BakedSource source : arbitrationSources) {
				choice -= source.weight;
				if (choice < 0.0D) return source.bundle;
			}
			return arbitrationSources[arbitrationSources.length - 1].bundle;
		}

		boolean accepts(IBlockState state, Random random, BakedGeomeConfig config) {
			Double chance = hostStates.get(state);
			if (chance == null) chance = hostBlocks.get(state.getBlock());
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
		final ResourceLocation ruleId;
		final Block output;
		final ResourceLocation material;
		final boolean materialDeclared;
		final boolean suppressVanilla;
		final Map<ResourceLocation, BakedOre> explicit = new HashMap<>();
		final Set<ResourceLocation> explicitDimensions = new HashSet<>();
		BakedOre selector;

		BakedOreRule(ResourceLocation ruleId, Block output, ResourceLocation material,
				boolean materialDeclared,
				boolean suppressVanilla) {
			this.ruleId = ruleId;
			this.output = output;
			this.material = material;
			this.materialDeclared = materialDeclared;
			this.suppressVanilla = suppressVanilla;
		}
	}

	private static final class BakedSource {
		final String sourceId;
		final BakedOutputBundle bundle;
		final double weight;

		BakedSource(String sourceId, BakedOutputBundle bundle, double weight) {
			this.sourceId = sourceId;
			this.bundle = bundle;
			this.weight = weight;
		}
	}

	private static final class BakedOutputBundle {
		final IBlockState output;
		final IBlockState deepOutput;
		final int deepOutputMaxY;
		final BakedOutput[] outputs;

		BakedOutputBundle(IBlockState output, IBlockState deepOutput, int deepOutputMaxY,
				BakedOutput[] outputs) {
			this.output = output;
			this.deepOutput = deepOutput;
			this.deepOutputMaxY = deepOutputMaxY;
			this.outputs = outputs;
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
				Collections.emptyMap(), Collections.emptyMap(), NO_ORES, Collections.emptySet(),
				Collections.emptyMap(), Collections.emptyMap());

		final Map<ResourceLocation, BakedOre[]> byDimension;
		final Map<ResourceLocation, Set<Block>> vanillaOutputs;
		final Map<ResourceLocation, Map<Block, Double>> backgroundGenerationScales;
		final Map<ResourceLocation, Map<ResourceLocation, Double>> materialBackgroundGenerationScales;
		final BakedOre[] selectorOres;
		final Set<Block> selectorVanillaOutputs;
		final Map<Block, Double> selectorBackgroundGenerationScales;
		final Map<ResourceLocation, Double> selectorMaterialBackgroundGenerationScales;

		BakedOres(Map<ResourceLocation, BakedOre[]> byDimension,
				Map<ResourceLocation, Set<Block>> vanillaOutputs,
				Map<ResourceLocation, Map<Block, Double>> backgroundGenerationScales,
				Map<ResourceLocation, Map<ResourceLocation, Double>> materialBackgroundGenerationScales,
				BakedOre[] selectorOres, Set<Block> selectorVanillaOutputs,
				Map<Block, Double> selectorBackgroundGenerationScales,
				Map<ResourceLocation, Double> selectorMaterialBackgroundGenerationScales) {
			this.byDimension = byDimension;
			this.vanillaOutputs = vanillaOutputs;
			this.backgroundGenerationScales = backgroundGenerationScales;
			this.materialBackgroundGenerationScales = materialBackgroundGenerationScales;
			this.selectorOres = selectorOres;
			this.selectorVanillaOutputs = selectorVanillaOutputs;
			this.selectorBackgroundGenerationScales = selectorBackgroundGenerationScales;
			this.selectorMaterialBackgroundGenerationScales = selectorMaterialBackgroundGenerationScales;
		}
	}

	private static final class GenerationScratch {
		final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		final PatternContext patternContext = new PatternContext(cursor);
		private double[] geomeValues = new double[0];
		private WorldServer samplerLevel;
		private ResourceLocation samplerDimension;
		private BakedGeomeConfig samplerConfig;
		private Optional<GeologySampler> geologySampler = Optional.empty();

		double[] geomeValues(int count) {
			if (geomeValues.length != count) {
				geomeValues = new double[count];
			}
			return geomeValues;
		}

		Optional<GeologySampler> geologySampler(WorldServer level, ResourceLocation dimension) {
			BakedGeomeConfig activeConfig = WorldIds.OVERWORLD.equals(dimension)
					? GeomeConfig.baked() : GeomeConfig.baked(dimension);
			if (level != samplerLevel || !dimension.equals(samplerDimension)
					|| activeConfig != samplerConfig) {
				samplerLevel = level;
				samplerDimension = dimension;
				samplerConfig = activeConfig;
				geologySampler = level == null ? Optional.empty() : OreSpawnApi.createSampler(level);
			}
			return geologySampler;
		}
	}

	private static final class PatternContext implements OreGenerationContext {
		private final BlockPos.MutableBlockPos cursor;
		private final BlockPos.MutableBlockPos airCursor = new BlockPos.MutableBlockPos();
		private World world;
		private Chunk chunk;
		private Random random;
		private BakedOre ore;
		private int geome;
		private ResourceLocation dimension;
		private long worldSeed;
		private int chunkX;
		private int chunkZ;
		private Optional<GeologySampler> geologySampler = Optional.empty();
		private int originX;
		private int originY;
		private int originZ;
		private int minY;
		private int maxY;
		private int quantity;
		private BakedOutputBundle ordinaryOutput;
		private BakedOutputBundle identifiedOutput;
		private long identifiedOutputIdentity;
		private boolean hasIdentifiedOutput;

		PatternContext(BlockPos.MutableBlockPos cursor) {
			this.cursor = cursor;
		}

		void initialize(World world, Chunk chunk, Random random, BakedOre ore, int geome,
				ResourceLocation dimension, long worldSeed,
				int attemptIndex,
				Optional<GeologySampler> geologySampler,
				int originX, int originY, int originZ, int minY, int maxY, int quantity) {
			this.world = world;
			this.chunk = chunk;
			this.random = random;
			this.ore = ore;
			this.geome = geome;
			this.dimension = dimension;
			this.worldSeed = worldSeed;
			ChunkPos chunkPos = ChunkAccessCompat.position(chunk);
			this.chunkX = chunkPos.chunkXPos;
			this.chunkZ = chunkPos.chunkZPos;
			this.geologySampler = geologySampler;
			this.originX = originX;
			this.originY = originY;
			this.originZ = originZ;
			this.minY = minY;
			this.maxY = maxY;
			this.quantity = quantity;
			long attemptIdentity = ordinaryOutputIdentity(chunkPos, attemptIndex, ore.ruleId, ore.channel);
			this.ordinaryOutput = ore.outputSource(worldSeed, dimension, attemptIdentity);
			this.identifiedOutput = null;
			this.hasIdentifiedOutput = false;
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
		@Override public long worldSeed() { return worldSeed; }
		@Override public ResourceLocation dimension() { return dimension; }
		@Override public int chunkX() { return chunkX; }
		@Override public int chunkZ() { return chunkZ; }
		@Override public Optional<GeologySampler> geologySampler() { return geologySampler; }

		@Override
		public boolean inside(int x, int y, int z) {
			if (y < minY || y > maxY || y < 0
					|| y >= 256) return false;
			if (world == null) return insideChunk(chunk, x, y, z);
			cursor.setPos(x, y, z);
			return insideChunk(chunk, x, y, z)
					&& world.isBlockLoaded(cursor.setPos(x, y, z), false);
		}

		@Override
		public boolean isFluid(int x, int y, int z, Fluid fluid) {
			if (!inside(x, y, z)) return false;
			cursor.setPos(x, y, z);
			IBlockState state = world == null ? chunk.getBlockState(cursor) : world.getBlockState(cursor);
			if (state.getBlock() instanceof IFluidBlock) {
				return ((IFluidBlock) state.getBlock()).getFluid() == fluid;
			}
			return FluidRegistry.lookupFluidForBlock(state.getBlock()) == fluid;
		}

		@Override
		public boolean tryPlace(int x, int y, int z) {
			return tryPlaceWithOutput(x, y, z, ordinaryOutput);
		}

		@Override
		public boolean tryPlace(int x, int y, int z, long outputIdentity) {
			if (!hasIdentifiedOutput || identifiedOutputIdentity != outputIdentity) {
				identifiedOutputIdentity = outputIdentity;
				identifiedOutput = ore.outputSource(worldSeed, dimension, outputIdentity);
				hasIdentifiedOutput = true;
			}
			return tryPlaceWithOutput(x, y, z, identifiedOutput);
		}

		private boolean tryPlaceWithOutput(int x, int y, int z, BakedOutputBundle outputSource) {
			if (!inside(x, y, z)) return false;
			cursor.setPos(x, y, z);
			IBlockState existing = world == null ? chunk.getBlockState(cursor) : world.getBlockState(cursor);
			if (!ore.accepts(existing, random, geomeConfig)) return false;
			if (ore.discardChanceOnAirExposure > 0.0D
					&& random.nextDouble() < ore.discardChanceOnAirExposure
					&& isAdjacentToAir(x, y, z)) {
				return false;
			}
			IBlockState output = outputSource.outputAt(y, random);
			if (world == null) chunk.setBlockState(cursor, output);
			else world.setBlockState(cursor.toImmutable(), output, GENERATION_WRITE_FLAGS);
			return true;
		}

		private boolean isAdjacentToAir(int x, int y, int z) {
			return isAir(x + 1, y, z) || isAir(x - 1, y, z)
					|| isAir(x, y + 1, z) || isAir(x, y - 1, z)
					|| isAir(x, y, z + 1) || isAir(x, y, z - 1);
		}

		private boolean isAir(int x, int y, int z) {
			if (!insideChunk(chunk, x, y, z)) {
				return false;
			}
			airCursor.setPos(x, y, z);
			IBlockState state = world == null ? chunk.getBlockState(airCursor) : world.getBlockState(airCursor);
			return state.getBlock().isAir(state, world, airCursor);
		}
	}

	private static long chunkKey(ChunkPos pos) {
		return ((long) pos.chunkXPos & 0xFFFFFFFFL) | (((long) pos.chunkZPos & 0xFFFFFFFFL) << 32);
	}

}
