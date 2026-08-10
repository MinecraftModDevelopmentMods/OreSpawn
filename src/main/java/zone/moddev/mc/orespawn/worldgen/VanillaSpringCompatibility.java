package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import zone.moddev.mc.orespawn.OreSpawn;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.CompositeFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;
import net.minecraft.world.gen.feature.LiquidsConfig;
import net.minecraft.world.gen.feature.RandomDefaultFeatureListConfig;
import net.minecraft.world.gen.feature.RandomFeatureListConfig;
import net.minecraft.world.gen.feature.RandomFeatureWithConfigConfig;
import net.minecraft.world.gen.feature.TwoFeatureChoiceConfig;

/** Rewrites only vanilla spring leaves to accept baked provider rocks. */
public final class VanillaSpringCompatibility extends ContextFeature<LiquidsConfig> {
	public static final VanillaSpringCompatibility FEATURE = new VanillaSpringCompatibility();
	private static volatile Set<Block> providerRocks = Collections.emptySet();

	private VanillaSpringCompatibility() {
		super();
	}

	static void refresh(BakedGeomeConfig config) {
		Set<Block> rocks = Collections.newSetFromMap(new IdentityHashMap<>());
		if (config != null) config.addRockBlocks(rocks);
		refreshBlocks(rocks);
	}

	static void refreshBlocks(Iterable<Block> rocks) {
		Set<Block> refreshed = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Block rock : rocks) refreshed.add(rock);
		providerRocks = Collections.unmodifiableSet(refreshed);
	}

	static boolean isHost(Block block) {
		return Block.isRock(block) || providerRocks.contains(block);
	}

	static boolean rewriteFeatureList(List<CompositeFeature<?, ?>> features) {
		boolean changed = false;
		for (int index = 0; index < features.size(); index++) {
			CompositeFeature<?, ?> original = features.get(index);
			FeatureConfig rewritten = rewrite(original.getFeature(),
					ConfiguredFeatureInspector.featureConfig(original));
			if (rewritten.changed) {
				features.set(index, ConfiguredFeatureInspector.replaceRoot(
						original, rewritten.feature, rewritten.config));
				changed = true;
			}
		}
		return changed;
	}

	private static FeatureConfig rewrite(Feature<?> feature, IFeatureConfig config) {
		if (feature == Feature.LIQUIDS && config instanceof LiquidsConfig) {
			return new FeatureConfig(FEATURE, config, true);
		}
		if (config instanceof RandomDefaultFeatureListConfig) {
			RandomDefaultFeatureListConfig original = (RandomDefaultFeatureListConfig) config;
			Feature<?>[] features = original.field_202449_a.clone();
			IFeatureConfig[] configs = original.field_202450_b.clone();
			boolean changed = false;
			for (int index = 0; index < features.length; index++) {
				FeatureConfig child = rewrite(features[index], configs[index]);
				features[index] = child.feature;
				configs[index] = child.config;
				changed |= child.changed;
			}
			FeatureConfig fallback = rewrite(original.field_202452_d, original.field_202453_f);
			changed |= fallback.changed;
			return changed ? new FeatureConfig(feature,
					randomDefault(features, configs, original.field_202451_c.clone(), fallback), true)
					: new FeatureConfig(feature, config, false);
		}
		if (config instanceof RandomFeatureListConfig) {
			RandomFeatureListConfig original = (RandomFeatureListConfig) config;
			Feature<?>[] features = original.field_202454_a.clone();
			IFeatureConfig[] configs = original.field_202455_b.clone();
			boolean changed = false;
			for (int index = 0; index < features.length; index++) {
				FeatureConfig child = rewrite(features[index], configs[index]);
				features[index] = child.feature;
				configs[index] = child.config;
				changed |= child.changed;
			}
			return changed ? new FeatureConfig(feature,
					new RandomFeatureListConfig(features, configs, original.field_202456_c), true)
					: new FeatureConfig(feature, config, false);
		}
		if (config instanceof RandomFeatureWithConfigConfig) {
			RandomFeatureWithConfigConfig original = (RandomFeatureWithConfigConfig) config;
			Feature<?>[] features = original.features.clone();
			IFeatureConfig[] configs = original.configs.clone();
			boolean changed = false;
			for (int index = 0; index < features.length; index++) {
				FeatureConfig child = rewrite(features[index], configs[index]);
				features[index] = child.feature;
				configs[index] = child.config;
				changed |= child.changed;
			}
			return changed ? new FeatureConfig(feature,
					new RandomFeatureWithConfigConfig(features, configs), true)
					: new FeatureConfig(feature, config, false);
		}
		if (config instanceof TwoFeatureChoiceConfig) {
			TwoFeatureChoiceConfig original = (TwoFeatureChoiceConfig) config;
			FeatureConfig first = rewrite(original.field_202445_a, original.field_202446_b);
			FeatureConfig second = rewrite(original.field_202447_c, original.field_202448_d);
			return first.changed || second.changed ? new FeatureConfig(feature,
					new TwoFeatureChoiceConfig(first.feature, first.config,
							second.feature, second.config), true)
					: new FeatureConfig(feature, config, false);
		}
		return new FeatureConfig(feature, config, false);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static RandomDefaultFeatureListConfig randomDefault(Feature<?>[] features,
			IFeatureConfig[] configs, float[] chances, FeatureConfig fallback) {
		return new RandomDefaultFeatureListConfig(features, configs, chances,
				(Feature) fallback.feature, fallback.config);
	}

	@Override
	boolean place(FeaturePlaceContext<LiquidsConfig> context) {
		BlockPos pos = context.origin();
		if (!isHost(context.level().getBlockState(pos.up()).getBlock())
				|| !isHost(context.level().getBlockState(pos.down()).getBlock())) return false;

		IBlockState state = context.level().getBlockState(pos);
		if (!state.isAir(context.level(), pos) && !isHost(state.getBlock())) return false;

		int rockSides = 0;
		int airSides = 0;
		for (net.minecraft.util.EnumFacing direction : new net.minecraft.util.EnumFacing[] {
				net.minecraft.util.EnumFacing.WEST, net.minecraft.util.EnumFacing.EAST,
				net.minecraft.util.EnumFacing.NORTH, net.minecraft.util.EnumFacing.SOUTH }) {
			BlockPos side = pos.offset(direction);
			if (isHost(context.level().getBlockState(side).getBlock())) rockSides++;
			if (context.level().isAirBlock(side)) airSides++;
		}
		if (rockSides != 3 || airSides != 1) return false;

		context.level().setBlockState(pos,
				context.config().field_202459_a.getDefaultState().getBlockState(), 2);
		context.level().getPendingFluidTicks().scheduleTick(
				pos, context.config().field_202459_a, 0);
		return true;
	}

	private static final class FeatureConfig {
		final Feature<?> feature;
		final IFeatureConfig config;
		final boolean changed;

		FeatureConfig(Feature<?> feature, IFeatureConfig config, boolean changed) {
			this.feature = feature;
			this.config = config;
			this.changed = changed;
		}
	}
}
