package zone.moddev.mc.orespawn.worldgen;

import java.util.Random;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.gen.IChunkGenSettings;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;

/** Preserves the later context-shaped feature implementation on Forge 25. */
abstract class ContextFeature<FC extends IFeatureConfig> extends Feature<FC> {
	ContextFeature() {
		super();
	}

	@Override
	public final boolean func_212245_a(IWorld level, IChunkGenerator<? extends IChunkGenSettings> chunkGenerator,
			Random random,
			BlockPos origin, FC config) {
		return place(new FeaturePlaceContext<>(level, chunkGenerator, random, origin, config));
	}

	abstract boolean place(FeaturePlaceContext<FC> context);
}
