package zone.moddev.mc.orespawn.worldgen;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;

public final class GeomeDistributionSampler {
	private static final int TERRAIN_SAMPLE_MAGIC = 0x4D54524E;
	private static final int TERRAIN_SAMPLE_VERSION = 1;

	private GeomeDistributionSampler() {
		throw new IllegalAccessError("Not an instantiable class");
	}

	public static String sample(long seed, Iterable<Biome> biomes, int columnsPerBiome, int yStep) {
		return sample(seed, biomes, columnsPerBiome, yStep, true);
	}

	public static String sample(long seed, Iterable<Biome> biomes, int columnsPerBiome, int yStep,
			boolean includeBiomeAudit) {
		BakedGeomeConfig config = GeomeConfig.baked();
		GeomeGeology geology = new GeomeGeology(seed, config);
		Map<String, Integer> geomeCounts = new LinkedHashMap<>();
		Map<String, Integer> rockCounts = new LinkedHashMap<>();
		Map<String, Biome> biomeAudit = new TreeMap<>();
		ShapeMetrics shape = new ShapeMetrics();

		int biomeIndex = 0;
		int samples = 0;
		long selectionSignature = 0xCBF29CE484222325L;
		int step = Math.max(1, yStep);
		for (Biome biome : biomes) {
			Identifier biomeId = zone.moddev.mc.orespawn.worldgen.BiomeRegistryAccess.id(biome);
			if (!isOverworldGeologyBiome(biomeId, biome)) {
				continue;
			}
			biomeAudit.put(biomeId.toString(), biome);
			int baseX = biomeIndex * 257;
			int baseZ = biomeIndex * -193;
			for (int column = 0; column < columnsPerBiome; column++) {
				int x = baseX + (column * 19);
				int z = baseZ + (column * 23);
				add(geomeCounts, geology.getGeomeName(biome, x, z));
				shape.addDrift(geology, x, z);
				for (int y = 8; y <= 96; y += step) {
					Block block = geology.getStoneAt(biome, x, y, z, 96);
					Identifier id = BuiltInRegistries.BLOCK.getKey(block);
					selectionSignature ^= id == null ? 0 : id.toString().hashCode();
					selectionSignature *= 0x100000001B3L;
					add(rockCounts, id == null ? "<unregistered>" : id.toString());
					shape.addAgreement(config, block,
							geology.getStoneAt(biome, x + 16, y, z, 96),
							geology.getStoneAt(biome, x + 64, y, z, 96));
					samples++;
				}
			}

			if (biomeIndex == 0) {
				shape.sampleRunsAndPatches(geology, biome, baseX, baseZ);
			}
			biomeIndex++;
		}

		StringBuilder report = new StringBuilder();
		report.append("Geome sampler seed=").append(seed)
				.append(" columnsPerBiome=").append(columnsPerBiome)
				.append(" yStep=").append(step)
				.append(" auditedBiomes=").append(biomeAudit.size())
				.append(" rockSamples=").append(samples)
				.append('\n');
		report.append("selection_signature=")
				.append(Long.toUnsignedString(selectionSignature, 16)).append('\n');
		appendCounts(report, "geomes", geomeCounts);
		appendCounts(report, "rocks", rockCounts);
		appendDiversity(report, "sampled_rock_diversity", rockCounts, samples);
		appendFamilyProfiles(report, config);
		if (includeBiomeAudit) {
			appendBiomeAudit(report, geology, config, biomeAudit);
		}
		shape.append(report);
		return report.toString();
	}

