package com.mcmoddev.orespawn.worldgen;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.mcmoddev.orespawn.worldgen.BakedBiomeWorldgen.Entry;
import com.mcmoddev.orespawn.worldgen.BakedBiomeWorldgen.Palette;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraftforge.common.world.BiomeGenerationSettingsBuilder;

/**
 * Adds OreSpawn's dynamic features to code-registered palette biomes before
 * they are exposed through the configured biome source.
 */
final class BiomeFeatureInstaller {
	private static final Map<Biome, BiomeGenerationSettings> ORIGINALS =
			new IdentityHashMap<>();

	private BiomeFeatureInstaller() {
	}

	static void install(BakedBiomeWorldgen config, ResourceKey<Level> dimension) {
		if (config == null || WorldgenBenchmark.isVanillaBaseline()) return;
		Set<Biome> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Palette palette : config.palettes) {
			for (Entry entry : palette.entries) {
				Biome biome = entry.biome.value();
				if (visited.add(biome)) install(biome, dimension);
			}
		}
	}

	static void restoreAll() {
		for (Map.Entry<Biome, BiomeGenerationSettings> entry : ORIGINALS.entrySet()) {
			entry.getKey().generationSettings = entry.getValue();
		}
		ORIGINALS.clear();
	}

	private static void install(Biome biome, ResourceKey<Level> dimension) {
		BiomeGenerationSettings original = biome.getGenerationSettings();
		BiomeGenerationSettingsBuilder builder =
				new BiomeGenerationSettingsBuilder(original);
		if (!OreSpawnBiomeModifier.apply(builder)) return;
		ORIGINALS.putIfAbsent(biome, original);
		biome.generationSettings = builder.build();
	}
}
