package zone.moddev.mc.orespawn.worldgen;

import java.util.Random;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.GenerationSettings;
import net.minecraft.world.gen.feature.IFeatureConfig;

/** Target-local adapter for the context object introduced by Minecraft 1.17. */
final class FeaturePlaceContext<FC extends IFeatureConfig> {
	private final IWorld level;
	private final ChunkGenerator<? extends GenerationSettings> chunkGenerator;
	private final Random random;
	private final BlockPos origin;
	private final FC config;

	FeaturePlaceContext(IWorld level, ChunkGenerator<? extends GenerationSettings> chunkGenerator, Random random,
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

	ChunkGenerator<? extends GenerationSettings> chunkGenerator() {
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
}
