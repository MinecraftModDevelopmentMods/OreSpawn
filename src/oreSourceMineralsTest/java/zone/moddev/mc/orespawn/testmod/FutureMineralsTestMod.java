package zone.moddev.mc.orespawn.testmod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.google.gson.JsonObject;

import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.storage.FolderName;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.orespawn.api.WorldgenProvider;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

/** Test-only stand-in for a future Base Minerals provider. */
@Mod(FutureMineralsTestMod.MODID)
public final class FutureMineralsTestMod {
	static final String MODID = "baseminerals";
	private static final String PHASE = "oresourceprobe.phase";

	public FutureMineralsTestMod() {
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueue);
		MinecraftForge.EVENT_BUS.addListener(this::audit);
	}

	private void enqueue(InterModEnqueueEvent event) {
		WorldgenProvider.Builder provider = WorldgenProvider.builder(MODID, 1);
		provider.ore(id("baseminerals:sulfur"), id("minecraft:blue_wool"), ore -> ore
				.material(id("orespawn:sulfur"))
				.dimension(id("minecraft:overworld"), rule -> rule
						.yRange(20, 42).attempts(20.0D).quantity(7)
						.hostBlock(id("minecraft:stone"))));
		if (!OreSpawnApi.enqueue(provider.build())) {
			throw new IllegalStateException("Base Minerals fixture was not sent to OreSpawn");
		}
	}

	private void audit(FMLServerStartedEvent event) {
		String phase = System.getProperty(PHASE, "");
		if (!"fresh".equals(phase) && !"reload".equals(phase)
				&& !"missing".equals(phase) && !"restored".equals(phase)) return;
		boolean both = ModList.get().isLoaded("electricadvantage");
		boolean sharedGroup = both || "missing".equals(phase);
		MinecraftServer server = event.getServer();
		JsonObject profile = WorldGeologyProfileManager.activeProfile().rootCopy();
		String key = "orespawn:sulfur|minecraft:overworld";
		JsonObject policy = profile.getAsJsonObject("ore_source_policies").getAsJsonObject(key);
		if (policy == null) throw new IllegalStateException("Sulfur policy is missing");
		String mode = policy.get("mode").getAsString();
		if (!mode.equals(sharedGroup ? "consolidated" : "keep_separate")) {
			throw new IllegalStateException("Unexpected Sulfur policy with " + (both ? "both" : "one")
					+ " providers: " + mode);
		}
		if (sharedGroup) {
			if (!"balanced".equals(policy.get("output_mode").getAsString())
					|| policy.getAsJsonObject("outputs").size() != 3
					|| policy.getAsJsonObject("placement_sources").size() != 1
					|| !"baseminerals:sulfur".equals(policy.getAsJsonObject("placement_sources")
							.get("orespawn:standard").getAsString())) {
				throw new IllegalStateException("Balanced Sulfur policy lacks the three outputs and one budget: "
						+ policy);
			}
		}
		ServerWorld world = server.getLevel(World.OVERWORLD);
		if (world == null) throw new IllegalStateException("Overworld is unavailable");
		int blue = 0, yellow = 0, green = 0;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		int spawnChunkX = world.getSharedSpawnPos().getX() >> 4;
		int spawnChunkZ = world.getSharedSpawnPos().getZ() >> 4;
		for (int chunkX = spawnChunkX - 1; chunkX <= spawnChunkX + 1; chunkX++) {
			for (int chunkZ = spawnChunkZ - 1; chunkZ <= spawnChunkZ + 1; chunkZ++) {
				if ("fresh".equals(phase) && !world.hasChunk(chunkX, chunkZ)) {
					throw new IllegalStateException("Spawn chunk was not generated before the audit");
				}
				world.getChunk(chunkX, chunkZ);
				for (int x = chunkX * 16; x < (chunkX + 1) * 16; x++) {
					for (int z = chunkZ * 16; z < (chunkZ + 1) * 16; z++) {
						for (int y = 20; y <= 42; y++) {
							pos.set(x, y, z);
							if (world.getBlockState(pos).getBlock() == Blocks.BLUE_WOOL) blue++;
							if (world.getBlockState(pos).getBlock() == Blocks.YELLOW_WOOL) yellow++;
							if (world.getBlockState(pos).getBlock() == Blocks.GREEN_WOOL) green++;
						}
					}
				}
			}
		}
		if (blue == 0 || (sharedGroup && (yellow == 0 || green == 0))
				|| (!sharedGroup && (yellow != 0 || green != 0))) {
			throw new IllegalStateException("Wrong ore outputs in generated chunks: blue=" + blue
					+ " yellow=" + yellow + " green=" + green);
		}
		Path marker = server.getWorldPath(FolderName.ROOT).normalize().resolve("oresourceprobe.properties");
		Properties values = new Properties();
		values.setProperty("mode", mode);
		values.setProperty("blue", Integer.toString(blue));
		values.setProperty("yellow", Integer.toString(yellow));
		values.setProperty("green", Integer.toString(green));
		if (!"fresh".equals(phase)) {
			Properties previous = new Properties();
			try (InputStream input = Files.newInputStream(marker)) { previous.load(input); }
			catch (IOException failure) { throw new IllegalStateException("Missing fresh ore audit", failure); }
			for (String name : values.stringPropertyNames()) {
				if (!values.getProperty(name).equals(previous.getProperty(name))) {
					throw new IllegalStateException("Reload changed " + name + ": " + previous + " -> " + values);
				}
			}
			values.setProperty(phase + "_verified", "true");
		}
		try (OutputStream output = Files.newOutputStream(marker)) { values.store(output, "Ore source fixture"); }
		catch (IOException failure) { throw new IllegalStateException("Could not save ore audit", failure); }
		server.halt(false);
	}

	private static ResourceLocation id(String value) { return new ResourceLocation(value); }
}
