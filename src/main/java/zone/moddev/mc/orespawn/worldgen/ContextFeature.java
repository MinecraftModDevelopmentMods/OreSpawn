package zone.moddev.mc.orespawn.worldgen;

import java.util.Random;
import java.util.function.Function;

import com.mojang.datafixers.Dynamic;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.GenerationSettings;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;

/** Preserves the later context-shaped feature implementation on Forge 31. */
abstract class ContextFeature<FC extends IFeatureConfig> extends Feature<FC> {
	ContextFeature(Function<Dynamic<?>, ? extends FC> decoder) {
		super(decoder);
	}

	@Override
	public final boolean place(IWorld level, ChunkGenerator<? extends GenerationSettings> chunkGenerator, Random random,
			BlockPos origin, FC config) {
		return place(new FeaturePlaceContext<>(level, chunkGenerator, random, origin, config));
	}

	abstract boolean place(FeaturePlaceContext<FC> context);
}
