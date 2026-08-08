package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraft.block.BlockState;

/** Immutable runtime data used by biome, surface, aquifer, and weather hooks. */
final class BakedBiomeWorldgen {
	final List<Palette> palettes;
	final Map<Biome, Surface> surfaces;
	final DimensionMaterials materials;

	BakedBiomeWorldgen(List<Palette> palettes, Map<Biome, Surface> surfaces,
			DimensionMaterials materials) {
		this.palettes = Collections.unmodifiableList(palettes);
		this.surfaces = Collections.unmodifiableMap(new IdentityHashMap<>(surfaces));
		this.materials = materials;
	}

	boolean hasBiomeOverlay() { return !palettes.isEmpty(); }
	boolean hasSurfaces() { return !surfaces.isEmpty(); }

	static final class Palette {
		final long salt;
		final boolean replace;
		final int scope;
		final int regionQuartSize;
		final double coverage;
		final double fallbackWeight;
		final Set<String> includedNamespaces;
		final Set<String> excludedNamespaces;
		final Entry[] entries;
		final Map<Biome, Choice> choices;

		Palette(long salt, boolean replace, int scope, int regionQuartSize, double coverage,
				double fallbackWeight, Set<String> includedNamespaces,
				Set<String> excludedNamespaces, Entry[] entries,
				Map<Biome, Choice> choices) {
			this.salt = salt;
			this.replace = replace;
			this.scope = scope;
			this.regionQuartSize = regionQuartSize;
			this.coverage = coverage;
			this.fallbackWeight = fallbackWeight;
			this.includedNamespaces = includedNamespaces;
			this.excludedNamespaces = excludedNamespaces;
			this.entries = entries;
			this.choices = Collections.unmodifiableMap(new IdentityHashMap<>(choices));
		}
	}

	static final class Choice {
		final Biome[] outputs;
		final double[] cumulativeWeights;
		final double fallbackWeight;
		final double totalWeight;
		final int sourceHash;

		Choice(Biome[] outputs, double[] cumulativeWeights,
				double fallbackWeight, double totalWeight, int sourceHash) {
			this.outputs = outputs;
			this.cumulativeWeights = cumulativeWeights;
			this.fallbackWeight = fallbackWeight;
			this.totalWeight = totalWeight;
			this.sourceHash = sourceHash;
		}
	}

	static final class Entry {
		final Biome biome;
		final double weight;
		final Set<ResourceLocation> similarBiomes;
		final float minTemperature;
		final float maxTemperature;
		final float minDownfall;
		final float maxDownfall;

		Entry(Biome biome, double weight, Set<ResourceLocation> similarBiomes,
				float minTemperature, float maxTemperature, float minDownfall,
				float maxDownfall) {
			this.biome = biome;
			this.weight = weight;
			this.similarBiomes = similarBiomes;
			this.minTemperature = minTemperature;
			this.maxTemperature = maxTemperature;
			this.minDownfall = minDownfall;
			this.maxDownfall = maxDownfall;
		}
	}

	static final class Surface {
		final BlockState top;
		final BlockState filler;
		final BlockState underwater;
		final BlockState ceiling;
		final int fillerDepth;

		Surface(BlockState top, BlockState filler, BlockState underwater,
				BlockState ceiling, int fillerDepth) {
			this.top = top;
			this.filler = filler;
			this.underwater = underwater;
			this.ceiling = ceiling;
			this.fillerDepth = fillerDepth;
		}
	}

	static final class DimensionMaterials {
		final BlockState defaultFluid;
		final BlockState deepFluid;
		final int deepFluidMaxY;
		final BlockState snow;
		final BlockState ice;

		DimensionMaterials(BlockState defaultFluid, BlockState deepFluid, int deepFluidMaxY,
				BlockState snow, BlockState ice) {
			this.defaultFluid = defaultFluid;
			this.deepFluid = deepFluid;
			this.deepFluidMaxY = deepFluidMaxY;
			this.snow = snow;
			this.ice = ice;
		}

		boolean hasAquiferOverride() { return defaultFluid != null || deepFluid != null; }
	}
}
