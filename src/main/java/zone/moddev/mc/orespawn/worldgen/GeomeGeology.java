package zone.moddev.mc.orespawn.worldgen;

import java.util.Random;
import java.util.Optional;

import zone.moddev.mc.orespawn.worldgen.math.PerlinNoise2D;

import net.minecraft.world.level.block.Block;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;

public final class GeomeGeology {
	private static final double GEOME_TRANSITION_SCORE_WIDTH = 0.125D;
	private static final int[] LITHOLOGY_PHASES = {
			0, 2, 1, 3,
			3, 1, 2, 0,
			1, 3, 0, 2,
			2, 0, 3, 1
	};
	private static final int[] LITHOLOGY_ROCK_SALTS = { 0x00, 0x9E, 0x3C, 0xDA };

	private final BakedGeomeConfig config;
	private final PerlinNoise2D regionalNoise;
	private final PerlinNoise2D boundaryNoise;
	private final PerlinNoise2D formationRegionNoise;
	private final PerlinNoise2D stratumNoise;
	private final short[] whiteNoiseArray;
	private final boolean[] globallyContinuousLayers;
	private final boolean[] regionallyVariedRocks;
	private final int geomeTransitionPhase;
	private final int layerThickness;
	private final int formationRegionScale;
	private final int familyDiversitySlots;
	private final boolean stableLayers;

	public GeomeGeology(long seed, BakedGeomeConfig config) {
		this.config = config;
		FormationSettings formations = config.formations;
		layerThickness = formations.verticalThickness;
		stableLayers = formations.usesStableLayers();
		regionalNoise = stableLayers
				? PerlinNoise2D.normalized(seed ^ 0x47E04E4DL, 1.0F, (float) config.geomeScale, 2)
				: new PerlinNoise2D(seed ^ 0x47E04E4DL, 96.0F, (float) config.geomeScale, 2);
		boundaryNoise = stableLayers
				? PerlinNoise2D.normalized(~seed ^ 0x1BADC0DEL, 1.0F,
						(float) (config.geomeScale * 0.45D), 2)
				: new PerlinNoise2D(~seed ^ 0x1BADC0DEL, 48.0F,
						(float) (config.geomeScale * 0.45D), 2);
		formationRegionNoise = new PerlinNoise2D(~seed, stableLayers ? 96.0F : 128.0F,
				(float) formations.familyRegionWavelength, 2);
		formationRegionScale = Math.max(16, (int) Math.round(formations.familyRegionWavelength));
		familyDiversitySlots = formations.familyDiversitySlots();
		if (stableLayers) {
			stratumNoise = PerlinNoise2D.independentStrata(seed,
					(float) formations.wavinessAmplitude, (float) formations.stratumWavelength,
					(float) formations.edgeAmplitude, (float) formations.edgeWavelength,
					formations.edgeOctaves);
		} else {
			stratumNoise = PerlinNoise2D.normalized(seed, (float) formations.wavinessAmplitude,
					(float) formations.stratumWavelength, formations.edgeOctaves);
		}

		Random random = new Random(seed ^ 0x5EEDBEEFL);
		geomeTransitionPhase = new Random(seed ^ 0x47454F4D4554524EL).nextInt(256);
		whiteNoiseArray = new short[256];
		for (int i = 0; i < whiteNoiseArray.length; i++) {
			whiteNoiseArray[i] = (short) random.nextInt(0x7FFF);
		}
		globallyContinuousLayers = new boolean[256];
		regionallyVariedRocks = new boolean[256];
		Random continuityRandom = new Random(seed ^ 0x434F4E54494E5545L);
		Random faciesRandom = new Random(seed ^ 0x464143494553L);
		double faciesFraction = faciesFraction(formations.familyRegionWavelength);
		for (int i = 0; i < globallyContinuousLayers.length; i++) {
			globallyContinuousLayers[i] = continuityRandom.nextDouble() < formations.continuity;
			regionallyVariedRocks[i] = !globallyContinuousLayers[i]
					|| faciesRandom.nextDouble() < faciesFraction;
		}
	}

