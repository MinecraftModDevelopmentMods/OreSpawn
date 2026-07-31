package zone.moddev.mc.orespawn.worldgen;

import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Surface;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Applies explicit provider surface blocks after the source surface is built. */
public final class BiomeSurfaceFeature extends Feature<NoneFeatureConfiguration> {
	public static final BiomeSurfaceFeature FEATURE = new BiomeSurfaceFeature();
	private static Holder<PlacedFeature> placedFeature;

	private BiomeSurfaceFeature() {
		super(NoneFeatureConfiguration.CODEC);
	}

	public static void registerConfiguredFeature() {
		placedFeature = WorldgenFeatureHolders.direct(FEATURE);
	}

	static Holder<PlacedFeature> placedFeature() {
		return placedFeature;
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel world = context.level();
		BakedBiomeWorldgen config = BiomeWorldgenManager.get(world.getLevel().dimension());
		if (config == null || !config.hasSurfaces()) return false;
		ChunkAccess chunk = world.getChunk(context.origin());
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean changed = false;
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				int x = minX + localX;
				int z = minZ + localZ;
				int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, localX, localZ) - 1;
				while (y > world.getMinY()) {
					cursor.set(x, y, z);
					BlockState state = chunk.getBlockState(cursor);
					if (!state.isAir() && state.getFluidState().isEmpty()) break;
					y--;
				}
				if (y <= world.getMinY()) continue;
				cursor.set(x, y, z);
				Surface surface = config.surfaces.get(world.getBiome(cursor));
				if (surface == null) continue;
				boolean underwater = !chunk.getBlockState(cursor.above()).getFluidState().isEmpty();
				BlockState top = underwater && surface.underwater != null
						? surface.underwater : surface.top;
				if (top != null && replaceable(chunk.getBlockState(cursor))) {
					chunk.setBlockState(cursor, top, 0);
					changed = true;
				}
				if (surface.filler != null) {
					for (int depth = 1; depth <= surface.fillerDepth
							&& y - depth >= world.getMinY(); depth++) {
						cursor.set(x, y - depth, z);
						if (!replaceable(chunk.getBlockState(cursor))) break;
						chunk.setBlockState(cursor, surface.filler, 0);
						changed = true;
					}
				}
				if (surface.ceiling != null) {
					changed |= applyCeiling(chunk, cursor, x, z, world.getMaxY(),
							world.getMinY(), surface.ceiling);
				}
			}
		}
		return changed;
	}

	private static boolean applyCeiling(ChunkAccess chunk, BlockPos.MutableBlockPos cursor,
			int x, int z, int maxY, int minY, BlockState ceiling) {
		for (int y = maxY - 1; y >= minY; y--) {
			cursor.set(x, y, z);
			BlockState state = chunk.getBlockState(cursor);
			if (state.isAir() || !state.getFluidState().isEmpty()) continue;
			if (!replaceable(state)) return false;
			chunk.setBlockState(cursor, ceiling, 0);
			return true;
		}
		return false;
	}

	private static boolean replaceable(BlockState state) {
		return !state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity();
	}
}
