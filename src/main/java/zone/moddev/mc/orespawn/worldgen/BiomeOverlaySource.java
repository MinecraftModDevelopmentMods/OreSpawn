package zone.moddev.mc.orespawn.worldgen;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nullable;

import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Choice;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Entry;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Palette;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.feature.structure.Structure;

/** Delegates biome choice first, then applies pre-baked provider palettes. */
final class BiomeOverlaySource extends BiomeProvider {
	private final BiomeProvider delegate;
	private final Palette[] palettes;
	private final Set<Biome> possibleBiomes;
	private final long seed;

	BiomeOverlaySource(BiomeProvider delegate, List<Palette> palettes, long seed) {
		this.delegate = delegate;
		this.palettes = palettes.toArray(new Palette[palettes.size()]);
		this.possibleBiomes = possible(palettes);
		this.seed = seed;
	}

	BiomeProvider delegate() {
		return delegate;
	}

	private static Set<Biome> possible(List<Palette> palettes) {
		Set<Biome> values = new HashSet<>();
		for (Palette palette : palettes) {
			for (Entry entry : palette.entries) values.add(entry.biome);
		}
		return values;
	}

	@Override
	public Biome getBiome(int blockX, int blockZ) {
		return selectAll(delegate.getBiome(blockX, blockZ),
				Math.floorDiv(blockX, 4), Math.floorDiv(blockZ, 4));
	}

	@Override
	public Biome func_222366_b(int quartX, int quartZ) {
		return selectAll(delegate.func_222366_b(quartX, quartZ), quartX, quartZ);
	}

	@Override
	public Biome[] getBiomes(int blockX, int blockZ, int width, int length,
			boolean cacheFlag) {
		Biome[] selected = delegate.getBiomes(blockX, blockZ, width, length, cacheFlag);
		for (int z = 0; z < length; z++) {
			for (int x = 0; x < width; x++) {
				int index = x + z * width;
				selected[index] = selectAll(selected[index],
						Math.floorDiv(blockX + x, 4), Math.floorDiv(blockZ + z, 4));
			}
		}
		return selected;
	}

	@Override
	public Set<Biome> getBiomesInSquare(int centerX, int centerZ, int sideLength) {
		int minQuartX = centerX - sideLength >> 2;
		int minQuartZ = centerZ - sideLength >> 2;
		int maxQuartX = centerX + sideLength >> 2;
		int maxQuartZ = centerZ + sideLength >> 2;
		Set<Biome> result = new HashSet<>();
		for (int quartZ = minQuartZ; quartZ <= maxQuartZ; quartZ++) {
			for (int quartX = minQuartX; quartX <= maxQuartX; quartX++) {
				result.add(func_222366_b(quartX, quartZ));
			}
		}
		return result;
	}

	@Override
	@Nullable
	public BlockPos findBiomePosition(int x, int z, int range, List<Biome> biomes,
			Random random) {
		int minQuartX = x - range >> 2;
		int minQuartZ = z - range >> 2;
		int maxQuartX = x + range >> 2;
		int maxQuartZ = z + range >> 2;
		BlockPos selected = null;
		int matches = 0;
		for (int quartZ = minQuartZ; quartZ <= maxQuartZ; quartZ++) {
			for (int quartX = minQuartX; quartX <= maxQuartX; quartX++) {
				if (!biomes.contains(func_222366_b(quartX, quartZ))) continue;
				if (selected == null || random.nextInt(matches + 1) == 0) {
					selected = new BlockPos(quartX << 2, 0, quartZ << 2);
				}
				matches++;
			}
		}
		return selected;
	}

	@Override
	public boolean hasStructure(Structure<?> structure) {
		if (delegate.hasStructure(structure)) return true;
		for (Biome biome : possibleBiomes) {
			if (biome.hasStructure(structure)) return true;
		}
		return false;
	}

	@Override
	public Set<BlockState> getSurfaceBlocks() {
		Set<BlockState> result = new HashSet<>(delegate.getSurfaceBlocks());
		for (Biome biome : possibleBiomes) {
			result.add(biome.getSurfaceBuilderConfig().getTop());
		}
		return result;
	}

	@Override
	public List<Biome> getBiomesToSpawnIn() {
		return delegate.getBiomesToSpawnIn();
	}

	@Override
	public float func_222365_c(int x, int z) {
		return delegate.func_222365_c(x, z);
	}

	private Biome selectAll(Biome selected, int quartX, int quartZ) {
		for (Palette palette : palettes) selected = select(palette, selected, quartX, quartZ);
		return selected;
	}

	private Biome select(Palette palette, Biome source, int quartX, int quartZ) {
		Choice choice = palette.choices.get(source);
		if (choice == null) return source;
		int regionX = Math.floorDiv(quartX, palette.regionQuartSize);
		int regionZ = Math.floorDiv(quartZ, palette.regionQuartSize);
		long regionHash = mix(seed ^ palette.salt, regionX, regionZ);
		if (unit(regionHash) >= palette.coverage) return source;

		double cursor = unit(mix(regionHash ^ 0x6A09E667F3BCC909L,
				choice.sourceHash, 0)) * choice.totalWeight;
		if (cursor < choice.fallbackWeight) return source;
		for (int index = 0; index < choice.outputs.length; index++) {
			if (cursor < choice.cumulativeWeights[index]) return choice.outputs[index];
		}
		return source;
	}

	private static long mix(long seed, int x, int z) {
		long value = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	private static double unit(long value) {
		return (value >>> 11) * 0x1.0p-53;
	}
}
