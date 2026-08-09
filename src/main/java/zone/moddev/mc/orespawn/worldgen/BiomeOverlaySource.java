package zone.moddev.mc.orespawn.worldgen;

import java.util.LinkedHashSet;
import java.util.List;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Entry;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Palette;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Choice;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.provider.BiomeProvider;

/** Delegates biome choice first, then applies pre-baked provider palettes. */
final class BiomeOverlaySource extends BiomeProvider {
	private final BiomeProvider delegate;
	private final Palette[] palettes;
	private final long seed;

	BiomeOverlaySource(BiomeProvider delegate, List<Palette> palettes, long seed) {
		super(possible(delegate, palettes));
		this.delegate = delegate;
		this.palettes = palettes.toArray(new Palette[palettes.size()]);
		this.seed = seed;
	}

	BiomeProvider delegate() {
		return delegate;
	}

	private static java.util.Set<Biome> possible(BiomeProvider delegate, List<Palette> palettes) {
		java.util.Set<Biome> values = new LinkedHashSet<>(delegate.biomes);
		for (Palette palette : palettes) {
			for (Entry entry : palette.entries) values.add(entry.biome);
		}
		return values;
	}

	@Override
	public Biome getNoiseBiome(int quartX, int quartY, int quartZ) {
		Biome selected = delegate.getNoiseBiome(quartX, quartY, quartZ);
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
