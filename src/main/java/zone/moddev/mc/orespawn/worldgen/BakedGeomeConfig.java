package zone.moddev.mc.orespawn.worldgen;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.resources.Identifier;

public final class BakedGeomeConfig {
	static final int MIN_Y = -64;
	static final int MAX_Y = 319;
	private static final int HEIGHT = MAX_Y - MIN_Y + 1;
	private static final int LEGACY_MAX_Y = 255;
	private static final int FORMATION_BUCKETS = 256;
	private static final int PICKER_SCALE = 1000;
	private static final BlockState FALLBACK = Blocks.STONE.defaultBlockState();

	final GeomeDefinition[] geomes;
	final double geomeScale;
	final double biomeInfluence;
	final double regionalNoiseInfluence;
	final double boundaryNoiseInfluence;
	final int[] noiseOffsetX;
	final int[] noiseOffsetZ;
	final FormationSettings formations;

	private final Map<Biome, double[]> biomeWeights;
	private final Map<Identifier, double[]> biomeWeightsById;
	private final double[] fallbackWeights;
	private final RockEntry[] rocks;
	private final BlockState[] rockStates;
	private final Set<Block> sedimentaryBlocks;
	private final Set<Block> oreReplaceableBlocks;
	private final Map<Block, RockFamily> rockFamilies;
	private final int familyDiversitySlots;

	private int[][][] legacyFamilyThresholds;
	private WeightedBlockPicker[][][] legacyRockPickers;
	private byte[] stableFamilyChoices;
	private int[] stableRockChoices;
	private int[][] familyRockIndexes;
	private double[][][] stableRockLogWeights;
	private double[][][] stableRockPriorities;

	BakedGeomeConfig(GeomeDefinition[] geomes, double geomeScale, double biomeInfluence,
			double regionalNoiseInfluence, double boundaryNoiseInfluence, Map<Biome, double[]> biomeWeights,
			Map<Identifier, double[]> biomeWeightsById, RockEntry[] rocks, FormationSettings formations) {
		this.geomes = geomes;
		this.geomeScale = geomeScale;
		this.biomeInfluence = biomeInfluence;
		this.regionalNoiseInfluence = regionalNoiseInfluence;
		this.boundaryNoiseInfluence = boundaryNoiseInfluence;
		this.formations = formations;
		this.familyDiversitySlots = formations.familyDiversitySlots();
		this.biomeWeights = new IdentityHashMap<>(biomeWeights);
		this.biomeWeightsById = new HashMap<>(biomeWeightsById);
		for (Map.Entry<Biome, double[]> entry : biomeWeights.entrySet()) {
			Identifier biomeId = BiomeRegistryAccess.id(entry.getKey());
			if (biomeId != null) {
				this.biomeWeightsById.putIfAbsent(biomeId, entry.getValue());
			}
		}
		this.fallbackWeights = defaultWeights(geomes.length);
		this.noiseOffsetX = new int[geomes.length];
		this.noiseOffsetZ = new int[geomes.length];
		for (int i = 0; i < geomes.length; i++) {
			noiseOffsetX[i] = (i + 1) * 9973;
			noiseOffsetZ[i] = -((i + 1) * 6151);
		}

		this.rocks = rocks.clone();
		rockStates = new BlockState[rocks.length];
		for (int i = 0; i < rocks.length; i++) {
			rockStates[i] = rocks[i].state;
		}
		sedimentaryBlocks = buildBlockSet(rocks, true, false);
		oreReplaceableBlocks = buildBlockSet(rocks, false, true);
		rockFamilies = buildFamilyMap(rocks);

		if (formations.usesStableLayers()) {
			buildStablePickers(rocks);
		} else {
			buildLegacyPickers(rocks);
		}
	}

	public int pickGeome(Biome biome, double[] regionalNoise, double boundaryNoise) {
		return pickGeome(biome, null, regionalNoise, boundaryNoise);
	}

	int pickGeome(Biome biome, Identifier biomeId, double[] regionalNoise, double boundaryNoise) {
		double[] weights = biomeWeightsFor(biome, biomeId);

		double bestScore = Double.NEGATIVE_INFINITY;
		int bestIndex = 0;
		for (int i = 0; i < geomes.length; i++) {
			double boundary = ((i & 1) == 0 ? boundaryNoise : -boundaryNoise) * boundaryNoiseInfluence;
			double score = geomes[i].baseWeight + (weights[i] * biomeInfluence)
					+ (regionalNoise[i] * regionalNoiseInfluence) + boundary;
			if (score > bestScore) {
				bestScore = score;
				bestIndex = i;
			}
		}

		return bestIndex;
	}

