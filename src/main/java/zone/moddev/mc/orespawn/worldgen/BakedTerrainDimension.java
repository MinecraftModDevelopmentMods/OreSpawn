package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.util.ResourceLocation;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

/** Immutable setup-time resolution of one terrain replacement dimension. */
final class BakedTerrainDimension {
	final ResourceLocation key;
	private final Set<ResourceLocation> biomeIds;
	private final Set<String> biomeNamespaces;
	private final Set<Block> hostBlocks;
	private final Block[] smallHostSet;

	BakedTerrainDimension(ResourceLocation key, Set<ResourceLocation> biomeIds,
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

	boolean isReplaceable(IBlockState state) {
		if (state.getMaterial() == Material.AIR || state.getMaterial().isLiquid()
				|| state.getBlock() == Blocks.BEDROCK
				|| state.getBlock().hasTileEntity(state)) {
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
