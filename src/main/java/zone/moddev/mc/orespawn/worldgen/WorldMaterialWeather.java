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
 * Converts vanilla weather products in loaded columns to configured materials.
 * Aquifer fluids are handled directly by the chunk generator.
 */
public final class WorldMaterialWeather {
	private WorldMaterialWeather() {
	}

	public static void onChunkLoad(ChunkEvent.Load event) {
		if (!(event.getWorld() instanceof ServerWorld)) return;
		ServerWorld level = (ServerWorld) event.getWorld();
		BakedBiomeWorldgen config = BiomeWorldgenManager.get(level.dimension());
		if (config == null || config.materials == null) return;
		convertChunk(event.getChunk(), config.materials);
	}

	public static void onWorldTick(TickEvent.WorldTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !(event.world instanceof ServerWorld)
				|| event.world.getGameTime() % 20L != 0L) return;
		ServerWorld level = (ServerWorld) event.world;
		BakedBiomeWorldgen config = BiomeWorldgenManager.get(level.dimension());
		if (config == null || config.materials == null) return;
		DimensionMaterials materials = config.materials;
		if (materials.snow == null && materials.ice == null) return;

		for (ServerPlayerEntity player : level.players()) {
			ChunkPos pos = new ChunkPos(player.blockPosition());
			if (level.hasChunk(pos.x, pos.z)) {
				convertChunk(level.getChunk(pos.x, pos.z), materials);
			}
		}
	}

	private static void convertChunk(IChunk chunk, DimensionMaterials materials) {
		if (materials.snow == null && materials.ice == null) return;
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		for (int localX = 0; localX < 16; localX++) {
			for (int localZ = 0; localZ < 16; localZ++) {
				int top = chunk.getHeight(Heightmap.Type.MOTION_BLOCKING, localX, localZ);
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
