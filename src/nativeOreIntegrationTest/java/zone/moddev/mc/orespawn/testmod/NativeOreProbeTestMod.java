package zone.moddev.mc.orespawn.testmod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import zone.moddev.mc.orespawn.worldgen.GeomeConfig;
import zone.moddev.mc.orespawn.worldgen.NativeOreProbeBridge;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

/** Exercises managed quartz in real Nether terrain, including tag and explicit block hosts. */
@Mod("nativeoreprobe")
public final class NativeOreProbeTestMod {
	private static final String QUARTZ = "minecraft:nether_quartz_ore";
	private static final String NETHER = "minecraft:the_nether";

	public NativeOreProbeTestMod() {
		MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, this::prepare);
		MinecraftForge.EVENT_BUS.addListener(this::audit);
		MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::checkShutdown);
	}

	private void checkShutdown(FMLServerStoppedEvent event) {
		if (NativeOreProbeBridge.tagsPending()) {
			throw new IllegalStateException("Tag retry state survived server shutdown");
		}
		System.out.println("NATIVE_ORE_PROBE shutdown_verified=true");
	}

	private void prepare(FMLServerAboutToStartEvent event) {
		Path profile = profilePath(event.getServer());
		String phase = System.getProperty("nativeoreprobe.phase", "fresh");
		if ("reload".equals(phase)) return;
		try {
			JsonObject root;
			if ("edited".equals(phase)) {
				root = new JsonParser().parse(new String(Files.readAllBytes(profile),
						StandardCharsets.UTF_8)).getAsJsonObject();
			} else {
				root = GeomeConfig.globalBaseProfile().rootCopy();
				Method defaults = GeomeConfig.class.getDeclaredMethod("defaultOreConfig");
				defaults.setAccessible(true);
				JsonObject ores = (JsonObject) defaults.invoke(null);
				JsonObject onlyQuartz = new JsonObject();
				onlyQuartz.add(QUARTZ, ores.get(QUARTZ));
				root.add("ores", onlyQuartz);
				root.addProperty("manage_vanilla_ores", true);
				root.addProperty("place_fluid_deposits", false);
				root.add("terrain_dimensions", new JsonObject());
				root.add("ore_source_policies", new JsonObject());
				JsonObject retrogen = new JsonObject();
				retrogen.addProperty("enabled", false);
				retrogen.addProperty("force", false);
				retrogen.addProperty("revision", 0);
				root.add("retrogen", retrogen);
			}
			JsonObject rule = root.getAsJsonObject("ores").getAsJsonObject(QUARTZ)
					.getAsJsonObject("dimensions").getAsJsonObject(NETHER);
			rule.addProperty("min_y", "edited".equals(phase) ? 100 : 24);
			rule.addProperty("max_y", "edited".equals(phase) ? 104 : 28);
			rule.addProperty("frequency", 20);
			rule.addProperty("quantity", 14);
			if ("explicit".equals(System.getProperty("nativeoreprobe.host"))) {
				rule.remove("host_tags");
				JsonArray blocks = new JsonArray();
				blocks.add("minecraft:netherrack");
				rule.add("host_blocks", blocks);
			}
			Files.createDirectories(profile.getParent());
			Files.write(profile, new GsonBuilder().setPrettyPrinting().create().toJson(root)
					.getBytes(StandardCharsets.UTF_8));
		} catch (IOException | ReflectiveOperationException failure) {
			throw new IllegalStateException("Could not prepare disposable quartz profile", failure);
		}
	}

	private void audit(FMLServerStartedEvent event) {
		MinecraftServer server = event.getServer();
		String phase = System.getProperty("nativeoreprobe.phase", "fresh");
		if (!NativeOreProbeBridge.controlsQuartz()) {
			throw new IllegalStateException("Quartz is not controlled by OreSpawn before generation");
		}
		if (NativeOreProbeBridge.tagsPending()) {
			throw new IllegalStateException("Tag-only host retry was not consumed before generation");
		}
		if (WorldGeologyProfileManager.activeProfile().oreRetrogenEnabled()) {
			throw new IllegalStateException("The quartz test must not enable retrogen");
		}
		ServerWorld world = server.getWorld(DimensionType.THE_NETHER);
		if (world == null) throw new IllegalStateException("Nether is unavailable");
		Path marker = worldRoot(server).resolve("nativeoreprobe.properties");
		Properties previous = new Properties();
		try {
			if (!"fresh".equals(phase)) {
				try (InputStream input = Files.newInputStream(marker)) { previous.load(input); }
			}
			Properties current = inspect(world, -8, 24, 28);
			current.setProperty("profile_sha256", hex(digest().digest(Files.readAllBytes(profilePath(server)))));
			if (!"fresh".equals(phase)) {
				for (String name : current.stringPropertyNames()) {
					if ("edited".equals(phase) && "profile_sha256".equals(name)) continue;
					if (!current.getProperty(name).equals(previous.getProperty(name))) {
						throw new IllegalStateException("Reload changed " + name + ": " + previous + " -> " + current);
					}
				}
			}
			if ("edited".equals(phase)) {
				Properties newTerrain = inspect(world, -24, 100, 104);
				current.setProperty("new_terrain_quartz", newTerrain.getProperty("quartz"));
				current.setProperty("old_profile_sha256", previous.getProperty("profile_sha256"));
			}
			current.setProperty(phase + "_verified", "true");
			try (OutputStream output = Files.newOutputStream(marker)) { current.store(output, "Managed Nether quartz"); }
			System.out.println("NATIVE_ORE_PROBE " + phase + " " + current);
		} catch (IOException failure) {
			throw new IllegalStateException("Could not save quartz audit", failure);
		}
		server.initiateShutdown(false);
	}

	private static Properties inspect(ServerWorld world, int origin, int minY, int maxY) {
		// Finish the surrounding row before hashing veins that cross chunk edges.
		for (int z = origin - 1; z <= origin + 3; z++) {
			for (int x = origin - 1; x <= origin + 3; x++) world.getChunk(x, z);
		}
		MessageDigest blocks = digest();
		MessageDigest biomes = digest();
		BlockPos.Mutable cursor = new BlockPos.Mutable();
		int quartz = 0;
		for (int z = origin; z <= origin + 2; z++) {
			for (int x = origin; x <= origin + 2; x++) {
				Chunk chunk = world.getChunk(x, z);
				for (int dz = 0; dz < 16; dz++) {
					for (int dx = 0; dx < 16; dx++) {
						cursor.setPos((x << 4) + dx, 64, (z << 4) + dz);
						biomes.update(world.getBiome(cursor).getRegistryName().toString().getBytes(StandardCharsets.UTF_8));
						biomes.update((byte) 0);
						for (int y = 0; y < 256; y++) {
							BlockState state = chunk.getBlockState(cursor.setPos((x << 4) + dx, y, (z << 4) + dz));
							int id = Block.getStateId(state);
							blocks.update((byte) (id >>> 24));
							blocks.update((byte) (id >>> 16));
							blocks.update((byte) (id >>> 8));
							blocks.update((byte) id);
							if (state.getBlock() == Blocks.NETHER_QUARTZ_ORE) {
								if (y < minY || y > maxY) throw new IllegalStateException("Quartz outside managed range at " + cursor);
								quartz++;
							}
						}
					}
				}
			}
		}
		if (quartz == 0) throw new IllegalStateException("Managed quartz did not generate");
		Properties result = new Properties();
		result.setProperty("quartz", Integer.toString(quartz));
		result.setProperty("blocks_sha256", hex(blocks.digest()));
		result.setProperty("biomes_sha256", hex(biomes.digest()));
		return result;
	}

	private static Path worldRoot(MinecraftServer server) {
		return server.getActiveAnvilConverter().getFile(server.getFolderName(), "level.dat")
				.toPath().toAbsolutePath().normalize().getParent();
	}

	private static Path profilePath(MinecraftServer server) {
		return worldRoot(server).resolve("serverconfig/orespawn-worldgen.json");
	}

	private static MessageDigest digest() {
		try { return MessageDigest.getInstance("SHA-256"); }
		catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
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
