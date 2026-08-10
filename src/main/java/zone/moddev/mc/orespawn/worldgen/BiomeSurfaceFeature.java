package zone.moddev.mc.orespawn.worldgen;

import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Surface;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.placement.Placement;
import net.minecraft.world.gen.placement.NoPlacementConfig;

/** Applies provider surfaces after base surfaces and lakes, before late features. */
public final class BiomeSurfaceFeature extends ContextFeature<NoFeatureConfig> {
	public static final BiomeSurfaceFeature FEATURE = new BiomeSurfaceFeature();
	private static ConfiguredFeature<?> configuredFeature;

	private BiomeSurfaceFeature() {
		super(NoFeatureConfig::deserialize);
		setRegistryName(OreSpawn.MODID, "biome_surfaces");
	}

	public static void registerConfiguredFeature() {
		configuredFeature = net.minecraft.world.biome.Biome.createDecoratedFeature(
				FEATURE, new NoFeatureConfig(), Placement.NOPE, new NoPlacementConfig());
	}

	static ConfiguredFeature<?> configuredFeature() {
		return configuredFeature;
	}

	@Override
	boolean place(FeaturePlaceContext<NoFeatureConfig> context) {
		IWorld world = context.level();
		BakedBiomeWorldgen config = BiomeWorldgenManager.get(WorldIds.dimension(world));
		if (config == null || !config.hasSurfaces()) return false;
		IChunk chunk = world.getChunk(context.origin());
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean changed = false;
		int minX = chunk.getPos().getXStart();
		int minZ = chunk.getPos().getZStart();
		boolean ceilingDimension = WorldIds.NETHER.equals(WorldIds.dimension(world));
		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				int x = minX + localX;
				int z = minZ + localZ;
				int groundY;
				int ceilingY = Integer.MIN_VALUE;
				if (ceilingDimension) {
					long column = findCeilingAndGround(chunk, cursor, x, z,
							256, 0);
					ceilingY = (int) (column >> 32);
					groundY = (int) column;
				} else {
					groundY = findOpenGround(chunk, cursor, x, z, localX, localZ,
							0);
				}
				if (groundY >= 0) {
					changed |= applyGround(chunk, world, config, cursor, x, z,
							groundY, 0);
				}
				if (ceilingY >= 0) {
					changed |= applyCeiling(chunk, world, config, cursor, x, z, ceilingY);
				}
			}
		}
		return changed;
	}

	private static int findOpenGround(IChunk chunk, BlockPos.MutableBlockPos cursor,
			int x, int z, int localX, int localZ, int minY) {
		int y = chunk.getTopBlockY(Heightmap.Type.WORLD_SURFACE_WG, localX, localZ);
		while (y >= minY && open(chunk.getBlockState(cursor.setPos(x, y, z)))) y--;
		return y;
	}

	private static long findCeilingAndGround(IChunk chunk,
			BlockPos.MutableBlockPos cursor, int x, int z, int maxY, int minY) {
		int y = maxY - 1;
		while (y >= minY && open(chunk.getBlockState(cursor.setPos(x, y, z)))) y--;
		if (y < minY) return pack(Integer.MIN_VALUE, Integer.MIN_VALUE);
		while (y >= minY && !open(chunk.getBlockState(cursor.setPos(x, y, z)))) y--;
		int ceilingY = y + 1;
		while (y >= minY && open(chunk.getBlockState(cursor.setPos(x, y, z)))) y--;
		return pack(ceilingY, y);
	}

	private static boolean applyGround(IChunk chunk, IWorld world,
			BakedBiomeWorldgen config, BlockPos.MutableBlockPos cursor,
			int x, int z, int y, int minY) {
		cursor.setPos(x, y, z);
		BlockState source = chunk.getBlockState(cursor);
		if (!replaceable(source)) return false;
		Surface surface = config.surfaces.get(world.getBiome(cursor));
		if (surface == null) return false;
		cursor.setPos(x, y + 1, z);
		boolean underwater = !chunk.getBlockState(cursor).getFluidState().isEmpty();
		BlockState top = underwater && surface.underwater != null
				? surface.underwater : surface.top;
		boolean changed = false;
		cursor.setPos(x, y, z);
		if (top != null) {
			chunk.setBlockState(cursor, top, false);
			changed = true;
		}
		if (surface.filler != null) {
			for (int depth = 1; depth <= surface.fillerDepth && y - depth >= minY; depth++) {
				cursor.setPos(x, y - depth, z);
				if (!replaceable(chunk.getBlockState(cursor))) break;
				chunk.setBlockState(cursor, surface.filler, false);
				changed = true;
			}
		}
		return changed;
	}

	private static boolean applyCeiling(IChunk chunk, IWorld world,
			BakedBiomeWorldgen config, BlockPos.MutableBlockPos cursor,
			int x, int z, int ceilingY) {
		cursor.setPos(x, ceilingY, z);
		BlockState source = chunk.getBlockState(cursor);
		if (!replaceable(source)) return false;
		Surface surface = config.surfaces.get(world.getBiome(cursor));
		if (surface == null || surface.ceiling == null) return false;
		chunk.setBlockState(cursor, surface.ceiling, false);
		return true;
	}

	private static boolean open(BlockState state) {
		return state.isAir() || !state.getFluidState().isEmpty();
	}

	private static long pack(int high, int low) {
		return ((long) high << 32) | (low & 0xFFFFFFFFL);
	}

	private static boolean replaceable(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.hasTileEntity();
	}
}
