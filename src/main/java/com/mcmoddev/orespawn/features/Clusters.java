package com.mcmoddev.orespawn.features;

import com.mcmoddev.orespawn.features.configs.ClusterConfiguration;
import com.mcmoddev.orespawn.misc.M;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * Generates clusters of ores or custom blocks within a circular area.
 * <p>
 * For each cluster, picks a random position within a radius around the origin,
 * checks the existing block at that position against the configured targets,
 * and replaces it if it matches.
 */
public class Clusters extends Feature<ClusterConfiguration> {
    public Clusters() {
        super(ClusterConfiguration.CODEC);
    }

    /**
     * Places clusters according to the provided configuration.
     *
     * @param context Contextual information for feature placement, including world, origin, and configuration.
     * @return true if placement completed (Minecraft ignores this return value for features).
     */
    @Override
    public boolean place(FeaturePlaceContext<ClusterConfiguration> context) {
        ClusterConfiguration config = context.config();
        WorldGenLevel world = (WorldGenLevel) context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        // Compute radius and number of attempts based on config
        int radius = config.spread / 2;
        int maxClusters = (int) (Math.PI * radius * radius);
        int clustersToPlace = Math.min(config.size, maxClusters);

        // World height bounds
        int minBuildY = world.getMinBuildHeight();
        int maxBuildY = world.getMaxBuildHeight(); // exclusive

        // Generate each cluster
        for (int i = 0; i < clustersToPlace; i++) {
            // Pick a random offset within the circular spread
            int offsetX = M.getPoint(0, config.spread, radius, random);
            int offsetZ = M.getPoint(0, config.spread, radius, random);
            int offsetY = random.nextInt(maxBuildY - minBuildY) + minBuildY;

            BlockPos targetPos = new BlockPos(
                origin.getX() + offsetX,
                offsetY,
                origin.getZ() + offsetZ
            );

            // Check existing block and replace if it matches any target state
            BlockState existingState = world.getBlockState(targetPos);
            for (var targetState : config.targetStates) {
                if (targetState.target.test(existingState, random)) {
                    // Use flag 2: no physics and no neighbor updates for performance
                    world.setBlock(targetPos, targetState.state, 2);
                }
            }
        }

        return true;
    }
}
