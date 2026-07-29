package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.SpringConfiguration;

final class VanillaSpringCompatibility {
	private static final Map<SpringConfiguration, HolderSet<Block>> ORIGINAL_HOSTS =
			new IdentityHashMap<>();

	private VanillaSpringCompatibility() {
		throw new IllegalAccessError("Not an instantiable class");
	}

	static synchronized void refresh(RegistryAccess registries, BakedGeomeConfig config) {
		Set<Block> rocks = Collections.newSetFromMap(new IdentityHashMap<>());
		if (config != null) {
			config.addRockBlocks(rocks);
		}
		refreshBlocks(registries, rocks);
	}

	static synchronized void clear(RegistryAccess registries) {
		refreshBlocks(registries, Collections.emptyList());
		ORIGINAL_HOSTS.clear();
	}

	private static void refreshBlocks(RegistryAccess registries, Iterable<Block> rocks) {
		Registry<ConfiguredFeature<?, ?>> features =
				registries.registryOrThrow(Registries.CONFIGURED_FEATURE);
		update(features, MiscOverworldFeatures.SPRING_LAVA_OVERWORLD, rocks);
		update(features, MiscOverworldFeatures.SPRING_WATER, rocks);
	}

	private static void update(Registry<ConfiguredFeature<?, ?>> features,
			ResourceKey<ConfiguredFeature<?, ?>> key, Iterable<Block> rocks) {
		ConfiguredFeature<?, ?> feature = features.get(key);
		if (feature != null && feature.config() instanceof SpringConfiguration spring) {
			update(spring, rocks);
		}
	}

	static void update(SpringConfiguration spring, Iterable<Block> rocks) {
		HolderSet<Block> original =
				ORIGINAL_HOSTS.computeIfAbsent(spring, ignored -> spring.validBlocks);
		spring.validBlocks = merge(original, rocks);
	}

	static HolderSet<Block> merge(HolderSet<Block> original, Iterable<Block> additionalBlocks) {
		List<Holder<Block>> holders = new ArrayList<>(original.size());
		Set<Block> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		boolean changed = false;
		for (Holder<Block> holder : original) {
			if (seen.add(holder.value())) {
				holders.add(holder);
			}
		}
		for (Block block : additionalBlocks) {
			if (seen.add(block)) {
				holders.add(block.builtInRegistryHolder());
				changed = true;
			}
		}
		return changed ? HolderSet.direct(holders) : original;
	}
}
