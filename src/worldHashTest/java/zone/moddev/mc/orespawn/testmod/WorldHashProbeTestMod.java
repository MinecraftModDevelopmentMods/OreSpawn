package zone.moddev.mc.orespawn.testmod;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

import net.minecraft.block.Block;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.World;
import net.minecraft.world.storage.FolderName;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;

/** Test-only checksum of completed chunks; it works with both 4.0.16 and 4.1. */
@Mod("worldhashprobe")
public final class WorldHashProbeTestMod {
	public WorldHashProbeTestMod() {
		MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::audit);
	}

	private void audit(FMLServerStartedEvent event) {
		ServerWorld world = event.getServer().getLevel(World.OVERWORLD);
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
		MessageDigest[] levels = new MessageDigest[256];
		for (int y = 0; y < levels.length; y++) levels[y] = digest();
		Properties marker = new Properties();
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		int chunks = 0;
		for (int chunkZ = 252; chunkZ <= 260; chunkZ++) {
			for (int chunkX = 252; chunkX <= 260; chunkX++) {
				if (!world.hasChunk(chunkX, chunkZ)) {
					throw new IllegalStateException("Benchmark did not finish chunk " + chunkX + "," + chunkZ);
				}
				Chunk chunk = world.getChunk(chunkX, chunkZ);
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
							int state = Block.getId(chunk.getBlockState(cursor));
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
		Path worldRoot = event.getServer().getWorldPath(FolderName.ROOT).normalize();
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
