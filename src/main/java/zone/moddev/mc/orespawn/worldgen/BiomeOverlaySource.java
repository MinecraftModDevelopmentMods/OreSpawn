package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Entry;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Palette;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Choice;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/** Delegates biome choice first, then applies pre-baked provider palettes. */
final class BiomeOverlaySource extends BiomeSource {
	static final MapCodec<BiomeOverlaySource> CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(BiomeSource.CODEC.fieldOf("delegate")
					.forGetter(BiomeOverlaySource::delegate))
					.apply(instance, delegate ->
							new BiomeOverlaySource(delegate, java.util.Collections.emptyList(), 0L)));

	private final BiomeSource delegate;
	private final Palette[] palettes;
	private final long seed;

	BiomeOverlaySource(BiomeSource delegate, List<Palette> palettes, long seed) {
		this.delegate = delegate;
		this.palettes = palettes.toArray(new Palette[palettes.size()]);
		this.seed = seed;
	}

	BiomeSource delegate() {
		return delegate;
	}

	private static Stream<Holder<Biome>> possible(BiomeSource delegate, List<Palette> palettes) {
		List<Holder<Biome>> values = new ArrayList<>(delegate.possibleBiomes());
		for (Palette palette : palettes) {
			for (Entry entry : palette.entries) values.add(entry.biome);
		}
		return values.stream();
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return possible(delegate, java.util.Arrays.asList(palettes));
	}

	@Override
	public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ,
			Climate.Sampler sampler) {
		Holder<Biome> selected = delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
		for (Palette palette : palettes) selected = select(palette, selected, quartX, quartZ);
		return selected;
	}

	private Holder<Biome> select(Palette palette, Holder<Biome> source, int quartX, int quartZ) {
		Choice choice = palette.choices.get(source.value());
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