	public static String sampleTerrain(long seed, Path path) throws IOException {
		BakedGeomeConfig config = GeomeConfig.baked();
		GeomeGeology geology = new GeomeGeology(seed, config);
		Map<String, Integer> geomeCounts = new LinkedHashMap<>();
		Map<String, Integer> rockCounts = new LinkedHashMap<>();
		int[] familyCounts = new int[RockFamily.values().length];
		double[] regionalValues = new double[config.geomeCount()];
		long selectionSignature = 0xCBF29CE484222325L;
		long started = System.nanoTime();
		int samples = 0;

		try (DataInputStream input = new DataInputStream(
				new BufferedInputStream(Files.newInputStream(path)))) {
			if (input.readInt() != TERRAIN_SAMPLE_MAGIC) {
				throw new IOException("Not a OreSpawn terrain sample: " + path);
			}
			int version = input.readInt();
			if (version != TERRAIN_SAMPLE_VERSION) {
				throw new IOException("Unsupported OreSpawn terrain sample version " + version);
			}
			int width = input.readInt();
			int depth = input.readInt();
			int minX = input.readInt();
			int minZ = input.readInt();
			int minY = input.readInt();
			int maxY = input.readInt();
			int paletteSize = input.readInt();
			if (width <= 0 || depth <= 0 || minY > maxY || paletteSize <= 0) {
				throw new IOException("Invalid OreSpawn terrain sample dimensions");
			}

			Biome[] biomePalette = new Biome[paletteSize];
			for (int index = 0; index < paletteSize; index++) {
				int length = input.readUnsignedShort();
				byte[] encoded = new byte[length];
				input.readFully(encoded);
				Identifier biomeId = Identifier.parse(new String(encoded, StandardCharsets.UTF_8));
				biomePalette[index] = zone.moddev.mc.orespawn.worldgen.BiomeRegistryAccess.get(biomeId);
				if (biomePalette[index] == null) {
					throw new IOException("Unknown biome " + biomeId + " in " + path);
				}
			}

			int height = maxY - minY + 1;
			byte[] rockMask = new byte[(height + 7) / 8];
			for (int zOffset = 0; zOffset < depth; zOffset++) {
				for (int xOffset = 0; xOffset < width; xOffset++) {
					int biomeIndex = input.readUnsignedShort();
					if (biomeIndex >= biomePalette.length) {
						throw new IOException("Invalid biome palette index " + biomeIndex);
					}
					input.readFully(rockMask);
					Biome biome = biomePalette[biomeIndex];
					int x = minX + xOffset;
					int z = minZ + zOffset;
					int geomeIndex = geology.classifyColumn(biome, x, z, regionalValues);
					int stratumOffset = geology.stratumOffsetAt(x, z);
					long formationRegion = geology.formationRegionAt(x, z);
					add(geomeCounts, config.geomeName(geomeIndex));
					for (int yIndex = 0; yIndex < height; yIndex++) {
						if ((rockMask[yIndex >>> 3] & (1 << (yIndex & 7))) == 0) {
							continue;
						}
						Block block = geology.getStoneAt(geomeIndex, stratumOffset, formationRegion,
								x, minY + yIndex, z);
						Identifier id = BuiltInRegistries.BLOCK.getKey(block);
						String rockId = id == null ? "<unregistered>" : id.toString();
						selectionSignature ^= rockId.hashCode();
						selectionSignature *= 0x100000001B3L;
						add(rockCounts, rockId);
						RockFamily family = config.familyOf(block.defaultBlockState());
						if (family != null) {
							familyCounts[family.ordinal()]++;
						}
						samples++;
					}
				}
			}

			StringBuilder report = new StringBuilder();
			report.append("Terrain replay seed=").append(seed)
					.append(" bounds=").append(minX).append("..").append(minX + width - 1)
					.append(',').append(minY).append("..").append(maxY)
					.append(',').append(minZ).append("..").append(minZ + depth - 1)
					.append(" rockSamples=").append(samples)
					.append(" elapsedMs=").append((System.nanoTime() - started) / 1_000_000L)
					.append('\n');
			report.append("selection_signature=")
					.append(Long.toUnsignedString(selectionSignature, 16)).append('\n');
			appendCounts(report, "geomes", geomeCounts);
			appendCounts(report, "rocks", rockCounts);
			appendDiversity(report, "sampled_rock_diversity", rockCounts, samples);
			report.append("families:");
			for (RockFamily family : RockFamily.values()) {
				report.append(' ').append(family.configName).append('=')
						.append(percent(familyCounts[family.ordinal()], samples)).append('%');
			}
			return report.append('\n').toString();
		}
	}

