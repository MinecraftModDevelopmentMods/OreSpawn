package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import net.minecraft.block.Block;
import net.minecraft.world.biome.DefaultBiomeFeatures;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.LiquidsConfig;

final class VanillaSpringCompatibility {
	private static final Map<LiquidsConfig, Set<Block>> ORIGINAL_HOSTS = new IdentityHashMap<>();

	private VanillaSpringCompatibility() {
		throw new IllegalAccessError("Not an instantiable class");
	}

	static synchronized void refresh(BakedGeomeConfig config) {
		Set<Block> rocks = Collections.newSetFromMap(new IdentityHashMap<>());
		if (config != null) {
			config.addRockBlocks(rocks);
		}
		refreshBlocks(rocks);
	}

	static synchronized void refreshBlocks(Iterable<Block> rocks) {
		update(DefaultBiomeFeatures.LAVA_SPRING_CONFIG, rocks);
		update(DefaultBiomeFeatures.WATER_SPRING_CONFIG, rocks);
	}

	static LiquidsConfig vanillaSpring(ConfiguredFeature<?, ?> root) {
		if (!(root.config instanceof LiquidsConfig)) {
			throw new IllegalStateException("Vanilla spring configuration is missing");
		}
		return (LiquidsConfig) root.config;
	}

	private static void update(LiquidsConfig spring, Iterable<Block> rocks) {
		Set<Block> original = ORIGINAL_HOSTS.computeIfAbsent(spring, ignored -> spring.acceptedBlocks);
		spring.acceptedBlocks = merge(original, rocks);
	}

	static Set<Block> merge(Set<Block> original, Iterable<Block> additionalBlocks) {
		Set<Block> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		ImmutableSet.Builder<Block> merged = ImmutableSet.builder();
		for (Block block : original) {
			if (seen.add(block)) merged.add(block);
		}
		for (Block block : additionalBlocks) {
			if (seen.add(block)) merged.add(block);
		}
		// LiquidsConfig.CODEC casts this field to ImmutableSet in 1.15.2.
		return merged.build();
	}
}