	public void replaceStoneInChunk(LevelAccessor world, ChunkAccess chunk, BakedTerrainDimension terrain) {
		ChunkPos chunkPos = chunk.getPos();
		int xOffset = chunkPos.getMinBlockX();
		int zOffset = chunkPos.getMinBlockZ();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		double[] regionalValues = new double[config.geomeCount()];
		boolean changed = false;

		for (int dx = 0; dx < 16; dx++) {
			int x = xOffset + dx;
			for (int dz = 0; dz < 16; dz++) {
				int z = zOffset + dz;
				int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, dx, dz);
				cursor.set(x, surfaceY, z);
				Holder<Biome> biomeHolder = world.getBiome(cursor);
				Biome biome = biomeHolder.value();
				Optional<ResourceKey<Biome>> biomeKey = biomeHolder.unwrapKey();
				Identifier biomeId = biomeKey.isPresent() ? biomeKey.get().identifier() : null;
				if (!terrain.acceptsBiome(biomeId)) {
					continue;
				}
				int geomeIndex = classifyColumn(biome, biomeId, x, z, regionalValues);
				int baseRockValue = stratumOffsetAt(x, z);
				long formationRegion = formationRegionAt(x, z);

				if (stableLayers) {
					int secondGeome = runnerUpGeome(regionalValues, geomeIndex);
					changed |= replaceStableColumn(chunk, cursor, geomeIndex, secondGeome,
							regionalValues, baseRockValue,
							formationRegion, x, z, surfaceY, terrain);
				} else {
					for (int y = surfaceY; y >= chunk.getMinY(); y--) {
						cursor.set(x, y, z);
						if (terrain.isReplaceable(chunk.getBlockState(cursor))) {
							chunk.setBlockState(cursor,
									pickReplacement(geomeIndex, baseRockValue, formationRegion, x, y, z), 0);
							changed = true;
						}
					}
				}
			}
		}