	int scoreGeomes(Biome biome, Identifier biomeId, double[] regionalNoiseAndScores, double boundaryNoise) {
		double[] weights = biomeWeightsFor(biome, biomeId);
		double bestScore = Double.NEGATIVE_INFINITY;
		int bestIndex = 0;
		for (int i = 0; i < geomes.length; i++) {
			double boundary = ((i & 1) == 0 ? boundaryNoise : -boundaryNoise) * boundaryNoiseInfluence;
			double score = geomes[i].baseWeight + (weights[i] * biomeInfluence)
					+ (regionalNoiseAndScores[i] * regionalNoiseInfluence) + boundary;
			regionalNoiseAndScores[i] = score;
			if (score > bestScore) {
				bestScore = score;
				bestIndex = i;
			}
		}
		return bestIndex;
	}

	public RockFamily pickFamily(int geomeIndex, int y, int formationValue) {
		return pickFamily(geomeIndex, y, formationValue, 0);
	}

	RockFamily pickFamily(int geomeIndex, int y, int formationValue, int diversitySlot) {
		if (formations.usesStableLayers()) {
			int index = stableFamilyIndex(geomeIndex, clampStableY(y), formationValue & 0xFF,
					diversitySlot & (familyDiversitySlots - 1));
			return RockFamily.values()[stableFamilyChoices[index] & 0xFF];
		}

		int[] thresholds = legacyFamilyThresholds[geomeIndex][clampLegacyY(y)];
		int total = thresholds[thresholds.length - 1];
		if (total <= 0) {
			return RockFamily.SEDIMENTARY;
		}

		int value = positive(formationValue) % total;
		for (int i = 0; i < thresholds.length; i++) {
			if (value < thresholds[i]) {
				return RockFamily.values()[i];
			}
		}
		return RockFamily.SEDIMENTARY;
	}

	RockFamily pickStableFamilyAtWorldY(int geomeIndex, int worldY, int formationY,
			int formationValue, int diversitySlot) {
		RockFamily preferred = pickFamily(geomeIndex, formationY, formationValue, diversitySlot);
		if (hasEligibleStableRock(geomeIndex, preferred, worldY, formationY)) {
			return preferred;
		}

		int bucket = formationValue & 0xFF;
		int boundedFormationY = clampStableValue(formationY);
		double bestScore = Double.NEGATIVE_INFINITY;
		RockFamily bestFamily = preferred;
		for (RockFamily family : RockFamily.values()) {
			if (!hasEligibleStableRock(geomeIndex, family, worldY, formationY)) {
				continue;
			}
			double weight = Math.pow(geomes[geomeIndex].familyWeights[family.ordinal()], 2.5D)
					* familyDepthWeight(family, boundedFormationY);
			if (weight <= 0.0D) {
				continue;
			}
			double score = Math.log(weight) + gumbelPriority(bucket, geomeIndex, family.ordinal(),
					isStableBucket(bucket, -1), 0x6A09E667F3BCC909L);
			if (score > bestScore) {
				bestScore = score;
				bestFamily = family;
			}
		}
		return bestFamily;
	}

	public BlockState pickRock(int geomeIndex, RockFamily family, int y, int formationValue) {
		if (formations.usesStableLayers()) {
			return pickStableRockAtWorldY(geomeIndex, family, y, y, formationValue);
		}
		return legacyRockPickers[geomeIndex][family.ordinal()][clampLegacyY(y)].pick(formationValue);
	}

	BlockState pickStableRockAtWorldY(int geomeIndex, RockFamily family, int worldY,
			int formationY, int formationValue) {
		int yIndex = clampStableY(formationY);
		int bucket = formationValue & 0xFF;
		int choiceIndex = stableRockIndex(geomeIndex, family.ordinal(), yIndex, bucket);
		int selectedRock = stableRockChoices[choiceIndex];
		if (isEligibleStableRock(geomeIndex, selectedRock, worldY, yIndex)) {
			return rockStates[selectedRock];
		}

		double bestScore = Double.NEGATIVE_INFINITY;
		int bestRock = -1;
		for (int rockIndex : familyRockIndexes[family.ordinal()]) {
			if (!isEligibleStableRock(geomeIndex, rockIndex, worldY, yIndex)) {
				continue;
			}
			double score = stableRockLogWeights[geomeIndex][rockIndex][yIndex]
					+ stableRockPriorities[geomeIndex][rockIndex][bucket];
			if (score > bestScore) {
				bestScore = score;
				bestRock = rockIndex;
			}
		}
		return bestRock < 0 ? FALLBACK : rockStates[bestRock];
	}

