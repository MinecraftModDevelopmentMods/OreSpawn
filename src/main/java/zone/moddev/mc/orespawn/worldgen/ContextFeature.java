package zone.moddev.mc.orespawn.worldgen;

import java.util.Random;

import com.mojang.serialization.Codec;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;

/** Preserves the later context-shaped feature implementation on Forge 36. */
abstract class ContextFeature<FC extends IFeatureConfig> extends Feature<FC> {
	ContextFeature(Codec<FC> codec) {
		super(codec);
	}

	@Override
	public final boolean place(ISeedReader level, ChunkGenerator chunkGenerator, Random random,
			BlockPos origin, FC config) {
		return place(new FeaturePlaceContext<>(level, chunkGenerator, random, origin, config));
	}

	abstract boolean place(FeaturePlaceContext<FC> context);
}
