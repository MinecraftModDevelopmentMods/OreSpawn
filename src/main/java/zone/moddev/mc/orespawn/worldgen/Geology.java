package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import zone.moddev.mc.orespawn.worldgen.math.PerlinNoise2D;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Geology {
	private static final Logger LOGGER = LogManager.getLogger();
	private final PerlinNoise2D geomeNoiseLayer;
	private final PerlinNoise2D rockNoiseLayer;
	private final short[] whiteNoiseArray;
	private final BlockState[] igneousStones;
	private final BlockState[] metamorphicStones;
	private final BlockState[] sedimentaryStones;
	private final int layerThickness;
	private final boolean realisticCoalLayers;

	public Geology(long seed, double geomeSize, double rockLayerSize, int layerThickness,
			BakedGeomeConfig config) {
		this(seed, geomeSize, rockLayerSize, layerThickness, false,
				config.statesForFamily(RockFamily.IGNEOUS_INTRUSIVE, RockFamily.IGNEOUS_VOLCANIC),
				config.statesForFamily(RockFamily.METAMORPHIC),
				config.statesForFamily(RockFamily.SEDIMENTARY));
	}

	Geology(long seed, WorldGeologyProfile profile, BakedGeomeConfig config) {
		this(seed, profile.cyanoGeomeSize(), profile.cyanoRockLayerNoise(),
				profile.cyanoLayerThickness(), profile.cyanoRealisticCoalLayers(),
				resolveRockOrder(profile, "igneous_rocks",
						config.statesForFamily(RockFamily.IGNEOUS_INTRUSIVE, RockFamily.IGNEOUS_VOLCANIC)),
				resolveRockOrder(profile, "metamorphic_rocks",
						config.statesForFamily(RockFamily.METAMORPHIC)),
				resolveRockOrder(profile, "sedimentary_rocks",
						config.statesForFamily(RockFamily.SEDIMENTARY)));
	}

	Geology(long seed, double geomeSize, double rockLayerSize, int layerThickness,
			boolean realisticCoalLayers, BlockState[] igneousStones,
			BlockState[] metamorphicStones, BlockState[] sedimentaryStones) {
		this.layerThickness = layerThickness;
		this.realisticCoalLayers = realisticCoalLayers;
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

		this.igneousStones = igneousStones;
		this.metamorphicStones = metamorphicStones;
		this.sedimentaryStones = sedimentaryStones;
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
					Biome biome = world.getBiome(cursor);
					ResourceLocation biomeId = world.registryAccess()
							.registryOrThrow(Registry.BIOME_REGISTRY).getKey(biome);
					if (!terrain.acceptsBiome(biomeId)) {
						continue;
					}
				}
				int baseRockVal = (int) rockNoiseLayer.valueAt(x, z);
				int geomeBase = (int) geomeNoiseLayer.valueAt(x, z);

				for (; y >= chunk.getMinBuildHeight(); y--) {
					cursor.set(x, y, z);
					BlockState current = chunk.getBlockState(cursor);
					if (terrain.isReplaceable(current)
							|| (realisticCoalLayers && current.getBlock() == Blocks.COAL_ORE)) {
						BlockState replacement = pickReplacement(baseRockVal, geomeBase, y);
						if (!GeomeGeology.changes(current, replacement)) continue;
						chunk.setBlockState(cursor, replacement, false);
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

	static BlockState[] resolveRockOrder(WorldGeologyProfile profile, String key,
			BlockState[] fallback) {
		if (!profile.hasCyanoRockOrder(key)) return fallback;
		List<BlockState> states = new ArrayList<>();
		for (String idText : profile.cyanoRockOrder(key)) {
			try {
				ResourceLocation id = new ResourceLocation(idText);
				Block block = ForgeRegistries.BLOCKS.containsKey(id)
						? ForgeRegistries.BLOCKS.getValue(id) : null;
				if (block != null && block != Blocks.AIR) {
					states.add(block.getDefaultState());
				} else {
					LOGGER.warn("Legacy Mineralogy rock '{}' is not registered and will be omitted", id);
				}
			} catch (RuntimeException e) {
				LOGGER.warn("Legacy Mineralogy rock registry name '{}' is invalid and will be omitted", idText);
			}
		}
		if (states.isEmpty()) {
			LOGGER.warn("No snapshotted legacy Mineralogy rocks for '{}' are registered; "
					+ "using the matching provider family as a safe fallback", key);
			return fallback;
		}
		return states.toArray(new BlockState[states.size()]);
	}

}