	public String geomeName(int geomeIndex) {
		return geomes[geomeIndex].name;
	}

	public boolean isSedimentaryRock(BlockState state) {
		return sedimentaryBlocks.contains(state.getBlock());
	}

	public boolean isOreReplaceable(BlockState state) {
		return oreReplaceableBlocks.contains(state.getBlock());
	}

	RockFamily familyOf(BlockState state) {
		return rockFamilies.get(state.getBlock());
	}

	public BlockState[] statesForFamily(RockFamily... families) {
		int mask = 0;
		for (RockFamily family : families) {
			mask |= 1 << family.ordinal();
		}
		int count = 0;
		for (BlockState state : rockStates) {
			RockFamily family = rockFamilies.get(state.getBlock());
			if (family != null && (mask & (1 << family.ordinal())) != 0) {
				count++;
			}
		}
		BlockState[] result = new BlockState[count];
		int index = 0;
		for (BlockState state : rockStates) {
			RockFamily family = rockFamilies.get(state.getBlock());
			if (family != null && (mask & (1 << family.ordinal())) != 0) {
				result[index++] = state;
			}
		}
		return result;
	}

	void addRockBlocks(Set<Block> target) {
		for (BlockState state : rockStates) {
			target.add(state.getBlock());
		}
	}

	int geomeCount() {
		return geomes.length;
	}

	int geomeIndex(String name) {
		for (int i = 0; i < geomes.length; i++) {
			if (geomes[i].name.equals(name)
					|| (name.startsWith("orespawn:")
							&& geomes[i].name.equals(name.substring("orespawn:".length())))
					|| (name.indexOf(':') < 0 && geomes[i].name.equals("orespawn:" + name))) {
				return i;
			}
		}
		return -1;
	}

	int familyDiversitySlots() {
		return familyDiversitySlots;
	}

	String describeBiomeWeights(Biome biome) {
		Identifier biomeId = BiomeRegistryAccess.id(biome);
		double[] weights = biomeId == null ? null : biomeWeightsById.get(biomeId);
		String source = "registry-id";
		if (weights == null) {
			weights = biomeWeights.get(biome);
			source = "identity";
		}
		if (weights == null) {
			weights = fallbackWeights;
			source = "fallback";
		}
		StringBuilder description = new StringBuilder(source).append('{');
		for (int i = 0; i < geomes.length; i++) {
			if (i > 0) {
				description.append(',');
			}
			description.append(geomes[i].name).append('=').append(weights[i]);
		}
		return description.append('}').toString();
	}

	String dominantBiomeWeight(Biome biome) {
		double[] weights = biomeWeightsFor(biome, BiomeRegistryAccess.id(biome));
		int best = 0;
		for (int i = 1; i < weights.length; i++) {
			if (weights[i] > weights[best]) {
				best = i;
			}
		}
		return geomes[best].name;
	}

	boolean hasDistinctBiomeWeights(Biome biome) {
		double[] weights = biomeWeightsFor(biome, BiomeRegistryAccess.id(biome));
		double first = weights[0];
		for (int i = 1; i < weights.length; i++) {
			if (Math.abs(weights[i] - first) > 0.000001D) {
				return true;
			}
		}
		return false;
	}

	private double[] biomeWeightsFor(Biome biome, Identifier biomeId) {
		double[] weights = biomeId == null ? null : biomeWeightsById.get(biomeId);
		if (weights == null) weights = biomeWeights.get(biome);
		return weights == null ? fallbackWeights : weights;
	}

