package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Set;

import com.google.common.base.Predicate;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.fml.common.eventhandler.Event;

/** Extends Forge 1.10's native ore-host predicate with baked provider rocks. */
public final class VanillaSpringCompatibility {
	private static volatile Set<Block> providerRocks = Collections.emptySet();

	private VanillaSpringCompatibility() {
	}

	static void refresh(BakedGeomeConfig config) {
		Set<Block> rocks = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
		if (config != null) config.addRockBlocks(rocks);
		providerRocks = Collections.unmodifiableSet(rocks);
	}

	static void refreshBlocks(Collection<Block> blocks) {
		Set<Block> rocks = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
		rocks.addAll(blocks);
		providerRocks = Collections.unmodifiableSet(rocks);
	}

	static boolean isProviderRock(Block block) {
		return providerRocks.contains(block);
	}

	static boolean accepts(World world, BlockPos pos, IBlockState state) {
		Predicate<IBlockState> nativeStone = candidate -> candidate.getBlock() == Blocks.STONE
				|| candidate.getBlock() == Blocks.NETHERRACK;
		return state.getBlock().isReplaceableOreGen(state, world, pos, nativeStone)
				|| providerRocks.contains(state.getBlock());
	}

	static void replaceVanillaSpringPass(DecorateBiomeEvent.Decorate event) {
		if (providerRocks.isEmpty() || event.getResult() == Event.Result.DENY) return;
		Block fluid;
		int attempts;
		if (event.getType() == DecorateBiomeEvent.Decorate.EventType.LAKE_WATER) {
			fluid = Blocks.FLOWING_WATER; attempts = 50;
		} else if (event.getType() == DecorateBiomeEvent.Decorate.EventType.LAKE_LAVA) {
			fluid = Blocks.FLOWING_LAVA; attempts = 20;
		} else {
			return;
		}
		World world = event.getWorld(); java.util.Random random = event.getRand();
		BlockPos origin = event.getPos();
		for (int attempt = 0; attempt < attempts; attempt++) {
			int x = random.nextInt(16) + 8;
			int z = random.nextInt(16) + 8;
			int y = fluid == Blocks.FLOWING_WATER
					? random.nextInt(random.nextInt(248) + 8)
					: random.nextInt(random.nextInt(random.nextInt(240) + 8) + 8);
			generate(fluid, world, random, origin.add(x, y, z));
		}
		event.setResult(Event.Result.DENY);
	}

	static boolean generate(Block fluid, World world, java.util.Random random, BlockPos pos) {
		// Never query an unloaded neighbour while the current chunk is populating.
		// That would cascade generation across an edge before Forge is ready.
		if (!loaded(world, pos) || !loaded(world, pos.up()) || !loaded(world, pos.down())
				|| !loaded(world, pos.west()) || !loaded(world, pos.east())
				|| !loaded(world, pos.north()) || !loaded(world, pos.south())) return false;
		if (!accepts(world, pos.up(), world.getBlockState(pos.up()))
				|| !accepts(world, pos.down(), world.getBlockState(pos.down()))) return false;
		IBlockState current = world.getBlockState(pos);
		if (!current.getBlock().isAir(current, world, pos) && !accepts(world, pos, current)) return false;
		int solid = 0; int air = 0;
		for (BlockPos neighbour : new BlockPos[] { pos.west(), pos.east(), pos.north(), pos.south() }) {
			IBlockState state = world.getBlockState(neighbour);
			if (accepts(world, neighbour, state)) solid++;
			if (state.getBlock().isAir(state, world, neighbour)) air++;
		}
		if (solid == 3 && air == 1) {
			IBlockState state = fluid.getDefaultState();
			world.setBlockState(pos, state, 2);
			world.immediateBlockTick(pos, state, random);
		}
		return true;
	}

	private static boolean loaded(World world, BlockPos pos) {
		return world.isBlockLoaded(pos, false);
	}
}
