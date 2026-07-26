package com.mcmoddev.orespawn.worldgen;

import java.util.Collections;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Creates runtime-only holders for profile-driven features. */
final class WorldgenFeatureHolders {
	private WorldgenFeatureHolders() {
	}

	static <F extends Feature<NoneFeatureConfiguration>> Holder<PlacedFeature> direct(F feature) {
		Holder<ConfiguredFeature<?, ?>> configured = Holder.direct(
				new ConfiguredFeature<NoneFeatureConfiguration, F>(
						feature, NoneFeatureConfiguration.INSTANCE));
		return Holder.direct(new PlacedFeature(configured, Collections.emptyList()));
	}
}
