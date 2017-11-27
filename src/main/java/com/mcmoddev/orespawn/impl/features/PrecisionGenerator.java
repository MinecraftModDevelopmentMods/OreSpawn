package com.mcmoddev.orespawn.impl.features;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.FeatureBase;
import com.mcmoddev.orespawn.api.GeneratorParameters;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.data.Constants.FormatBits;
import com.mcmoddev.orespawn.util.OreList;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.IChunkGenerator;

public class PrecisionGenerator extends FeatureBase implements IFeature {

	private PrecisionGenerator(Random rand) {
		super(rand);
	}

	public PrecisionGenerator() {
		this(new Random());
	}


	@Override
	public void generate(World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider,
	    GeneratorParameters parameters) {
		ChunkPos pos = parameters.getChunk();
		List<IBlockState> blockReplace = new LinkedList<>();
		blockReplace.addAll(parameters.getReplacements());
		JsonObject params = parameters.getParameters();
		OreList ores = parameters.getOres();
		BiomeLocation biomes = parameters.getBiomes();

		// First, load cached blocks for neighboring chunk ore spawns
		int chunkX = pos.chunkXPos;
		int chunkZ = pos.chunkZPos;

		mergeDefaults(params, getDefaultParameters());

		runCache(chunkX, chunkZ, world, blockReplace);

		// extract parameters
		int nodeCount = params.get(FormatBits.NODE_COUNT).getAsInt();
		int maxHeight = params.get(FormatBits.MAX_HEIGHT).getAsInt();
		int minHeight = params.get(FormatBits.MIN_HEIGHT).getAsInt();
		int nodeSize  = params.get(FormatBits.NODE_SIZE).getAsInt();

		int thisNode = nodeSize;

		// now to use them
		for (int c = nodeCount; c > 0; c--) {
			int sc;
			HeightRange hr = new HeightRange(minHeight, maxHeight);
			BlockPos spot = chooseSpot(chunkX, chunkZ, hr);
			FunctionParameterWrapper fp = new FunctionParameterWrapper();
			fp.setBlockPos(spot);
			fp.setWorld(world);
			fp.setReplacements(blockReplace);
			fp.setBiomes(biomes);
			fp.setOres(ores);
			fp.setChunkPos(new ChunkPos(chunkX, chunkZ));

			sc = spawnAtSpot(thisNode, hr, fp);

			// bit of feedback - if we underproduce or overproduce a node, the next one gets a correction
			if (sc != thisNode && sc != 0) {
				thisNode += (nodeSize - sc);
				OreSpawn.LOGGER.debug("node at %s of size %d instead of %d - modding to %d",
				    spot, sc, nodeSize, thisNode);
			} else if (sc == thisNode) {
				// if we produced exact size, reset the size
				thisNode = nodeSize;
			}

			// if we hit a node of size zero or less, we've done something wrong
			if (thisNode <= 0) {
				thisNode = nodeSize;
			}
		}
	}

	private int spawnAtSpot(int nodeSize, HeightRange heightRange, FunctionParameterWrapper params) {
		int spawned = 0;
		int c;

		FunctionParameterWrapper np = new FunctionParameterWrapper(params);
		BlockPos act = params.getBlockPos();
		int counter = nodeSize;

		while (counter > 0 && spawned < nodeSize) {
			np.setBlockPos(act);
			c = spawnOreNode(np, nodeSize, heightRange);

			if (c == 0) {
				OreSpawn.LOGGER.debug("Unable to place block at %s (chunk %s)", np.getBlockPos(), np.getChunkPos());
				act = chooseSpot(Math.floorDiv(params.getBlockPos().getX(), 16), Math.floorDiv(params.getBlockPos().getZ(), 16), heightRange);
			}

			counter -= (c + 1);
			spawned += c;
		}

		return spawned;
	}

	private int spawnOreNode(FunctionParameterWrapper params, int nodeSize, HeightRange heightRange) {

		int count = nodeSize;
		int lutType = (nodeSize < 8) ? offsetIndexRef_small.length : offsetIndexRef.length;
		int[] lut = (nodeSize < 8) ? offsetIndexRef_small : offsetIndexRef;
		Vec3i[] offs = new Vec3i[lutType];

		System.arraycopy((nodeSize < 8) ? offsets_small : offsets, 0, offs, 0, lutType);

		if (nodeSize < 27) {
			int[] scrambledLUT = new int[lutType];
			System.arraycopy(lut, 0, scrambledLUT, 0, scrambledLUT.length);
			scramble(scrambledLUT, this.random);

			int nc = 0;

			for (; count > 0 && nc <= nodeSize; count--) {
				IBlockState oreBlock = params.getOres().getRandomOre(this.random).getOre();
				Vec3i offset = offs[scrambledLUT[--count]];
				BlockPos p = fixMungeOffset(offset, params.getBlockPos(), heightRange, params.getChunkPos());
				int dimension = params.getWorld().provider.getDimension();

				if (spawn(oreBlock, params.getWorld(), p, dimension, true, params.getReplacements(), params.getBiomes())) {
					nc++;
				}
			}

			return nc;
		}

		return spawnFill(params, nodeSize, heightRange);
	}