	private void buildStablePickers(RockEntry[] rocks) {
		stableFamilyChoices = new byte[geomes.length * HEIGHT * FORMATION_BUCKETS * familyDiversitySlots];
		stableRockChoices = new int[geomes.length * RockFamily.values().length * HEIGHT * FORMATION_BUCKETS];
		Arrays.fill(stableRockChoices, -1);
		int familyCount = RockFamily.values().length;
		familyRockIndexes = groupRockIndexes(rocks);
		stableRockLogWeights = new double[geomes.length][rocks.length][HEIGHT];
		stableRockPriorities = new double[geomes.length][rocks.length][FORMATION_BUCKETS];
		double[][][] familyLogWeights = new double[geomes.length][familyCount][HEIGHT];
		double[][][] familyWeights = new double[geomes.length][familyCount][HEIGHT];
		double[][][] familyPriorities = new double[geomes.length][familyCount][FORMATION_BUCKETS];

		for (int geome = 0; geome < geomes.length; geome++) {
			for (int rockIndex = 0; rockIndex < rocks.length; rockIndex++) {
				RockEntry rock = rocks[rockIndex];
				for (int y = MIN_Y; y <= MAX_Y; y++) {
					double rawWeight = rock.weight * rock.geomeWeights[geome]
							* depthWeight(y, rock.depthPeak, rock.depthSpread);
					stableRockLogWeights[geome][rockIndex][y - MIN_Y] = rawWeight > 0.0D
							? Math.log(rawWeight) : Double.NEGATIVE_INFINITY;
				}
				for (int bucket = 0; bucket < FORMATION_BUCKETS; bucket++) {
					stableRockPriorities[geome][rockIndex][bucket] = gumbelPriority(bucket, geome, rockIndex,
							isStableBucket(bucket, rock.family.ordinal()),
							0xBB67AE8584CAA73BL ^ ((long) rock.family.ordinal() << 32));
				}
			}

			for (RockFamily family : RockFamily.values()) {
				int familyIndex = family.ordinal();
				for (int y = MIN_Y; y <= MAX_Y; y++) {
					int yIndex = y - MIN_Y;
					boolean available = false;
					for (int rockIndex : familyRockIndexes[familyIndex]) {
						RockEntry rock = rocks[rockIndex];
						if (y >= rock.minY && y <= rock.maxY
								&& stableRockLogWeights[geome][rockIndex][yIndex] != Double.NEGATIVE_INFINITY) {
							available = true;
							break;
						}
					}
					double geomeWeight = geomes[geome].familyWeights[familyIndex];
					double weight = available
							? Math.pow(geomeWeight, 2.5D) * familyDepthWeight(family, y) : 0.0D;
					familyWeights[geome][familyIndex][yIndex] = weight;
					familyLogWeights[geome][familyIndex][yIndex] = weight > 0.0D
							? Math.log(weight) : Double.NEGATIVE_INFINITY;
				}
				for (int bucket = 0; bucket < FORMATION_BUCKETS; bucket++) {
					familyPriorities[geome][familyIndex][bucket] = gumbelPriority(bucket, geome, familyIndex,
							isStableBucket(bucket, -1), 0x6A09E667F3BCC909L);
				}
			}
		}

		int[] quotas = new int[familyCount];
		int[] remaining = new int[familyCount];
		double[] remainders = new double[familyCount];
		boolean[] bonusAwarded = new boolean[familyCount];
		for (int geome = 0; geome < geomes.length; geome++) {
			for (int y = MIN_Y; y <= MAX_Y; y++) {
				int yIndex = y - MIN_Y;
				for (int bucket = 0; bucket < FORMATION_BUCKETS; bucket++) {
					if (familyDiversitySlots == 1) {
						double bestFamilyScore = Double.NEGATIVE_INFINITY;
						int bestFamily = RockFamily.SEDIMENTARY.ordinal();
						for (RockFamily family : RockFamily.values()) {
							int familyIndex = family.ordinal();
							double familyScore = familyLogWeights[geome][familyIndex][yIndex]
									+ familyPriorities[geome][familyIndex][bucket];
							if (familyScore > bestFamilyScore) {
								bestFamilyScore = familyScore;
								bestFamily = familyIndex;
							}
						}
						stableFamilyChoices[stableFamilyIndex(geome, yIndex, bucket, 0)] = (byte) bestFamily;
					} else {
						fillBalancedFamilyCycle(geome, yIndex, bucket, familyWeights, familyPriorities,
								quotas, remaining, remainders, bonusAwarded);
					}

					for (RockFamily family : RockFamily.values()) {
						int familyIndex = family.ordinal();
						double bestRockScore = Double.NEGATIVE_INFINITY;
						int bestRock = -1;
						for (int rockIndex : familyRockIndexes[familyIndex]) {
							RockEntry rock = rocks[rockIndex];
							if (y < rock.minY || y > rock.maxY) {
								continue;
							}
							double rockScore = stableRockLogWeights[geome][rockIndex][yIndex]
									+ stableRockPriorities[geome][rockIndex][bucket];
							if (rockScore > bestRockScore) {
								bestRockScore = rockScore;
								bestRock = rockIndex;
							}
						}
						stableRockChoices[stableRockIndex(geome, familyIndex, yIndex, bucket)] = bestRock;
					}
				}
			}
		}
	}

