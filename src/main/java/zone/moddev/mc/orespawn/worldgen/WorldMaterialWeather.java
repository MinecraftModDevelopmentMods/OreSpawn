package zone.moddev.mc.orespawn.worldgen;

import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.DimensionMaterials;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.ChunkEvent;

/**
 * Converts vanilla weather products in loaded columns to configured materials.
 * Aquifer fluids are handled directly by the chunk generator.
 */
public final class WorldMaterialWeather {
	private WorldMaterialWeather() {
	}

	public static void onChunkLoad(ChunkEvent.Load event) {
		if (!(event.getWorld() instanceof WorldServer)) return;
		WorldServer level = (WorldServer) event.getWorld();
		BakedBiomeWorldgen config = BiomeWorldgenManager.get(WorldIds.dimension(level));
		if (config == null || config.materials == null) return;
		convertChunk(event.getChunk(), config.materials);
	}

	public static void onWorldTick(TickEvent.WorldTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)
				|| event.world.getGameTime() % 20L != 0L) return;
		WorldServer level = (WorldServer) event.world;
		BakedBiomeWorldgen config = BiomeWorldgenManager.get(WorldIds.dimension(level));
		if (config == null || config.materials == null) return;
		DimensionMaterials materials = config.materials;
		if (materials.snow == null && materials.ice == null) return;

		for (EntityPlayerMP player : level.getPlayers(EntityPlayerMP.class, ignored -> true)) {
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
				for (int offset = 0; offset <= 2; offset++) {
					cursor.setPos(minX + localX, top - offset, minZ + localZ);
					IBlockState state = chunk.getBlockState(cursor);
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
