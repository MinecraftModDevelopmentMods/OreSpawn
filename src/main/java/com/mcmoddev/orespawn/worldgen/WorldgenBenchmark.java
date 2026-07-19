package com.mcmoddev.orespawn.worldgen;

import java.util.Arrays;
import java.util.Locale;

import com.mcmoddev.orespawn.OreSpawnConfig.GeologyMode;
import com.mcmoddev.orespawn.worldgen.FormationSettings.Preset;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Opt-in integrated benchmark used to keep worldgen overhead measurable. */
public final class WorldgenBenchmark {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String PROPERTY = "orespawn.worldgenBenchmarkMode";
	private static final String MODE = System.getProperty(PROPERTY, "").trim().toLowerCase(Locale.ROOT);
	private static final boolean ENABLED = "vanilla".equals(MODE) || "cyano".equals(MODE) || "sky".equals(MODE);

	private WorldgenBenchmark() {
		throw new IllegalAccessError("Not an instantiable class");
	}

	public static void register() {
		if (!ENABLED) {
			return;
		}
		MinecraftForge.EVENT_BUS.addListener(WorldgenBenchmark::onServerAboutToStart);
		MinecraftForge.EVENT_BUS.addListener(WorldgenBenchmark::onServerStarted);
	}

	public static boolean isVanillaBaseline() {
		return ENABLED && "vanilla".equals(MODE);
	}

	private static void onServerAboutToStart(ServerAboutToStartEvent event) {
		if (isVanillaBaseline()) {
			return;
		}

		WorldGeologyProfile source = WorldGeologyProfileManager.activeProfile();
		GeologyMode geologyMode = "cyano".equals(MODE) ? GeologyMode.LEGACY : GeologyMode.GEOME;
		WorldGeologyProfile benchmarkProfile = source.withSelection(geologyMode,
				Preset.AVERAGE, Preset.AVERAGE, Preset.AVERAGE, Preset.AVERAGE, Preset.AVERAGE,
				source.placeCrudeOil());
		if (Boolean.getBoolean("orespawn.worldgenBenchmarkVanillaOres")) {
			com.google.gson.JsonObject root = benchmarkProfile.rootCopy();
			root.addProperty("manage_vanilla_ores", true);
			benchmarkProfile = benchmarkProfile.withRoot(root);
		}
		WorldGeologyProfileManager.applyBenchmarkProfile(benchmarkProfile);
	}

	private static void onServerStarted(ServerStartedEvent event) {
		ServerLevel level = event.getServer().overworld();
		int radius = boundedInteger("orespawn.worldgenBenchmarkRadius", 4, 1, 16);
		int repetitions = boundedInteger("orespawn.worldgenBenchmarkRepetitions", 3, 1, 9);
		int warmupRadius = Math.min(2, radius);
		int chunks = squareDiameter(radius);

		LOGGER.info("ORESPAWN_BENCHMARK start mode={} seed={} target_chunks={} repetitions={} "
				+ "java={} processors={} max_heap_mb={}",
				MODE, level.getSeed(), chunks, repetitions, System.getProperty("java.version"),
				Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / (1024L * 1024L));

		generateSquare(level, 192, 192, warmupRadius);
		double[] milliseconds = new double[repetitions];
		for (int repetition = 0; repetition < repetitions; repetition++) {
			int centerX = 256 + (repetition * 64);
			long started = System.nanoTime();
			generateSquare(level, centerX, 256, radius);
			milliseconds[repetition] = (System.nanoTime() - started) / 1_000_000.0D;
			LOGGER.info("ORESPAWN_BENCHMARK result mode={} repetition={} chunks={} elapsed_ms={} ms_per_chunk={}",
					MODE, repetition + 1, chunks, format(milliseconds[repetition]),
					format(milliseconds[repetition] / chunks));
		}

		double[] sorted = milliseconds.clone();
		Arrays.sort(sorted);
		double median = sorted[sorted.length / 2];
		LOGGER.info("ORESPAWN_BENCHMARK summary mode={} chunks_per_repetition={} repetitions={} "
				+ "median_ms={} median_ms_per_chunk={} min_ms={} max_ms={}",
				MODE, chunks, repetitions, format(median), format(median / chunks),
				format(sorted[0]), format(sorted[sorted.length - 1]));
	}

	private static void generateSquare(ServerLevel level, int centerX, int centerZ, int radius) {
		for (int z = centerZ - radius; z <= centerZ + radius; z++) {
			for (int x = centerX - radius; x <= centerX + radius; x++) {
				level.getChunk(x, z, ChunkStatus.FULL, true);
			}
		}
	}

	private static int squareDiameter(int radius) {
		int diameter = (radius * 2) + 1;
		return diameter * diameter;
	}

	private static int boundedInteger(String property, int fallback, int min, int max) {
		try {
			return Math.max(min, Math.min(max, Integer.parseInt(System.getProperty(property, ""))));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}
}