	private BlockPos fixMungeOffset(Vec3i offset, BlockPos spot, HeightRange heightRange, ChunkPos pos) {
		BlockPos p = spot.add(offset);
		ChunkPos x1z1 = new ChunkPos(pos.chunkXPos + 1, pos.chunkZPos + 1);
		int xMax = x1z1.getXEnd();
		int zMax = x1z1.getZEnd();
		int xMin = pos.getXStart();
		int zMin = pos.getZStart();

		int xmod = offset.getX();
		int ymod = offset.getY();
		int zmod = offset.getZ();

		// correct the points values to not cause the Y coordinate to go outside the permissable range
		if (p.getY() < heightRange.getMin() || p.getY() > heightRange.getMax()) {
			ymod = rescaleOffset(ymod, spot.getY(), heightRange.getMin(), heightRange.getMax());
		}

		if (p.getX() < xMin || p.getX() > xMax) {
			xmod = rescaleOffset(xmod, spot.getX(), xMin, xMax);
		}

		if (p.getZ() < zMin || p.getZ() > zMax) {
			zmod = rescaleOffset(zmod, spot.getZ(), zMin, zMax);
		}

		BlockPos rVal = spot.add(xmod, ymod, zmod);
		OreSpawn.LOGGER.debug("rescaled %s to %s", spot, rVal);
		return rVal;
	}

	private int rescaleOffset(final int offsetIn, final int centerIn, final int minimumIn, final int maximumIn) {
		int actual = centerIn + offsetIn;
		int wrapDistance;
		int range = maximumIn - minimumIn;
		int workingPoint;

		if (actual < minimumIn) {
			wrapDistance = minimumIn - actual;
		} else {
			wrapDistance = actual - maximumIn;
		}

		if (wrapDistance < 0) {
			wrapDistance = ((-1) * wrapDistance) % range;
		} else {
			wrapDistance %= range;
		}

		if (actual < minimumIn) {
			workingPoint = maximumIn - wrapDistance;
		} else {
			workingPoint = minimumIn + wrapDistance;
		}

		return workingPoint - centerIn;
	}

	private int spawnFill(FunctionParameterWrapper params, int nodeSize, HeightRange heightRange) {

		double radius = Math.pow(nodeSize, 1.0 / 3.0) * (3.0 / 4.0 / Math.PI) + 2;
		int rSqr = (int)Math.ceil(radius * radius);

		if (this.random.nextBoolean()) {
			return spawnPrecise(params, heightRange, false, radius, rSqr, nodeSize);
		} else {
			return spawnPrecise(params, heightRange, true, radius, rSqr, nodeSize);
		}
	}

	private int spawnPrecise(FunctionParameterWrapper params, HeightRange heightRange, boolean toPositive, double radius,
	    int rSqr, int nodeSize) {
		int quantity = nodeSize;
		int nc = 0;

		for (int dy = (int)(-1 * radius); dy < radius; dy++) {
			for (int dx = getStart(toPositive, radius); endCheck(toPositive, dx, radius); dx = countItem(dx, toPositive)) {
				for (int dz = getStart(toPositive, radius); endCheck(toPositive, dz, radius); dz = countItem(dz, toPositive)) {
					if (doCheckSpawn(dx, dy, dz, rSqr, heightRange, params) >= 0) {
						nc++;
						quantity--;

						if (nc >= nodeSize || quantity <= 0) {
							return nc;
						}
					}
				}
			}
		}

		return nc;
	}

	private int doCheckSpawn(int dx, int dy, int dz, int rSqr, HeightRange heightRange, FunctionParameterWrapper params) {
		if (getABC(dx, dy, dz) <= rSqr) {
			BlockPos p = fixMungeOffset(new Vec3i(dx, dy, dz), params.getBlockPos(), heightRange, params.getChunkPos());
			IBlockState bl = params.getOres().getRandomOre(this.random).getOre();
			return spawn(bl, params.getWorld(), p, params.getWorld().provider.getDimension(), true,
			        params.getReplacements(), params.getBiomes()) ? 1 : 0;
		}

		return -1;
	}

	private int getPoint(int lowerBound, int upperBound) {
		List<Integer> arr = new ArrayList<> ();

		for (int i = lowerBound; i <= upperBound; i++) {
			arr.add(i);
		}

		return arr.get(this.random.nextInt(arr.size()));
	}

	private BlockPos chooseSpot(int xPosition, int zPosition, HeightRange heightRange) {
		int xRet = getPoint(0, 15) + (xPosition * 16);
		int zRet = getPoint(0, 15) + (zPosition * 16);
		int yRet = getPoint(heightRange.getMin(), heightRange.getMax());

		return new BlockPos(xRet, yRet, zRet);
	}

	@Override
	public void setRandom(Random rand) {
		this.random = rand;
	}

	@Override
	public JsonObject getDefaultParameters() {
		JsonObject defaults = new JsonObject();
		defaults.addProperty(FormatBits.NODE_COUNT, 4);
		defaults.addProperty(FormatBits.MIN_HEIGHT, 16);
		defaults.addProperty(FormatBits.MAX_HEIGHT, 80);
		defaults.addProperty(FormatBits.NODE_SIZE, 8);
		return defaults;
	}

	private class HeightRange {
		private int min;
		private int max;

		HeightRange(int min, int max) {
			this.min = min;
			this.max = max;
		}

		int getMin() {
			return this.min;
		}

		int getMax() {
			return this.max;
		}
	}
}
