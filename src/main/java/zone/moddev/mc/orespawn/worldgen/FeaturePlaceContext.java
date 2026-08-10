package zone.moddev.mc.orespawn.worldgen;

import java.util.Random;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.IChunkGenSettings;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.feature.IFeatureConfig;

/** Target-local adapter for the context object introduced by Minecraft 1.17. */
final class FeaturePlaceContext<FC extends IFeatureConfig> {
	private final IWorld level;
	private final IChunkGenerator<? extends IChunkGenSettings> chunkGenerator;
	private final Random random;
	private final BlockPos origin;
	private final FC config;

	FeaturePlaceContext(IWorld level, IChunkGenerator<? extends IChunkGenSettings> chunkGenerator, Random random,
			BlockPos origin, FC config) {
		this.level = level;
		this.chunkGenerator = chunkGenerator;
		this.random = random;
		this.origin = origin;
		this.config = config;
	}

	IWorld level() {
		return level;
	}

	IChunkGenerator<? extends IChunkGenSettings> chunkGenerator() {
		return chunkGenerator;
	}

	Random random() {
		return random;
	}

	BlockPos origin() {
		return origin;
	}

	FC config() {
		return config;
	}

	/**
	 * Forge 25 decorates the center chunk from an origin one chunk northwest.
	 * Whole-chunk pass-through features must therefore advance by one chunk;
	 * ordinary positioned features, such as springs, continue using origin().
	 */
	IChunk decorationChunk() {
		return level.getChunk((origin.getX() >> 4) + 1, (origin.getZ() >> 4) + 1);
	}
}
