package com.mcmoddev.orespawn.worldgen;

import java.util.Random;

import com.mcmoddev.orespawn.worldgen.math.PerlinNoise2D;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

public class Geology {
	private final PerlinNoise2D geomeNoiseLayer;
	private final PerlinNoise2D rockNoiseLayer;
	private final short[] whiteNoiseArray;
	private final BlockState[] igneousStones;
	private final BlockState[] metamorphicStones;
	private final BlockState[] sedimentaryStones;
	private final int layerThickness;

	public Geology(long seed, double geomeSize, double rockLayerSize, int layerThickness,
			BakedGeomeConfig config) {
		this.layerThickness = layerThickness;
		int rockLayerUndertones = 4;
		int undertoneMultiplier = 1 << (rockLayerUndertones - 1);
		geomeNoiseLayer = new PerlinNoise2D(~seed, 128, (float) geomeSize, 2);
		rockNoiseLayer = new PerlinNoise2D(seed, (float) (4 * undertoneMultiplier),
				(float) (rockLayerSize * undertoneMultiplier), rockLayerUndertones);

		Random random = new Random(seed);
		whiteNoiseArray = new short[256];
		for (int i = 0; i < whiteNoiseArray.length; i++) {
			whiteNoiseArray[i] = (short) random.nextInt(0x7FFF);
		}

		igneousStones = config.statesForFamily(RockFamily.IGNEOUS_INTRUSIVE, RockFamily.IGNEOUS_VOLCANIC);
		metamorphicStones = config.statesForFamily(RockFamily.METAMORPHIC);
		sedimentaryStones = config.statesForFamily(RockFamily.SEDIMENTARY);
	}

	public Block getStoneAt(int x, int y, int z) {
		float geome = geomeNoiseLayer.valueAt(x, z) + y;
		int rockValue = (int) rockNoiseLayer.valueAt(x, z) + y;
		if (geome < -64) {
			return pickStateFromList(rockValue, igneousStones).getBlock();
		} else if (geome < 64) {
			return pickStateFromList(rockValue, metamorphicStones).getBlock();
		}

		return pickStateFromList(rockValue, sedimentaryStones).getBlock();
	}

	public void replaceStoneInChunk(LevelAccessor world, ChunkAccess chunk, BakedTerrainDimension terrain) {
		ChunkPos chunkPos = chunk.getPos();
		int xOffset = chunkPos.getMinBlockX();
		int zOffset = chunkPos.getMinBlockZ();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean changed = false;

		for (int dx = 0; dx < 16; dx++) {
			int x = xOffset + dx;
			for (int dz = 0; dz < 16; dz++) {
				int z = zOffset + dz;
				int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, dx, dz);
				if (terrain.hasBiomeFilter()) {
					cursor.set(x, y, z);
					Holder<Biome> biome = world.getBiome(cursor);
					ResourceLocation biomeId = biome.unwrapKey().map(key -> key.location()).orElse(null);
					if (!terrain.acceptsBiome(biomeId)) {
						continue;
					}
				}
				int baseRockVal = (int) rockNoiseLayer.valueAt(x, z);
				int geomeBase = (int) geomeNoiseLayer.valueAt(x, z);

				for (; y >= chunk.getMinBuildHeight(); y--) {
					cursor.set(x, y, z);
					if (terrain.isReplaceable(chunk.getBlockState(cursor))) {
						chunk.setBlockState(cursor, pickReplacement(baseRockVal, geomeBase, y), false);
						changed = true;
					}
				}
			}
		}

		if (changed) {
			chunk.setUnsaved(true);
		}
	}

	private BlockState pickReplacement(int baseRockVal, int geomeBase, int y) {
		int geome = geomeBase + y;
		if (geome < -32) {
			return pickStateFromList(baseRockVal + y, igneousStones);
		} else if (geome < 32) {
			return pickStateFromList(baseRockVal + y, metamorphicStones);
		}

		return pickStateFromList(baseRockVal + y, sedimentaryStones);
	}

	public Block[] getStoneColumn(int x, int z, int height) {
		Block[] column = new Block[height];
		int baseRockVal = (int) rockNoiseLayer.valueAt(x, z);
		double geomeBase = geomeNoiseLayer.valueAt(x, z);
		for (int y = 0; y < column.length; y++) {
			double geome = geomeBase + y;
			if (geome < -32) {
				column[y] = pickStateFromList(baseRockVal + y, igneousStones).getBlock();
			} else if (geome < 32) {
				column[y] = pickStateFromList(baseRockVal + y + 3, metamorphicStones).getBlock();
			} else {
				column[y] = pickStateFromList(baseRockVal + y + 5, sedimentaryStones).getBlock();
			}
		}
		return column;
	}

	private BlockState pickStateFromList(int value, BlockState[] list) {
		if (list.length == 0) {
			return Blocks.STONE.defaultBlockState();
		}

		return list[whiteNoiseArray[(value / layerThickness) & 0xFF] % list.length];
	}

}
