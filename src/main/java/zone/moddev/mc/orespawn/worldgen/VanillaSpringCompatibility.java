package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.configurations.SpringConfiguration;

final class VanillaSpringCompatibility {
	private static final Map<SpringConfiguration, HolderSet<Block>> ORIGINAL_HOSTS = new IdentityHashMap<>();

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
		update(MiscOverworldFeatures.SPRING_LAVA_OVERWORLD.value().config(), rocks);
		update(MiscOverworldFeatures.SPRING_WATER.value().config(), rocks);
	}

	private static void update(SpringConfiguration spring, Iterable<Block> rocks) {
		HolderSet<Block> original = ORIGINAL_HOSTS.computeIfAbsent(spring, ignored -> spring.validBlocks);
		spring.validBlocks = merge(original, rocks);
	}

	static HolderSet<Block> merge(HolderSet<Block> original, Iterable<Block> additionalBlocks) {
		List<Holder<Block>> holders = new ArrayList<>(original.size());
		Set<Block> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Holder<Block> holder : original) {
			if (seen.add(holder.value())) {
				holders.add(holder);
			}
		}
		for (Block block : additionalBlocks) {
			if (seen.add(block)) {
				holders.add(block.builtInRegistryHolder());
			}
		}
		return HolderSet.direct(holders);
	}
}
