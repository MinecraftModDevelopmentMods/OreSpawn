package zone.moddev.mc.orespawn.worldgen;

import java.util.Random;

import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Surface;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fluids.IFluidBlock;

/** Provider surface pass run after base terrain and lakes, before decoration. */
public final class BiomeSurfaceFeature {
	public static final BiomeSurfaceFeature FEATURE = new BiomeSurfaceFeature();

	private BiomeSurfaceFeature() {
	}

	public static void registerConfiguredFeature() {
		// Forge 1.12 invokes this pass from OreSpawnWorldGenerator.
	}

	boolean generate(World world, Chunk chunk, Random random) {
		BakedBiomeWorldgen config = BiomeWorldgenManager.get(WorldIds.dimension(world));
		if (config == null || !config.hasSurfaces()) return false;
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
				int ceilingY = -1;
				if (ceilingDimension) {
					long column = findCeilingAndGround(chunk, cursor, x, z, 256, 0);
					ceilingY = (int) (column >> 32);
					groundY = (int) column;
				} else {
					groundY = findOpenGround(chunk, cursor, x, z, localX, localZ, 0);
				}
				if (groundY >= 0) changed |= applyGround(chunk, world, config, cursor, x, z, groundY, 0);
				if (ceilingY >= 0) changed |= applyCeiling(chunk, world, config, cursor, x, z, ceilingY);
			}
		}
		if (changed) chunk.markDirty();
		return changed;
	}

	private static int findOpenGround(Chunk chunk, BlockPos.MutableBlockPos cursor,
			int x, int z, int localX, int localZ, int minY) {
		// Chunk#getHeightValue is the first free Y in 1.12. Subtract exactly once.
		int y = chunk.getHeightValue(localX, localZ) - 1;
		while (y >= minY && open(chunk.getBlockState(cursor.setPos(x, y, z)))) y--;
		return y;
	}

	private static long findCeilingAndGround(Chunk chunk, BlockPos.MutableBlockPos cursor,
			int x, int z, int maxY, int minY) {
		int y = maxY - 1;
		while (y >= minY && open(chunk.getBlockState(cursor.setPos(x, y, z)))) y--;
		if (y < minY) return pack(-1, -1);
		while (y >= minY && !open(chunk.getBlockState(cursor.setPos(x, y, z)))) y--;
		int ceilingY = y + 1;
		while (y >= minY && open(chunk.getBlockState(cursor.setPos(x, y, z)))) y--;
		return pack(ceilingY, y);
	}

	private static boolean applyGround(Chunk chunk, World world, BakedBiomeWorldgen config,
			BlockPos.MutableBlockPos cursor, int x, int z, int y, int minY) {
		cursor.setPos(x, y, z);
		Surface surface = config.surfaces.get(world.getBiome(cursor));
		if (surface == null) return false;
		IBlockState source = chunk.getBlockState(cursor);
		if (!replaceable(world, cursor, source)) return false;
		cursor.setPos(x, y + 1, z);
		boolean underwater = liquid(chunk.getBlockState(cursor));
		IBlockState top = underwater && surface.underwater != null ? surface.underwater : surface.top;
		int depth = surface.filler == null ? 0 : surface.fillerDepth;
		for (int offset = 1; offset <= depth && y - offset >= minY; offset++) {
			cursor.setPos(x, y - offset, z);
			if (!replaceable(world, cursor, chunk.getBlockState(cursor))) return false;
		}
		boolean changed = false;
		if (top != null) {
			cursor.setPos(x, y, z);
			changed |= set(chunk, cursor, top);
		}
		for (int offset = 1; offset <= depth && y - offset >= minY; offset++) {
			cursor.setPos(x, y - offset, z);
			changed |= set(chunk, cursor, surface.filler);
		}
		return changed;
	}

	private static boolean applyCeiling(Chunk chunk, World world, BakedBiomeWorldgen config,
			BlockPos.MutableBlockPos cursor, int x, int z, int ceilingY) {
		cursor.setPos(x, ceilingY, z);
		IBlockState source = chunk.getBlockState(cursor);
		if (!replaceable(world, cursor, source)) return false;
		Surface surface = config.surfaces.get(world.getBiome(cursor));
		return surface != null && surface.ceiling != null && set(chunk, cursor, surface.ceiling);
	}

	private static boolean set(Chunk chunk, BlockPos pos, IBlockState state) {
		IBlockState old = chunk.getBlockState(pos);
		if (old.equals(state)) return false;
		chunk.setBlockState(pos, state);
		return true;
	}

	private static boolean open(IBlockState state) {
		Material material = state.getMaterial();
		return material == Material.AIR || material == Material.PLANTS
				|| material == Material.VINE || material == Material.SNOW || liquid(state);
	}

	private static boolean liquid(IBlockState state) {
		return state.getMaterial().isLiquid() || state.getBlock() instanceof IFluidBlock;
	}

	private static boolean replaceable(World world, BlockPos pos, IBlockState state) {
		if (open(state) || world.getTileEntity(pos) != null || state.getBlock().hasTileEntity(state)) return false;
		Block block = state.getBlock();
		if (block == Blocks.GRASS || block == Blocks.DIRT || block == Blocks.STONE
				|| block == Blocks.SAND || block == Blocks.GRAVEL || block == Blocks.NETHERRACK
				|| block == Blocks.END_STONE) return true;
		BakedTerrainDimension terrain = GeomeConfig.terrainDimension(WorldIds.dimension(world));
		return terrain != null && terrain.isReplaceable(state);
	}

	private static long pack(int high, int low) {
		return ((long) high << 32) | (low & 0xFFFFFFFFL);
	}
}