	private static boolean isOverworldGeologyBiome(Identifier biomeId, Biome biome) {
		if (biomeId == null || "minecraft:the_void".equals(biomeId.toString())) {
			return false;
		}
		ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME, biomeId);
		return !BiomeTypeCompatibility.hasType(biomeKey, "NETHER")
				&& !BiomeTypeCompatibility.hasType(biomeKey, "END")
				&& !BiomeTypeCompatibility.hasType(biomeKey, "VOID");
	}

	private static void appendBiomeAudit(StringBuilder report, GeomeGeology geology,
			BakedGeomeConfig config, Map<String, Biome> biomes) {
		report.append("biome geology audit:").append('\n');
		Map<String, Integer> namespaceCounts = new TreeMap<>();
		List<String> neutralBiomes = new ArrayList<>();
		int biomeIndex = 0;
		for (Entry<String, Biome> entry : biomes.entrySet()) {
			Identifier biomeId = Identifier.parse(entry.getKey());
			add(namespaceCounts, biomeId.getNamespace());
			if (!config.hasDistinctBiomeWeights(entry.getValue())) {
				neutralBiomes.add(entry.getKey());
			}
			int[] familyCounts = new int[RockFamily.values().length];
			Map<String, Integer> geomeCounts = new LinkedHashMap<>();
			int total = 0;
			int baseX = 8192 + (biomeIndex * 2053);
			int baseZ = -4096 - (biomeIndex * 1597);
			for (int column = 0; column < 64; column++) {
				int x = baseX + (column * 37);
				int z = baseZ + (column * 53);
				add(geomeCounts, geology.getGeomeName(entry.getValue(), x, z));
				for (int y = -32; y <= 96; y += 8) {
					Block block = geology.getStoneAt(entry.getValue(), x, y, z, 96);
					RockFamily family = config.familyOf(block.defaultBlockState());
					if (family != null) {
						familyCounts[family.ordinal()]++;
						total++;
					}
				}
			}
			report.append("  ").append(entry.getKey())
					.append(" types=").append(biomeTypes(biomeId))
					.append(" temperature=").append(format(entry.getValue().getBaseTemperature()))
					.append(" downfall=").append(format(
							entry.getValue().getModifiedClimateSettings().downfall()))
					.append(" dominant=").append(config.dominantBiomeWeight(entry.getValue()))
					.append(" weights=").append(config.describeBiomeWeights(entry.getValue()))
					.append(" geomes=").append(geomeCounts);
			for (RockFamily family : RockFamily.values()) {
				report.append(' ').append(family.configName).append('=')
						.append(percent(familyCounts[family.ordinal()], total)).append('%');
			}
			report.append('\n');
			biomeIndex++;
		}
		report.append("biome audit summary: total=").append(biomes.size())
				.append(" namespaces=").append(namespaceCounts)
				.append(" neutral=").append(neutralBiomes.size());
		if (!neutralBiomes.isEmpty()) {
			report.append(' ').append(neutralBiomes);
		}
		report.append('\n');
	}

	private static String biomeTypes(Identifier biomeId) {
		List<String> names = new ArrayList<>();
		Biome biome = zone.moddev.mc.orespawn.worldgen.BiomeRegistryAccess.get(biomeId);
		if (biome != null) names.addAll(BiomeTypeCompatibility.types(biome));
		Collections.sort(names);
		return names.toString();
	}

	private static void appendFamilyProfiles(StringBuilder report, BakedGeomeConfig config) {
		report.append("geome families:").append('\n');
		for (int geome = 0; geome < config.geomes.length; geome++) {
			int[] counts = new int[RockFamily.values().length];
			int total = 0;
			for (int y = -32; y <= 96; y += 8) {
				for (int bucket = 0; bucket < 256; bucket++) {
					for (int slot = 0; slot < config.familyDiversitySlots(); slot++) {
						counts[config.pickFamily(geome, y, bucket, slot).ordinal()]++;
						total++;
					}
				}
			}
			report.append("  ").append(config.geomes[geome].name);
			for (RockFamily family : RockFamily.values()) {
				report.append(' ').append(family.configName).append('=')
						.append(percent(counts[family.ordinal()], total)).append('%');
			}
			report.append('\n');
		}
	}

	private static void appendCounts(StringBuilder report, String label, Map<String, Integer> counts) {
		report.append(label).append(':').append('\n');
		for (Entry<String, Integer> entry : counts.entrySet()) {
			report.append("  ").append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
		}
	}

	private static void add(Map<String, Integer> counts, String key) {
		Integer current = counts.get(key);
		counts.put(key, current == null ? 1 : current + 1);
	}

	private static <T> void appendDiversity(StringBuilder report, String label,
			Map<T, Integer> counts, int total) {
		int dominant = 0;
		List<Integer> rankedCounts = new ArrayList<Integer>(counts.values());
		Collections.sort(rankedCounts, Collections.reverseOrder());
		int topFour = 0;
		for (int i = 0; i < Math.min(4, rankedCounts.size()); i++) {
			topFour += rankedCounts.get(i);
		}
		double entropy = 0.0D;
		for (int count : counts.values()) {
			dominant = Math.max(dominant, count);
			if (count > 0 && total > 0) {
				double probability = count / (double) total;
				entropy -= probability * Math.log(probability);
			}
		}
		report.append(label).append(": distinct=").append(counts.size())
				.append(" dominant=").append(percent(dominant, total)).append('%')
				.append(" top_four=").append(percent(topFour, total)).append('%')
				.append(" effective=").append(format(Math.exp(entropy))).append('\n');
	}

	private static final class ShapeMetrics {
		private final List<Integer> horizontalRuns = new ArrayList<Integer>();
		private final List<Integer> horizontalLayerRuns = new ArrayList<Integer>();
		private final List<Integer> verticalRuns = new ArrayList<Integer>();
		private final List<Integer> verticalLayerRuns = new ArrayList<Integer>();
		private final List<Integer> drift16 = new ArrayList<Integer>();
		private final List<Integer> drift64 = new ArrayList<Integer>();
		private int agreementSamples;
		private int exact16;
		private int exact64;
		private int family16;
		private int family64;
		private PatchMetrics patches = new PatchMetrics();
		private final Map<Block, Integer> localRockCounts = new LinkedHashMap<Block, Integer>();
		private int localRockSamples;

		void addAgreement(BakedGeomeConfig config, Block origin, Block at16, Block at64) {
			agreementSamples++;
			if (origin == at16) {
				exact16++;
			}
			if (origin == at64) {
				exact64++;
			}
			RockFamily originFamily = config.familyOf(origin.defaultBlockState());
			if (originFamily != null && originFamily == config.familyOf(at16.defaultBlockState())) {
				family16++;
			}
			if (originFamily != null && originFamily == config.familyOf(at64.defaultBlockState())) {
				family64++;
			}
		}

		void addDrift(GeomeGeology geology, int x, int z) {
			int base = geology.stratumOffsetAt(x, z);
			drift16.add(Math.abs(base - geology.stratumOffsetAt(x + 16, z)));
			drift64.add(Math.abs(base - geology.stratumOffsetAt(x + 64, z)));
		}

		void sampleRunsAndPatches(GeomeGeology geology, Biome biome, int baseX, int baseZ) {
			for (int zOffset = 0; zOffset < 16; zOffset++) {
				Block previous = null;
				int run = 0;
				for (int xOffset = 0; xOffset < 256; xOffset++) {
					Block block = geology.getStoneAt(biome, baseX + xOffset, 32, baseZ + zOffset, 96);
					if (block == previous) {
						run++;
					} else {
						if (run > 0) {
							horizontalRuns.add(run);
						}
						previous = block;
						run = 1;
					}
				}
				horizontalRuns.add(run);
			}

			for (int y = -32; y <= 96; y += 16) {
				for (int zOffset = 0; zOffset < 16; zOffset++) {
					int previousLayer = Integer.MIN_VALUE;
					int run = 0;
					for (int xOffset = 0; xOffset < 1024; xOffset++) {
						int layer = geology.stratumLayerAt(baseX + xOffset, y, baseZ + zOffset);
						if (layer == previousLayer) {
							run++;
						} else {
							if (run > 0) {
								horizontalLayerRuns.add(run);
							}
							previousLayer = layer;
							run = 1;
						}
					}
					horizontalLayerRuns.add(run);
				}
			}

			for (int column = 0; column < 32; column++) {
				int x = baseX + (column * 7);
				int z = baseZ + (column * 11);
				Block previous = null;
				int previousLayer = Integer.MIN_VALUE;
				int run = 0;
				int layerRun = 0;
				for (int y = BakedGeomeConfig.MIN_Y; y <= 96; y++) {
					Block block = geology.getStoneAt(biome, x, y, z, 96);
					int layer = geology.stratumLayerAt(x, y, z);
					if (block == previous) {
						run++;
					} else {
						if (run > 0) {
							verticalRuns.add(run);
						}
						previous = block;
						run = 1;
					}
					if (layer == previousLayer) {
						layerRun++;
					} else {
						if (layerRun > 0) {
							verticalLayerRuns.add(layerRun);
						}
						previousLayer = layer;
						layerRun = 1;
					}
				}
				verticalRuns.add(run);
				verticalLayerRuns.add(layerRun);
			}

			patches = PatchMetrics.sample(geology, biome, baseX, baseZ, 64, 32);
			for (int y = -32; y <= 96; y += 32) {
				for (int zOffset = 0; zOffset < 64; zOffset += 4) {
					for (int xOffset = 0; xOffset < 1024; xOffset += 4) {
						Block block = geology.getStoneAt(biome, baseX + xOffset, y,
								baseZ + zOffset, 96);
						Integer count = localRockCounts.get(block);
						localRockCounts.put(block, count == null ? 1 : count + 1);
						localRockSamples++;
					}
				}
			}
		}

		void append(StringBuilder report) {
			report.append("shape:").append('\n');
			report.append("  horizontal_rock_run_mean=").append(format(mean(horizontalRuns))).append('\n');
			report.append("  horizontal_layer_run_mean=").append(format(mean(horizontalLayerRuns))).append('\n');
			report.append("  vertical_rock_run_mean=").append(format(mean(verticalRuns))).append('\n');
			report.append("  vertical_layer_run_mean=").append(format(mean(verticalLayerRuns))).append('\n');
			report.append("  exact_agreement_16=").append(percent(exact16, agreementSamples)).append('%').append('\n');
			report.append("  exact_agreement_64=").append(percent(exact64, agreementSamples)).append('%').append('\n');
			report.append("  family_agreement_16=").append(percent(family16, agreementSamples)).append('%').append('\n');
			report.append("  family_agreement_64=").append(percent(family64, agreementSamples)).append('%').append('\n');
			report.append("  drift_median_16=").append(median(drift16)).append('\n');
			report.append("  drift_median_64=").append(median(drift64)).append('\n');
			report.append("  patch_area_mean=").append(format(patches.meanArea)).append('\n');
			report.append("  patch_compactness=").append(format(patches.compactness * 100.0D)).append('%').append('\n');
			report.append("  ");
			appendDiversity(report, "local_rock_diversity", localRockCounts, localRockSamples);
		}
	}

	private static final class PatchMetrics {
		final double meanArea;
		final double compactness;

		PatchMetrics() {
			this(0.0D, 0.0D);
		}

		PatchMetrics(double meanArea, double compactness) {
			this.meanArea = meanArea;
			this.compactness = compactness;
		}

		static PatchMetrics sample(GeomeGeology geology, Biome biome, int baseX, int baseZ, int size, int y) {
			Block[] blocks = new Block[size * size];
			for (int z = 0; z < size; z++) {
				for (int x = 0; x < size; x++) {
					blocks[(z * size) + x] = geology.getStoneAt(biome, baseX + x, y, baseZ + z, 96);
				}
			}

			boolean[] visited = new boolean[blocks.length];
			ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
			int components = 0;
			int totalArea = 0;
			double weightedCompactness = 0.0D;
			for (int start = 0; start < blocks.length; start++) {
				if (visited[start]) {
					continue;
				}
				Block target = blocks[start];
				visited[start] = true;
				queue.add(start);
				int area = 0;
				int minX = size;
				int maxX = 0;
				int minZ = size;
				int maxZ = 0;
				while (!queue.isEmpty()) {
					int index = queue.removeFirst();
					int x = index % size;
					int z = index / size;
					area++;
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minZ = Math.min(minZ, z);
					maxZ = Math.max(maxZ, z);
					visit(blocks, visited, queue, target, size, x - 1, z);
					visit(blocks, visited, queue, target, size, x + 1, z);
					visit(blocks, visited, queue, target, size, x, z - 1);
					visit(blocks, visited, queue, target, size, x, z + 1);
				}
				int boxArea = (maxX - minX + 1) * (maxZ - minZ + 1);
				components++;
				totalArea += area;
				weightedCompactness += area * (area / (double) boxArea);
			}
			return components == 0 ? new PatchMetrics()
					: new PatchMetrics(totalArea / (double) components, weightedCompactness / totalArea);
		}

		private static void visit(Block[] blocks, boolean[] visited, ArrayDeque<Integer> queue, Block target,
				int size, int x, int z) {
			if (x < 0 || x >= size || z < 0 || z >= size) {
				return;
			}
			int index = (z * size) + x;
			if (!visited[index] && blocks[index] == target) {
				visited[index] = true;
				queue.add(index);
			}
		}
	}

	private static double mean(List<Integer> values) {
		if (values.isEmpty()) {
			return 0.0D;
		}
		long total = 0L;
		for (int value : values) {
			total += value;
		}
		return total / (double) values.size();
	}

	private static int median(List<Integer> values) {
		if (values.isEmpty()) {
			return 0;
		}
		List<Integer> sorted = new ArrayList<Integer>(values);
		Collections.sort(sorted);
		return sorted.get(sorted.size() / 2);
	}

	private static String percent(int count, int total) {
		return format(total == 0 ? 0.0D : (count * 100.0D) / total);
	}

	private static String format(double value) {
		return String.format(java.util.Locale.ROOT, "%.2f", value);
	}
}