	private boolean hasEligibleStableRock(int geomeIndex, RockFamily family, int worldY, int formationY) {
		int yIndex = clampStableY(formationY);
		for (int rockIndex : familyRockIndexes[family.ordinal()]) {
			if (isEligibleStableRock(geomeIndex, rockIndex, worldY, yIndex)) {
				return true;
			}
		}
		return false;
	}

	private boolean isEligibleStableRock(int geomeIndex, int rockIndex, int worldY, int formationYIndex) {
		if (rockIndex < 0) {
			return false;
		}
		RockEntry rock = rocks[rockIndex];
		return worldY >= rock.minY && worldY <= rock.maxY
				&& stableRockLogWeights[geomeIndex][rockIndex][formationYIndex] != Double.NEGATIVE_INFINITY;
	}

	private void fillBalancedFamilyCycle(int geome, int yIndex, int bucket,
			double[][][] familyWeights, double[][][] familyPriorities,
			int[] quotas, int[] remaining, double[] remainders, boolean[] bonusAwarded) {
		Arrays.fill(quotas, 0);
		Arrays.fill(remainders, 0.0D);
		Arrays.fill(bonusAwarded, false);

		double totalWeight = 0.0D;
		for (RockFamily family : RockFamily.values()) {
			totalWeight += familyWeights[geome][family.ordinal()][yIndex];
		}
		if (totalWeight <= 0.0D) {
			for (int slot = 0; slot < familyDiversitySlots; slot++) {
				stableFamilyChoices[stableFamilyIndex(geome, yIndex, bucket, slot)] =
						(byte) RockFamily.SEDIMENTARY.ordinal();
			}
			return;
		}

		int allocated = 0;
		// Largest-remainder apportionment turns the continuous weights into an exact cycle quota.
		for (RockFamily family : RockFamily.values()) {
			int familyIndex = family.ordinal();
			double exactSlots = familyWeights[geome][familyIndex][yIndex]
					* familyDiversitySlots / totalWeight;
			int wholeSlots = (int) Math.floor(exactSlots);
			quotas[familyIndex] = wholeSlots;
			remainders[familyIndex] = exactSlots - wholeSlots;
			allocated += wholeSlots;
		}

		while (allocated < familyDiversitySlots) {
			int bestFamily = -1;
			double bestRemainder = Double.NEGATIVE_INFINITY;
			double bestPriority = Double.NEGATIVE_INFINITY;
			for (RockFamily family : RockFamily.values()) {
				int familyIndex = family.ordinal();
				if (familyWeights[geome][familyIndex][yIndex] <= 0.0D || bonusAwarded[familyIndex]) {
					continue;
				}
				double remainder = remainders[familyIndex];
				double priority = familyPriorities[geome][familyIndex][bucket];
				if (remainder > bestRemainder
						|| (remainder == bestRemainder && priority > bestPriority)) {
					bestFamily = familyIndex;
					bestRemainder = remainder;
					bestPriority = priority;
				}
			}
			if (bestFamily < 0) {
				break;
			}
			quotas[bestFamily]++;
			bonusAwarded[bestFamily] = true;
			allocated++;
		}

		System.arraycopy(quotas, 0, remaining, 0, quotas.length);
		int previousFamily = -1;
		// Interleave quota copies so a favored family is spread through the cycle.
		for (int slot = 0; slot < familyDiversitySlots; slot++) {
			boolean hasAlternative = false;
			for (RockFamily family : RockFamily.values()) {
				int familyIndex = family.ordinal();
				if (familyIndex != previousFamily && remaining[familyIndex] > 0) {
					hasAlternative = true;
					break;
				}
			}

			int bestFamily = -1;
			int bestScore = Integer.MIN_VALUE;
			double bestPriority = Double.NEGATIVE_INFINITY;
			for (RockFamily family : RockFamily.values()) {
				int familyIndex = family.ordinal();
				if (remaining[familyIndex] <= 0
						|| (hasAlternative && familyIndex == previousFamily)) {
					continue;
				}
				int score = (remaining[familyIndex] * 16) + quotas[familyIndex];
				double priority = familyPriorities[geome][familyIndex][bucket];
				if (score > bestScore || (score == bestScore && priority > bestPriority)) {
					bestFamily = familyIndex;
					bestScore = score;
					bestPriority = priority;
				}
			}
			if (bestFamily < 0) {
				bestFamily = RockFamily.SEDIMENTARY.ordinal();
			}
			stableFamilyChoices[stableFamilyIndex(geome, yIndex, bucket, slot)] = (byte) bestFamily;
			remaining[bestFamily]--;
			previousFamily = bestFamily;
		}
	}

