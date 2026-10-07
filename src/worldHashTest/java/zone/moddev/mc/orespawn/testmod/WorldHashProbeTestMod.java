package zone.moddev.mc.orespawn.testmod;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fmlserverevents.FMLServerStartedEvent;

/** Test-only checksum of completed chunks; it works with both 4.0.16 and 4.1. */
@Mod("worldhashprobe")
public final class WorldHashProbeTestMod {
	public WorldHashProbeTestMod() {
		MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::audit);
	}

	private void audit(FMLServerStartedEvent event) {
		ServerLevel world = event.getServer().getLevel(Level.OVERWORLD);
		if (world == null) throw new IllegalStateException("Overworld is unavailable");
		// Adjacent veins can write into the outer benchmark row. Finish the
		// surrounding chunks before comparing the completed 81-chunk square.
		for (int x = 251; x <= 261; x++) {
			world.getChunk(x, 251);
			world.getChunk(x, 261);
		}
		for (int z = 252; z <= 260; z++) {
			world.getChunk(251, z);
			world.getChunk(261, z);
		}
		MessageDigest blocks = digest();
		MessageDigest biomes = digest();
		MessageDigest managedBlocks = digest();
		MessageDigest managedOres = digest();
		MessageDigest[] levels = new MessageDigest[256];
		for (int y = 0; y < levels.length; y++) levels[y] = digest();
		Properties marker = new Properties();
		Map<Block, Long> blockCounts = new HashMap<>();
		Map<Block, Boolean> managed = new HashMap<>();
		Map<Block, Boolean> ores = new HashMap<>();
		long managedCount = 0;
		long oreCount = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int chunks = 0;
		for (int chunkZ = 252; chunkZ <= 260; chunkZ++) {
			for (int chunkX = 252; chunkX <= 260; chunkX++) {
				if (!world.hasChunk(chunkX, chunkZ)) {
					throw new IllegalStateException("Benchmark did not finish chunk " + chunkX + "," + chunkZ);
				}
				LevelChunk chunk = world.getChunk(chunkX, chunkZ);
				MessageDigest chunkBlocks = digest();
				chunks++;
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						int absoluteX = (chunkX << 4) + x;
						int absoluteZ = (chunkZ << 4) + z;
						ResourceLocation biome = world.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY)
								.getKey(world.getBiome(
								cursor.set(absoluteX, 64, absoluteZ)));
						if (biome == null) throw new IllegalStateException("Unregistered biome in benchmark");
						biomes.update(biome.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
						biomes.update((byte) 0);
						for (int y = 0; y < 256; y++) {
							cursor.set(absoluteX, y, absoluteZ);
							BlockState blockState = chunk.getBlockState(cursor);
							blockCounts.merge(blockState.getBlock(), 1L, Long::sum);
							int state = Block.getId(blockState);
							if (managed.computeIfAbsent(blockState.getBlock(), block -> {
								ResourceLocation id = Registry.BLOCK.getKey(block);
								return id != null && ("mineralogy".equals(id.getNamespace())
										|| "orespawn".equals(id.getNamespace()));
							})) {
								updateInt(managedBlocks, absoluteX);
								updateInt(managedBlocks, y);
								updateInt(managedBlocks, absoluteZ);
								updateInt(managedBlocks, state);
								managedCount++;
							}
							if (ores.computeIfAbsent(blockState.getBlock(), block -> {
								ResourceLocation id = Registry.BLOCK.getKey(block);
								return id != null && "mineralogy".equals(id.getNamespace())
										&& id.getPath().endsWith("_ore");
							})) {
								updateInt(managedOres, absoluteX);
								updateInt(managedOres, y);
								updateInt(managedOres, absoluteZ);
								updateInt(managedOres, state);
								oreCount++;
							}
							updateInt(blocks, state);
							updateInt(chunkBlocks, state);
							updateInt(levels[y], state);
						}
					}
				}
				marker.setProperty("chunk." + chunkX + "." + chunkZ, hex(chunkBlocks.digest()));
			}
		}
		for (int y = 0; y < levels.length; y++)
			marker.setProperty("level." + y, hex(levels[y].digest()));
		marker.setProperty("chunks", Integer.toString(chunks));
		marker.setProperty("blocks_sha256", hex(blocks.digest()));
		marker.setProperty("biomes_sha256", hex(biomes.digest()));
		marker.setProperty("managed_blocks_sha256", hex(managedBlocks.digest()));
		marker.setProperty("managed_block_count", Long.toString(managedCount));
		marker.setProperty("managed_ores_sha256", hex(managedOres.digest()));
		marker.setProperty("managed_ore_count", Long.toString(oreCount));
		for (Map.Entry<Block, Long> entry : blockCounts.entrySet()) {
			ResourceLocation id = Registry.BLOCK.getKey(entry.getKey());
			if (id != null) marker.setProperty("count." + id, Long.toString(entry.getValue()));
		}
		Path worldRoot = event.getServer().getWorldPath(LevelResource.ROOT).normalize();
		try (OutputStream output = Files.newOutputStream(worldRoot.resolve("worldhashprobe.properties"))) {
			marker.store(output, "Completed benchmark chunks");
		} catch (IOException failure) {
			throw new IllegalStateException("Could not save benchmark hashes", failure);
		}
	}

	private static MessageDigest digest() {
		try { return MessageDigest.getInstance("SHA-256"); }
		catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
	}

	private static void updateInt(MessageDigest digest, int value) {
		digest.update((byte) (value >>> 24));
		digest.update((byte) (value >>> 16));
		digest.update((byte) (value >>> 8));
		digest.update((byte) value);
	}

	private static String hex(byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			result.append(Character.forDigit((value >>> 4) & 15, 16));
			result.append(Character.forDigit(value & 15, 16));
		}
		return result.toString();
	}
}
