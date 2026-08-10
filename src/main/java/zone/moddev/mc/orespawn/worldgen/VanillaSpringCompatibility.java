package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import zone.moddev.mc.orespawn.OreSpawn;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.ConfiguredRandomFeatureList;
import net.minecraft.world.gen.feature.DecoratedFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;
import net.minecraft.world.gen.feature.LiquidsConfig;
import net.minecraft.world.gen.feature.MultipleRandomFeatureConfig;
import net.minecraft.world.gen.feature.MultipleWithChanceRandomFeatureConfig;
import net.minecraft.world.gen.feature.SingleRandomFeature;
import net.minecraft.world.gen.feature.TwoFeatureChoiceConfig;

/** Rewrites only vanilla spring leaves to accept baked provider rocks. */
public final class VanillaSpringCompatibility extends ContextFeature<LiquidsConfig> {
	public static final VanillaSpringCompatibility FEATURE = new VanillaSpringCompatibility();
	private static volatile Set<Block> providerRocks = Collections.emptySet();

	private VanillaSpringCompatibility() {
		super(LiquidsConfig::deserialize);
		setRegistryName(OreSpawn.MODID, "provider_rock_spring");
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

	static boolean rewriteFeatureList(List<ConfiguredFeature<?>> features) {
		boolean changed = false;
		for (int index = 0; index < features.size(); index++) {
			ConfiguredFeature<?> original = features.get(index);
			ConfiguredFeature<?> rewritten = rewrite(original);
			if (rewritten != original) {
				features.set(index, rewritten);
				changed = true;
			}
		}
		return changed;
	}

	private static ConfiguredFeature<?> rewrite(ConfiguredFeature<?> root) {
		if (root == null) return null;
		if (root.feature == Feature.SPRING_FEATURE && root.config instanceof LiquidsConfig) {
			return new ConfiguredFeature<>(FEATURE, (LiquidsConfig) root.config);
		}

		Object config = root.config;
		if (config instanceof DecoratedFeatureConfig) {
			DecoratedFeatureConfig decorated = (DecoratedFeatureConfig) config;
			ConfiguredFeature<?> child = rewrite(decorated.feature);
			return child == decorated.feature ? root
					: configured(root.feature, new DecoratedFeatureConfig(child, decorated.decorator));
		}
		if (config instanceof MultipleRandomFeatureConfig) {
			MultipleRandomFeatureConfig random = (MultipleRandomFeatureConfig) config;
			boolean changed = false;
			List<ConfiguredRandomFeatureList<?>> choices = new ArrayList<>(random.features.size());
			for (ConfiguredRandomFeatureList<?> choice : random.features) {
				ConfiguredFeature<?> child = rewrite(configured(choice.feature, choice.config));
				changed |= child.feature != choice.feature || child.config != choice.config;
				choices.add(randomChoice(child, choice.chance));
			}
			ConfiguredFeature<?> fallback = rewrite(random.defaultFeature);
			changed |= fallback != random.defaultFeature;
			return changed ? configured(root.feature,
					new MultipleRandomFeatureConfig(choices, fallback)) : root;
		}
		if (config instanceof MultipleWithChanceRandomFeatureConfig) {
			MultipleWithChanceRandomFeatureConfig random =
					(MultipleWithChanceRandomFeatureConfig) config;
			List<ConfiguredFeature<?>> choices = rewrite(random.features);
			return choices == random.features ? root : configured(root.feature,
					new MultipleWithChanceRandomFeatureConfig(choices, random.count));
		}
		if (config instanceof SingleRandomFeature) {
			SingleRandomFeature random = (SingleRandomFeature) config;
			List<ConfiguredFeature<?>> choices = rewrite(random.features);
			return choices == random.features ? root
					: configured(root.feature, new SingleRandomFeature(choices));
		}
		if (config instanceof TwoFeatureChoiceConfig) {
			TwoFeatureChoiceConfig choice = (TwoFeatureChoiceConfig) config;
			ConfiguredFeature<?> whenTrue = rewrite(choice.trueFeature);
			ConfiguredFeature<?> whenFalse = rewrite(choice.falseFeature);
			return whenTrue == choice.trueFeature && whenFalse == choice.falseFeature ? root
					: configured(root.feature, new TwoFeatureChoiceConfig(whenTrue, whenFalse));
		}
		return root;
	}

	private static List<ConfiguredFeature<?>> rewrite(List<ConfiguredFeature<?>> originals) {
		List<ConfiguredFeature<?>> rewritten = null;
		for (int index = 0; index < originals.size(); index++) {
			ConfiguredFeature<?> original = originals.get(index);
			ConfiguredFeature<?> value = rewrite(original);
			if (value != original && rewritten == null) rewritten = new ArrayList<>(originals);
			if (rewritten != null) rewritten.set(index, value);
		}
		return rewritten == null ? originals : rewritten;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static ConfiguredFeature<?> configured(Feature<?> feature, Object config) {
		return new ConfiguredFeature((Feature) feature, (IFeatureConfig) config);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static ConfiguredRandomFeatureList<?> randomChoice(
			ConfiguredFeature<?> feature, Float chance) {
		return new ConfiguredRandomFeatureList((Feature) feature.feature,
				(IFeatureConfig) feature.config, chance);
	}

	@Override
	boolean place(FeaturePlaceContext<LiquidsConfig> context) {
		BlockPos pos = context.origin();
		if (!isHost(context.level().getBlockState(pos.up()).getBlock())
				|| !isHost(context.level().getBlockState(pos.down()).getBlock())) return false;

		BlockState state = context.level().getBlockState(pos);
		if (!state.isAir(context.level(), pos) && !isHost(state.getBlock())) return false;

		int rockSides = 0;
		int airSides = 0;
		for (net.minecraft.util.Direction direction : new net.minecraft.util.Direction[] {
				net.minecraft.util.Direction.WEST, net.minecraft.util.Direction.EAST,
				net.minecraft.util.Direction.NORTH, net.minecraft.util.Direction.SOUTH }) {
			BlockPos side = pos.offset(direction);
			if (isHost(context.level().getBlockState(side).getBlock())) rockSides++;
			if (context.level().isAirBlock(side)) airSides++;
		}
		if (rockSides != 3 || airSides != 1) return false;

		context.level().setBlockState(pos, context.config().state.getBlockState(), 2);
		context.level().getPendingFluidTicks().scheduleTick(
				pos, context.config().state.getFluid(), 0);
		return true;
	}
}