	private static int[][] groupRockIndexes(RockEntry[] rocks) {
		int[][] indexes = new int[RockFamily.values().length][];
		for (RockFamily family : RockFamily.values()) {
			int count = 0;
			for (RockEntry rock : rocks) {
				if (rock.family == family) {
					count++;
				}
			}
			int[] familyIndexes = new int[count];
			int cursor = 0;
			for (int i = 0; i < rocks.length; i++) {
				if (rocks[i].family == family) {
					familyIndexes[cursor++] = i;
				}
			}
			indexes[family.ordinal()] = familyIndexes;
		}
		return indexes;
	}

	private boolean isStableBucket(int bucket, int scope) {
		if (formations.continuity <= 0.0D) {
			return false;
		}
		if (formations.continuity >= 1.0D) {
			return true;
		}
		long hash = mix64(0x3C6EF372FE94F82BL ^ ((long) bucket << 32) ^ (scope * 0x9E3779B9L));
		return unitDouble(hash) < formations.continuity;
	}

	private static double gumbelPriority(int bucket, int geome, int candidate, boolean stable, long salt) {
		long key = salt ^ ((long) bucket * 0x9E3779B97F4A7C15L)
				^ ((long) (candidate + 1) * 0xD1B54A32D192ED03L);
		if (!stable) {
			key ^= (long) (geome + 1) * 0x94D049BB133111EBL;
		}
		double uniform = unitDouble(mix64(key));
		return -Math.log(-Math.log(uniform));
	}

