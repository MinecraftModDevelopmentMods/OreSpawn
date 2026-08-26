package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Immutable setup-time resolution of one terrain replacement dimension. */
final class BakedTerrainDimension {
	final ResourceKey<Level> key;
	private final Set<ResourceLocation> biomeIds;
	private final Set<String> biomeNamespaces;
	private final Set<Block> hostBlocks;
	private final Block[] smallHostSet;

	BakedTerrainDimension(ResourceKey<Level> key, Set<ResourceLocation> biomeIds,
			Set<String> biomeNamespaces, Set<Block> hostBlocks) {
		this.key = key;
		this.biomeIds = Collections.unmodifiableSet(new LinkedHashSet<>(biomeIds));
		this.biomeNamespaces = Collections.unmodifiableSet(new LinkedHashSet<>(biomeNamespaces));
		this.hostBlocks = Collections.unmodifiableSet(hostBlocks);
		this.smallHostSet = hostBlocks.size() <= 8 ? hostBlocks.toArray(new Block[hostBlocks.size()]) : null;
	}

	boolean acceptsBiome(ResourceLocation biomeId) {
		return biomeIds.isEmpty() && biomeNamespaces.isEmpty()
				|| biomeId != null && (biomeIds.contains(biomeId)
						|| biomeNamespaces.contains(biomeId.getNamespace()));
	}

	boolean hasBiomeFilter() {
		return !biomeIds.isEmpty() || !biomeNamespaces.isEmpty();
	}

	boolean isReplaceable(BlockState state) {
		if (state.isAir() || !state.getFluidState().isEmpty()
				|| state.getBlock() == Blocks.BEDROCK) {
			return false;
		}
		if (smallHostSet != null) {
			Block block = state.getBlock();
			for (Block host : smallHostSet) {
				if (block == host) {
					return true;
				}
			}
			return false;
		}
		return hostBlocks.contains(state.getBlock());
	}
}