		if (changed) {
			chunk.markUnsaved();
		}
	}

	private boolean replaceStableColumn(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, int geomeIndex,
			int secondGeome, double[] geomeScores, int baseRockValue,
			long formationRegion, int x, int z, int surfaceY,
			BakedTerrainDimension terrain) {
		int layerIndex = Math.floorDiv(baseRockValue + surfaceY, layerThickness);
		int layerStart = layerIndex * layerThickness;
		int layerGeome = pickStableLayerGeome(geomeScores, geomeIndex, secondGeome,
				layerIndex, geomeTransitionPhase);
		BlockState replacement = pickStableReplacement(layerGeome, formationRegion, layerIndex);
		boolean changed = false;
		cursor.set(x, surfaceY, z);

		for (int y = surfaceY; y >= chunk.getMinY(); y--) {
			int stratum = baseRockValue + y;
			if (stratum < layerStart) {
				layerIndex--;
				layerStart -= layerThickness;
				layerGeome = pickStableLayerGeome(geomeScores, geomeIndex, secondGeome,
						layerIndex, geomeTransitionPhase);
				replacement = pickStableReplacement(layerGeome, formationRegion, layerIndex);
			}
			cursor.setY(y);
			if (terrain.isReplaceable(chunk.getBlockState(cursor))) {
				chunk.setBlockState(cursor, replacement, 0);
				changed = true;
			}
		}
		return changed;
	}

	public Block getStoneAt(Biome biome, int x, int y, int z, int surfaceY) {
		double[] regionalValues = new double[config.geomeCount()];
		int geomeIndex = classifyColumn(biome, x, z, regionalValues);
		int stratumOffset = stratumOffsetAt(x, z);
		long formationRegion = formationRegionAt(x, z);
		if (stableLayers) {
			int layerIndex = Math.floorDiv(stratumOffset + y, layerThickness);
			geomeIndex = pickStableLayerGeome(regionalValues, geomeIndex,
					runnerUpGeome(regionalValues, geomeIndex), layerIndex, geomeTransitionPhase);
		}
		return pickReplacement(geomeIndex, stratumOffset, formationRegion, x, y, z).getBlock();
	}

	public String getGeomeName(Biome biome, int x, int z) {
		double[] regionalValues = new double[config.geomeCount()];
		return config.geomeName(classifyColumn(biome, x, z, regionalValues));
	}

	int classifyColumn(Biome biome, int x, int z, double[] regionalValues) {
		return classifyColumn(biome, null, x, z, regionalValues);
	}

	/**
	 * Classifies a column once for read-only API sampling. All Y queries on the
	 * returned object reuse the same biome, geome, stratum, and formation data.
	 */
	public ColumnSample sampleColumn(Biome biome, Identifier biomeId, int x, int z) {
		double[] regionalValues = new double[config.geomeCount()];
		int geomeIndex = classifyColumn(biome, biomeId, x, z, regionalValues);
		return new ColumnSample(geomeIndex, runnerUpGeome(regionalValues, geomeIndex),
				regionalValues, stratumOffsetAt(x, z), formationRegionAt(x, z), x, z);
	}

	public final class ColumnSample {
		private final int geomeIndex;
		private final int secondGeome;
		private final double[] geomeScores;
		private final int stratumOffset;
		private final long formationRegion;
		private final int x;
		private final int z;

		private ColumnSample(int geomeIndex, int secondGeome, double[] geomeScores,
				int stratumOffset, long formationRegion, int x, int z) {
			this.geomeIndex = geomeIndex;
			this.secondGeome = secondGeome;
			this.geomeScores = geomeScores;
			this.stratumOffset = stratumOffset;
			this.formationRegion = formationRegion;
			this.x = x;
			this.z = z;
		}

		public String geomeName() {
			return config.geomeName(geomeIndex);
		}

		public BlockState rockAt(int y) {
			int selectedGeome = geomeIndex;
			if (stableLayers) {
				int layerIndex = Math.floorDiv(stratumOffset + y, layerThickness);
				selectedGeome = pickStableLayerGeome(geomeScores, geomeIndex, secondGeome,
						layerIndex, geomeTransitionPhase);
			}
			return pickReplacement(selectedGeome, stratumOffset, formationRegion, x, y, z);
		}

		public RockFamily familyAt(int y) {
			return config.familyOf(rockAt(y));
		}
	}

	int classifyColumn(Biome biome, Identifier biomeId, int x, int z, double[] regionalValues) {
		for (int i = 0; i < regionalValues.length; i++) {
			regionalValues[i] = regionalNoise.valueAt(x + config.noiseOffsetX[i], z + config.noiseOffsetZ[i]);
		}

		double boundary = boundaryNoise.valueAt(x, z);
		return config.scoreGeomes(biome, biomeId, regionalValues, boundary);
	}

	private net.minecraft.world.level.block.state.BlockState pickReplacement(int geomeIndex, int baseRockValue,
			long formationRegion, int x, int y, int z) {
		int stratum = baseRockValue + y;
		int layerIndex = Math.floorDiv(stratum, layerThickness);
		if (stableLayers) {
			return pickStableReplacement(geomeIndex, formationRegion, layerIndex);
		}

		int layerY = y + (layerThickness / 2) - Math.floorMod(stratum, layerThickness);
		int familyHash = whiteNoiseArray[(layerIndex + (geomeIndex * 37)) & 0xFF];
		RockFamily family = pickShapedFamily(geomeIndex, x, y, z, layerY, familyHash);
		int rockHash = whiteNoiseArray[((layerIndex * 31) + (family.ordinal() * 53) + (geomeIndex * 79)) & 0xFF];
		return config.pickRock(geomeIndex, family, layerY, rockHash);
	}

	private BlockState pickStableReplacement(int geomeIndex, long formationRegion, int layerIndex) {
		// A dipping or uplifted layer keeps the depth identity it had in stratum space.
		int formationY = (layerIndex * layerThickness) + (layerThickness / 2);
		int layerBucket = layerIndex & 0xFF;
		int regionHash = (int) (formationRegion >> 32);
		int regionalIndex = (layerIndex + regionHash) & 0xFF;
		int originalFamilyIndex = globallyContinuousLayers[layerBucket] ? layerBucket : regionalIndex;
		int familyBucket;
		int familySlot = 0;
		if (familyDiversitySlots == 1) {
			familyBucket = whiteNoiseArray[originalFamilyIndex] & 0xFF;
		} else {
			int familyGroup = Math.floorDiv(layerIndex, familyDiversitySlots);
			int groupBucket = familyGroup & 0xFF;
			int regionalGroup = (familyGroup + regionHash) & 0xFF;
			int familyIndex = globallyContinuousLayers[groupBucket] ? groupBucket : regionalGroup;
			familyBucket = whiteNoiseArray[familyIndex] & 0xFF;
			familySlot = (layerIndex + (int) formationRegion) & (familyDiversitySlots - 1);
		}
		int rockIndex = regionallyVariedRocks[layerBucket] ? regionalIndex : originalFamilyIndex;
		int rockBucket = whiteNoiseArray[rockIndex] & 0xFF;
		if (familyDiversitySlots > 1) {
			// Reuse the same rendezvous table while preventing thick single-family provinces
			// from collapsing onto one exact rock.
			rockBucket ^= LITHOLOGY_ROCK_SALTS[familySlot];
		}
		RockFamily family = config.pickFamily(geomeIndex, formationY, familyBucket, familySlot);
		return config.pickRock(geomeIndex, family, formationY, rockBucket);
	}

	int stratumOffsetAt(int x, int z) {
		return (int) stratumNoise.valueAt(x, z);
	}

	int stratumLayerAt(int x, int y, int z) {
		return Math.floorDiv(stratumOffsetAt(x, z) + y, layerThickness);
	}

	Block getStoneAt(int geomeIndex, double[] geomeScores, int stratumOffset,
			long formationRegion, int x, int y, int z) {
		if (stableLayers) {
			int layerIndex = Math.floorDiv(stratumOffset + y, layerThickness);
			geomeIndex = pickStableLayerGeome(geomeScores, geomeIndex,
					runnerUpGeome(geomeScores, geomeIndex), layerIndex, geomeTransitionPhase);
		}
		return pickReplacement(geomeIndex, stratumOffset, formationRegion, x, y, z).getBlock();
	}

	long formationRegionAt(int x, int z) {
		if (!stableLayers) {
			return 0L;
		}
		double regionalValue = formationRegionNoise.valueAt(x, z);
		int contour = Math.floorDiv((int) Math.floor(regionalValue), 96);
		int warpedX = x + (int) Math.floor(regionalValue);
		int warpedZ = z - (int) Math.floor(regionalValue * 0.61803398875D);
		int cellX = Math.floorDiv(warpedX, formationRegionScale);
		int cellZ = Math.floorDiv(warpedZ, formationRegionScale);
		int regionHash = mixRegion(cellX, cellZ, contour);
		int localCell = (Math.floorMod(cellZ, 4) << 2) | Math.floorMod(cellX, 4);
		int superHash = mixRegion(Math.floorDiv(cellX, 4), Math.floorDiv(cellZ, 4), contour ^ 0x51A7);
		int lithologyPhase = (LITHOLOGY_PHASES[localCell] + superHash) & 0x03;
		return ((long) regionHash << 32) | (lithologyPhase & 0xFFFFFFFFL);
	}

	private static int mixRegion(int cellX, int cellZ, int contour) {
		int hash = (cellX * 0x1F1F1F1F) ^ Integer.rotateLeft(cellZ * 0x6D2B79F5, 11)
				^ (contour * 0x5D588B65);
		hash = (hash ^ (hash >>> 16)) * 0x7FEB352D;
		hash = (hash ^ (hash >>> 15)) * 0x846CA68B;
		return hash ^ (hash >>> 16);
	}

	static int pickStableLayerGeome(double[] geomeScores, int firstGeome, int secondGeome,
			int layerIndex, int phase) {
		if (firstGeome == secondGeome) {
			return firstGeome;
		}
		int lowerGeome = Math.min(firstGeome, secondGeome);
		int higherGeome = Math.max(firstGeome, secondGeome);
		double higherFraction = 0.5D + ((geomeScores[higherGeome] - geomeScores[lowerGeome])
				/ (2.0D * GEOME_TRANSITION_SCORE_WIDTH));
		if (higherFraction <= 0.0D) {
			return lowerGeome;
		}
		if (higherFraction >= 1.0D) {
			return higherGeome;
		}

		// Bit reversal supplies an allocation-free low-discrepancy sequence. Nearby
		// layers therefore cross a close geome boundary at different horizontal
		// positions instead of moving as one full-height wall.
		int pairPhase = (lowerGeome * 53) + (higherGeome * 97);
		int layerBucket = (layerIndex + phase + pairPhase) & 0xFF;
		int threshold = Integer.reverse(layerBucket) >>> 24;
		return ((threshold + 0.5D) / 256.0D) < higherFraction ? higherGeome : lowerGeome;
	}

	private static int runnerUpGeome(double[] geomeScores, int bestGeome) {
		if (geomeScores.length < 2) {
			return bestGeome;
		}
		int second = bestGeome == 0 ? 1 : 0;
		for (int i = 0; i < geomeScores.length; i++) {
			if (i != bestGeome && geomeScores[i] > geomeScores[second]) {
				second = i;
			}
		}
		return second;
	}

	private static double faciesFraction(double regionScale) {
		if (regionScale <= 100.0D) {
			return Math.max(0.0D, (regionScale - 50.0D) / 350.0D);
		}
		if (regionScale <= 200.0D) {
			return (200.0D - regionScale) / 700.0D;
		}
		return Math.min(1.0D, (regionScale - 200.0D) / 440.0D);
	}

	private RockFamily pickShapedFamily(int geomeIndex, int x, int y, int z, int layerY, int familyHash) {
		double shapedFamily = formationRegionNoise.valueAt(x, z) + y;
		boolean nearBoundary = Math.abs(shapedFamily + 32.0D) < 12.0D || Math.abs(shapedFamily - 32.0D) < 12.0D;
		if (nearBoundary && (familyHash & 0x03) == 0) {
			return config.pickFamily(geomeIndex, layerY, familyHash);
		}

		if (shapedFamily < -32.0D) {
			return y > 48 ? RockFamily.IGNEOUS_VOLCANIC : RockFamily.IGNEOUS_INTRUSIVE;
		} else if (shapedFamily < 32.0D) {
			return RockFamily.METAMORPHIC;
		}

		return RockFamily.SEDIMENTARY;
	}
}