	private static long mix64(long value) {
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	private static double unitDouble(long value) {
		return ((value >>> 11) + 0.5D) * 0x1.0p-53;
	}

	private void buildLegacyPickers(RockEntry[] rocks) {
		legacyFamilyThresholds = new int[geomes.length][LEGACY_MAX_Y + 1][RockFamily.values().length];
		legacyRockPickers = new WeightedBlockPicker[geomes.length][RockFamily.values().length][LEGACY_MAX_Y + 1];
		for (int geome = 0; geome < geomes.length; geome++) {
			for (int y = 0; y <= LEGACY_MAX_Y; y++) {
				int familyTotal = 0;
				for (RockFamily family : RockFamily.values()) {
					WeightedBlockPicker picker = buildLegacyRockPicker(rocks, geome, family, y);
					legacyRockPickers[geome][family.ordinal()][y] = picker;

					double familyWeight = geomes[geome].familyWeights[family.ordinal()]
							* familyDepthWeight(family, y);
					if (picker.isEmpty()) {
						familyWeight = 0.0D;
					}
					if (familyWeight > 0.0D) {
						familyTotal += Math.max(1, (int) Math.round(familyWeight * PICKER_SCALE));
					}
					legacyFamilyThresholds[geome][y][family.ordinal()] = familyTotal;
				}
			}
		}
	}

	private WeightedBlockPicker buildLegacyRockPicker(RockEntry[] rocks, int geome, RockFamily family, int y) {
		BlockState[] states = new BlockState[rocks.length];
		int[] thresholds = new int[rocks.length];
		int total = 0;
		int index = 0;
		for (RockEntry rock : rocks) {
			if (rock.family != family || y < rock.minY || y > rock.maxY) {
				continue;
			}
			double rawWeight = rock.weight * rock.geomeWeights[geome]
					* depthWeight(y, rock.depthPeak, rock.depthSpread);
			if (rawWeight <= 0.0D) {
				continue;
			}
			total += Math.max(1, (int) Math.round(rawWeight * PICKER_SCALE));
			states[index] = rock.state;
			thresholds[index] = total;
			index++;
		}
		return new WeightedBlockPicker(Arrays.copyOf(states, index), Arrays.copyOf(thresholds, index), total);
	}

	private static Set<Block> buildBlockSet(RockEntry[] rocks, boolean sedimentaryOnly,
			boolean oreReplaceableOnly) {
		Set<Block> blocks = new HashSet<Block>();
		for (RockEntry rock : rocks) {
			if ((!sedimentaryOnly || rock.family == RockFamily.SEDIMENTARY)
					&& (!oreReplaceableOnly || rock.oreReplaceable)) {
				blocks.add(rock.state.getBlock());
			}
		}
		return blocks;
	}

	private static Map<Block, RockFamily> buildFamilyMap(RockEntry[] rocks) {
		Map<Block, RockFamily> families = new IdentityHashMap<Block, RockFamily>();
		for (RockEntry rock : rocks) {
			families.put(rock.state.getBlock(), rock.family);
		}
		return families;
	}

	private int stableFamilyIndex(int geome, int yIndex, int bucket, int diversitySlot) {
		return ((((geome * HEIGHT + yIndex) * FORMATION_BUCKETS) + bucket) * familyDiversitySlots)
				+ diversitySlot;
	}

	private int stableRockIndex(int geome, int family, int yIndex, int bucket) {
		return ((((geome * RockFamily.values().length) + family) * HEIGHT + yIndex) * FORMATION_BUCKETS)
				+ bucket;
	}

	private static double[] defaultWeights(int count) {
		double[] weights = new double[count];
		Arrays.fill(weights, 1.0D);
		return weights;
	}

	private static double depthWeight(int y, int peak, int spread) {
		double distance = (y - peak) / (double) Math.max(1, spread);
		return 0.08D + (0.92D / (1.0D + distance * distance));
	}

	private static double familyDepthWeight(RockFamily family, int y) {
		switch (family) {
			case SEDIMENTARY:
				return 0.35D + depthWeight(y, 68, 54);
			case METAMORPHIC:
				return 0.25D + (1.35D * depthWeight(y, 18, 36));
			case IGNEOUS_INTRUSIVE:
				return 0.25D + (1.20D * depthWeight(y, 28, 44));
			case IGNEOUS_VOLCANIC:
				return 0.25D + (1.20D * depthWeight(y, 76, 30));
			default:
				return 1.0D;
		}
	}

	private static int clampStableY(int y) {
		return Math.max(MIN_Y, Math.min(MAX_Y, y)) - MIN_Y;
	}

	private static int clampStableValue(int y) {
		return Math.max(MIN_Y, Math.min(MAX_Y, y));
	}

	private static int clampLegacyY(int y) {
		return Math.max(0, Math.min(LEGACY_MAX_Y, y));
	}

	private static int positive(int value) {
		return value & 0x7FFFFFFF;
	}

	static final class GeomeDefinition {
		final String name;
		final double baseWeight;
		final double[] familyWeights;

		GeomeDefinition(String name, double baseWeight, double[] familyWeights) {
			this.name = name;
			this.baseWeight = baseWeight;
			this.familyWeights = familyWeights;
		}
	}

	static final class RockEntry {
		final BlockState state;
		final RockFamily family;
		final int depthPeak;
		final int depthSpread;
		final int minY;
		final int maxY;
		final double weight;
		final boolean oreReplaceable;
		final double[] geomeWeights;

		RockEntry(BlockState state, RockFamily family, int depthPeak, int depthSpread, int minY, int maxY,
				double weight, boolean oreReplaceable, double[] geomeWeights) {
			this.state = state;
			this.family = family;
			this.depthPeak = depthPeak;
			this.depthSpread = depthSpread;
			this.minY = minY;
			this.maxY = maxY;
			this.weight = weight;
			this.oreReplaceable = oreReplaceable;
			this.geomeWeights = geomeWeights;
		}
	}

	private static final class WeightedBlockPicker {
		private final BlockState[] states;
		private final int[] thresholds;
		private final int total;

		WeightedBlockPicker(BlockState[] states, int[] thresholds, int total) {
			this.states = states;
			this.thresholds = thresholds;
			this.total = total;
		}

		boolean isEmpty() {
			return total <= 0 || states.length == 0;
		}

		BlockState pick(int hash) {
			if (isEmpty()) {
				return FALLBACK;
			}
			int value = positive(hash) % total;
			for (int i = 0; i < thresholds.length; i++) {
				if (value < thresholds[i]) {
					return states[i];
				}
			}
			return states[states.length - 1];
		}
	}
}
