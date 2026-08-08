package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import net.minecraft.data.worldgen.Features;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpringConfiguration;

final class VanillaSpringCompatibility {
	private static final Map<SpringConfiguration, Set<Block>> ORIGINAL_HOSTS = new IdentityHashMap<>();

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
		update(vanillaSpring(Features.SPRING_LAVA), rocks);
		update(vanillaSpring(Features.SPRING_WATER), rocks);
	}

	static SpringConfiguration vanillaSpring(ConfiguredFeature<?, ?> root) {
		return root.getFeatures().map(ConfiguredFeature::config)
				.filter(SpringConfiguration.class::isInstance)
				.map(SpringConfiguration.class::cast).findFirst()
				.orElseThrow(() -> new IllegalStateException("Vanilla spring configuration is missing"));
	}

	private static void update(SpringConfiguration spring, Iterable<Block> rocks) {
		Set<Block> original = ORIGINAL_HOSTS.computeIfAbsent(spring, ignored -> spring.validBlocks);
		spring.validBlocks = merge(original, rocks);
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
		// SpringConfiguration.CODEC casts this field to ImmutableSet in 1.17.1.
		return merged.build();
	}
}
