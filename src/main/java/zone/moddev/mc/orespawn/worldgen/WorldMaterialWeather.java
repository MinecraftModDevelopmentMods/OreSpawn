package zone.moddev.mc.orespawn.worldgen;

import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.DimensionMaterials;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.ChunkEvent;

/**
 * Replaces vanilla snow and ice with the configured materials in loaded columns.
 * The chunk generator handles aquifer fluids separately.
 */
public final class WorldMaterialWeather {
	private WorldMaterialWeather() {
	}

	public static void onChunkLoad(ChunkEvent.Load event) {
		if (!(event.getWorld() instanceof ServerWorld)) return;
		ServerWorld level = (ServerWorld) event.getWorld();
		BakedBiomeWorldgen config = BiomeWorldgenManager.get(WorldIds.dimension(level));
		if (config == null || config.materials == null) return;
		convertChunk(event.getChunk(), config.materials);
	}

	public static void onWorldTick(TickEvent.WorldTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !(event.world instanceof ServerWorld)
				|| event.world.getGameTime() % 20L != 0L) return;
		ServerWorld level = (ServerWorld) event.world;
		BakedBiomeWorldgen config = BiomeWorldgenManager.get(WorldIds.dimension(level));
		if (config == null || config.materials == null) return;
		DimensionMaterials materials = config.materials;
		if (materials.snow == null && materials.ice == null) return;

		for (ServerPlayerEntity player : level.getPlayers()) {
			ChunkPos pos = new ChunkPos(player.getPosition());
			if (level.getChunkProvider().chunkExists(pos.x, pos.z)) {
				convertChunk(level.getChunk(pos.x, pos.z), materials);
			}
		}
	}

	private static void convertChunk(IChunk chunk, DimensionMaterials materials) {
		if (materials.snow == null && materials.ice == null) return;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int minX = chunk.getPos().getXStart();
		int minZ = chunk.getPos().getZStart();
		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				int top = chunk.getTopBlockY(Heightmap.Type.MOTION_BLOCKING, localX, localZ);
				// A single snow layer does not raise this heightmap.
				// Look in the first free block above the surface.
				if (materials.snow != null && top + 1 < 256) {
					cursor.setPos(minX + localX, top + 1, minZ + localZ);
					if (chunk.getBlockState(cursor).getBlock() == Blocks.SNOW) {
						chunk.setBlockState(cursor, materials.snow, false);
					}
				}
				for (int offset = 0; offset <= 2; offset++) {
					cursor.setPos(minX + localX, top - offset, minZ + localZ);
					BlockState state = chunk.getBlockState(cursor);
					if (materials.snow != null && state.getBlock() == Blocks.SNOW) {
						chunk.setBlockState(cursor, materials.snow, false);
					} else if (materials.ice != null && state.getBlock() == Blocks.ICE) {
						chunk.setBlockState(cursor, materials.ice, false);
					}
				}
			}
		}
	}
}
