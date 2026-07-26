package com.mcmoddev.orespawn.worldgen;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.mojang.datafixers.util.Pair;
import com.mcmoddev.orespawn.OreSpawnConfig.GeologyMode;
import com.mcmoddev.orespawn.worldgen.FormationSettings.Preset;

import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraftforge.common.BiomeDictionary;
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
				source.placeFluidDeposits());
		if (Boolean.getBoolean("orespawn.worldgenBenchmarkVanillaOres")) {
			com.google.gson.JsonObject root = benchmarkProfile.rootCopy();
			root.addProperty("manage_vanilla_ores", true);
			benchmarkProfile = benchmarkProfile.withRoot(root);
		}
		WorldGeologyProfileManager.applyBenchmarkProfile(benchmarkProfile);
	}

	private static void onServerStarted(ServerStartedEvent event) {
		String dimensionName = System.getProperty("orespawn.worldgenBenchmarkDimension", "overworld")
				.trim().toLowerCase(Locale.ROOT);
		ServerLevel level = event.getServer().getLevel(benchmarkDimensionKey(dimensionName));
		if (level == null) {
			throw new IllegalStateException("Benchmark dimension is unavailable: " + dimensionName);
		}
		int radius = boundedInteger("orespawn.worldgenBenchmarkRadius", 4, 1, 16);
		int repetitions = boundedInteger("orespawn.worldgenBenchmarkRepetitions", 3, 1, 9);
		int warmupRadius = Math.min(2, radius);
		int chunks = squareDiameter(radius);
		int baseCenterX = integer("orespawn.worldgenBenchmarkCenterX", 256);
		int baseCenterZ = integer("orespawn.worldgenBenchmarkCenterZ", 256);
		int[] locatedCenter = locateBiomeType(level, baseCenterX, baseCenterZ);
		baseCenterX = locatedCenter[0];
		baseCenterZ = locatedCenter[1];
		int centerStep = integer("orespawn.worldgenBenchmarkCenterStep", 64);

		LOGGER.info("ORESPAWN_BENCHMARK start mode={} dimension={} seed={} target_chunks={} repetitions={} "
				+ "java={} processors={} max_heap_mb={}",
				MODE, level.dimension().location(), level.getSeed(), chunks, repetitions,
				System.getProperty("java.version"),
				Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / (1024L * 1024L));

		generateSquare(level, baseCenterX - 64, baseCenterZ - 64, warmupRadius);
		double[] milliseconds = new double[repetitions];
		for (int repetition = 0; repetition < repetitions; repetition++) {
			int centerX = baseCenterX + (repetition * centerStep);
			long started = System.nanoTime();
			generateSquare(level, centerX, baseCenterZ, radius);
			milliseconds[repetition] = (System.nanoTime() - started) / 1_000_000.0D;
			LOGGER.info("ORESPAWN_BENCHMARK result mode={} repetition={} chunks={} elapsed_ms={} ms_per_chunk={}",
					MODE, repetition + 1, chunks, format(milliseconds[repetition]),
					format(milliseconds[repetition] / chunks));
			if (Boolean.getBoolean("orespawn.worldgenBenchmarkOreAudit")) {
				auditOres(level, centerX, baseCenterZ, radius, repetition + 1);
			}
		}

		double[] sorted = milliseconds.clone();
		Arrays.sort(sorted);
		double median = sorted[sorted.length / 2];
		LOGGER.info("ORESPAWN_BENCHMARK summary mode={} chunks_per_repetition={} repetitions={} "
				+ "median_ms={} median_ms_per_chunk={} min_ms={} max_ms={}",
				MODE, chunks, repetitions, format(median), format(median / chunks),
				format(sorted[0]), format(sorted[sorted.length - 1]));
		if (Boolean.getBoolean("orespawn.worldgenBenchmarkStopServer")) {
			LOGGER.info("ORESPAWN_BENCHMARK stopping server after completed benchmark");
			event.getServer().halt(false);
		}
	}

	static ResourceKey<Level> benchmarkDimensionKey(String configured) {
		String dimensionName = configured.trim().toLowerCase(Locale.ROOT);
		return switch (dimensionName) {
			case "overworld" -> Level.OVERWORLD;
			case "nether" -> Level.NETHER;
			case "end" -> Level.END;
			default -> {
				ResourceLocation id = ResourceLocation.tryParse(dimensionName);
				if (id == null) {
					throw new IllegalArgumentException("Invalid benchmark dimension: " + configured);
				}
				yield ResourceKey.create(Registry.DIMENSION_REGISTRY, id);
			}
		};
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

	private static int[] locateBiomeType(ServerLevel level, int centerX, int centerZ) {
		String configured = System.getProperty("orespawn.worldgenBenchmarkBiomeType", "").trim();
		if (configured.isEmpty()) {
			return new int[] { centerX, centerZ };
		}
		BiomeDictionary.Type type = BiomeDictionary.Type.getType(configured);
		BlockPos origin = new BlockPos(centerX << 4, level.getSeaLevel(), centerZ << 4);
		Pair<BlockPos, Holder<Biome>> located = level.findNearestBiome(holder -> holder.unwrapKey()
				.map(key -> BiomeDictionary.hasType(key, type)).orElse(false), origin, 16384, 32);
		if (located == null) {
			throw new IllegalStateException("Benchmark could not locate biome dictionary type " + configured);
		}
		int locatedX = located.getFirst().getX() >> 4;
		int locatedZ = located.getFirst().getZ() >> 4;
		LOGGER.info("ORESPAWN_BENCHMARK located biome_type={} center_chunk_x={} center_chunk_z={}",
				configured.toUpperCase(Locale.ROOT), locatedX, locatedZ);
		return new int[] { locatedX, locatedZ };
	}

	private static void auditOres(ServerLevel level, int centerX, int centerZ, int radius,
			int repetition) {
		Map<String, OreAudit> audits = new LinkedHashMap<>();
		if (net.minecraft.world.level.Level.NETHER.equals(level.dimension())) {
			audits.put("nether_gold", new OreAudit(Blocks.NETHER_GOLD_ORE, Blocks.NETHER_GOLD_ORE));
			audits.put("quartz", new OreAudit(Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_QUARTZ_ORE));
			audits.put("ancient_debris", new OreAudit(Blocks.ANCIENT_DEBRIS, Blocks.ANCIENT_DEBRIS));
		} else {
			audits.put("coal", new OreAudit(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE));
			audits.put("copper", new OreAudit(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE));
			audits.put("iron", new OreAudit(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE));
			audits.put("gold", new OreAudit(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE));
			audits.put("redstone", new OreAudit(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE));
			audits.put("diamond", new OreAudit(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE));
			audits.put("lapis", new OreAudit(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE));
			audits.put("emerald", new OreAudit(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE));
		}
		addConfiguredAudits(audits);

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
		for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
			for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
				LevelChunk chunk = level.getChunk(chunkX, chunkZ);
				int minX = chunk.getPos().getMinBlockX();
				int minZ = chunk.getPos().getMinBlockZ();
				for (int localX = 0; localX < 16; localX++) {
					for (int localZ = 0; localZ < 16; localZ++) {
						cursor.set(minX + localX, chunk.getMinBuildHeight(), minZ + localZ);
						for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
							cursor.setY(y);
							BlockState state = chunk.getBlockState(cursor);
							for (OreAudit audit : audits.values()) {
								if (audit.accepts(state.getBlock())) {
									audit.record(localX, localZ, y,
											isAdjacentToAir(level, cursor.getX(), y, cursor.getZ(), neighbor));
								}
							}
						}
					}
				}
			}
		}

		int chunks = squareDiameter(radius);
		for (Map.Entry<String, OreAudit> entry : audits.entrySet()) {
			OreAudit audit = entry.getValue();
			LOGGER.info("ORESPAWN_BENCHMARK_ORE mode={} repetition={} ore={} total={} per_chunk={} "
					+ "min_y={} max_y={} out_of_range={} exposed={} exposed_pct={} "
					+ "x_low_pct={} x_high_pct={} z_low_pct={} z_high_pct={}",
					MODE, repetition, entry.getKey(), audit.total, format(audit.total / (double) chunks),
					audit.minimumObserved(), audit.maximumObserved(), audit.outOfRange,
					audit.exposed, format(audit.exposedPercent()),
					format(audit.edgePercent(audit.byX, 0)), format(audit.edgePercent(audit.byX, 14)),
					format(audit.edgePercent(audit.byZ, 0)), format(audit.edgePercent(audit.byZ, 14)));
			audit.assertExpectation(entry.getKey());
		}
	}

	private static void addConfiguredAudits(Map<String, OreAudit> audits) {
		String configured = System.getProperty("orespawn.worldgenBenchmarkBlockAudit", "").trim();
		if (configured.isEmpty()) {
			return;
		}
		for (String specification : configured.split(";")) {
			String[] fields = specification.trim().split(",");
			if (fields.length != 4) {
				throw new IllegalArgumentException("Invalid benchmark block audit specification: " + specification);
			}
			ResourceLocation id = ResourceLocation.tryParse(fields[0].trim());
			if (id == null || !Registry.BLOCK.containsKey(id)) {
				throw new IllegalArgumentException("Unknown benchmark audit block: " + fields[0].trim());
			}
			int minimumY = Integer.parseInt(fields[1].trim());
			int maximumY = Integer.parseInt(fields[2].trim());
			boolean required = switch (fields[3].trim().toLowerCase(Locale.ROOT)) {
				case "present" -> true;
				case "absent" -> false;
				default -> throw new IllegalArgumentException(
						"Benchmark audit expectation must be present or absent: " + specification);
			};
			audits.put(id.toString(), new OreAudit(Registry.BLOCK.get(id), Registry.BLOCK.get(id),
					minimumY, maximumY, required));
		}
	}

	private static boolean isAdjacentToAir(ServerLevel level, int x, int y, int z,
			BlockPos.MutableBlockPos cursor) {
		return level.getBlockState(cursor.set(x + 1, y, z)).isAir()
				|| level.getBlockState(cursor.set(x - 1, y, z)).isAir()
				|| level.getBlockState(cursor.set(x, y + 1, z)).isAir()
				|| level.getBlockState(cursor.set(x, y - 1, z)).isAir()
				|| level.getBlockState(cursor.set(x, y, z + 1)).isAir()
				|| level.getBlockState(cursor.set(x, y, z - 1)).isAir();
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

	private static int integer(String property, int fallback) {
		try {
			return Integer.parseInt(System.getProperty(property, ""));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static final class OreAudit {
		private final Block shallow;
		private final Block deep;
		private final int minimumY;
		private final int maximumY;
		private final Boolean required;
		private final long[] byX = new long[16];
		private final long[] byZ = new long[16];
		private long total;
		private long exposed;
		private long outOfRange;
		private int minimumObserved = Integer.MAX_VALUE;
		private int maximumObserved = Integer.MIN_VALUE;

		OreAudit(Block shallow, Block deep) {
			this(shallow, deep, Integer.MIN_VALUE, Integer.MAX_VALUE, null);
		}

		OreAudit(Block shallow, Block deep, int minimumY, int maximumY, Boolean required) {
			this.shallow = shallow;
			this.deep = deep;
			this.minimumY = minimumY;
			this.maximumY = maximumY;
			this.required = required;
		}

		boolean accepts(Block block) {
			return block == shallow || block == deep;
		}

		void record(int localX, int localZ, int y, boolean isExposed) {
			total++;
			byX[localX]++;
			byZ[localZ]++;
			minimumObserved = Math.min(minimumObserved, y);
			maximumObserved = Math.max(maximumObserved, y);
			if (y < minimumY || y > maximumY) outOfRange++;
			if (isExposed) exposed++;
		}

		String minimumObserved() {
			return total == 0L ? "n/a" : Integer.toString(minimumObserved);
		}

		String maximumObserved() {
			return total == 0L ? "n/a" : Integer.toString(maximumObserved);
		}

		void assertExpectation(String name) {
			if (outOfRange != 0L) {
				throw new IllegalStateException("Benchmark audit found " + outOfRange
						+ " out-of-range blocks for " + name + " (expected " + minimumY + ".." + maximumY + ")");
			}
			if (Boolean.TRUE.equals(required) && total == 0L) {
				throw new IllegalStateException("Benchmark audit found no blocks for required ore " + name);
			}
			if (Boolean.FALSE.equals(required) && total != 0L) {
				throw new IllegalStateException("Benchmark audit found " + total + " blocks for absent ore " + name);
			}
		}

		double exposedPercent() {
			return total == 0L ? 0.0D : (exposed * 100.0D) / total;
		}

		double edgePercent(long[] counts, int start) {
			double edgeAverage = (counts[start] + counts[start + 1]) / 2.0D;
			long interior = 0L;
			for (int i = 2; i < 14; i++) interior += counts[i];
			double interiorAverage = interior / 12.0D;
			return interiorAverage <= 0.0D ? 0.0D : (edgeAverage * 100.0D) / interiorAverage;
		}
	}
}
