package zone.moddev.mc.orespawn.worldgen;

import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.DimensionMaterials;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.ChunkEvent;

/**
 * Converts vanilla weather products in loaded columns to configured materials.
 * Aquifer fluids are handled directly by the chunk generator.
 */
public final class WorldMaterialWeather {
	private WorldMaterialWeather() {
	}

	public static void onChunkLoad(ChunkEvent.Load event) {
		if (!(event.getWorld() instanceof ServerLevel)) return;
		ServerLevel level = (ServerLevel) event.getWorld();
		BakedBiomeWorldgen config = BiomeWorldgenManager.get(level.dimension());
		if (config == null || config.materials == null) return;
		convertChunk(event.getChunk(), config.materials);
	}

	public static void onWorldTick(TickEvent.WorldTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !(event.world instanceof ServerLevel)
				|| event.world.getGameTime() % 20L != 0L) return;
		ServerLevel level = (ServerLevel) event.world;
		BakedBiomeWorldgen config = BiomeWorldgenManager.get(level.dimension());
		if (config == null || config.materials == null) return;
		DimensionMaterials materials = config.materials;
		if (materials.snow == null && materials.ice == null) return;

		for (ServerPlayer player : level.players()) {
			ChunkPos pos = new ChunkPos(player.blockPosition());
			if (level.hasChunk(pos.x, pos.z)) {
				convertChunk(level.getChunk(pos.x, pos.z), materials);
			}
		}
	}

	private static void convertChunk(ChunkAccess chunk, DimensionMaterials materials) {
		if (materials.snow == null && materials.ice == null) return;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, localX, localZ);
				// A one-layer Snow block is non-motion-blocking and therefore occupies
				// the first free cell immediately above this heightmap's surface.
				if (materials.snow != null && top + 1 < chunk.getMaxBuildHeight()) {
					cursor.set(minX + localX, top + 1, minZ + localZ);
					if (chunk.getBlockState(cursor).is(Blocks.SNOW)) {
						chunk.setBlockState(cursor, materials.snow, false);
					}
				}
				for (int offset = 0; offset <= 2; offset++) {
					cursor.set(minX + localX, top - offset, minZ + localZ);
					BlockState state = chunk.getBlockState(cursor);
					if (materials.snow != null && state.is(Blocks.SNOW)) {
						chunk.setBlockState(cursor, materials.snow, false);
					} else if (materials.ice != null && state.is(Blocks.ICE)) {
						chunk.setBlockState(cursor, materials.ice, false);
					}
				}
			}
		}
	}
}
