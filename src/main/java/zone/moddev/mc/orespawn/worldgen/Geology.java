package zone.moddev.mc.orespawn.worldgen;

import java.util.Random;

import zone.moddev.mc.orespawn.worldgen.math.PerlinNoise2D;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;

public class Geology {
	private final PerlinNoise2D geomeNoiseLayer;
	private final PerlinNoise2D rockNoiseLayer;
	private final short[] whiteNoiseArray;
	private final IBlockState[] igneousStones;
	private final IBlockState[] metamorphicStones;
	private final IBlockState[] sedimentaryStones;
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

	public void replaceStoneInChunk(World world, Chunk chunk, BakedTerrainDimension terrain) {
		ChunkPos chunkPos = chunk.getPos();
		int xOffset = chunkPos.getXStart();
		int zOffset = chunkPos.getZStart();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean changed = false;

		for (int dx = 0; dx < 16; dx++) {
			int x = xOffset + dx;
			for (int dz = 0; dz < 16; dz++) {
				int z = zOffset + dz;
				int y = chunk.getHeightValue(dx, dz) - 1;
				if (terrain.hasBiomeFilter()) {
					cursor.setPos(x, y, z);
					Biome biome = world.getBiome(cursor);
					ResourceLocation biomeId = WorldIds.biome(biome);
					if (!terrain.acceptsBiome(biomeId)) {
						continue;
					}
				}
				int baseRockVal = (int) rockNoiseLayer.valueAt(x, z);
				int geomeBase = (int) geomeNoiseLayer.valueAt(x, z);

				for (; y >= 0; y--) {
					cursor.setPos(x, y, z);
					IBlockState current = chunk.getBlockState(cursor);
					if (terrain.isReplaceable(current)) {
						IBlockState replacement = pickReplacement(baseRockVal, geomeBase, y);
						if (!GeomeGeology.changes(current, replacement)) continue;
						chunk.setBlockState(cursor, replacement);
						changed = true;
					}
				}
			}
		}

		if (changed) {
			chunk.markDirty();
		}
	}

	private IBlockState pickReplacement(int baseRockVal, int geomeBase, int y) {
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

	private IBlockState pickStateFromList(int value, IBlockState[] list) {
		if (list.length == 0) {
			return Blocks.STONE.getDefaultState();
		}

		return list[whiteNoiseArray[(value / layerThickness) & 0xFF] % list.length];
	}

}
