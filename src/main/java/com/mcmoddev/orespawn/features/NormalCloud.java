package com.mcmoddev.orespawn.features;

import com.mcmoddev.orespawn.features.configs.NormalCloudConfiguration;
import com.mcmoddev.orespawn.misc.M;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

import java.util.HashSet;
import java.util.Set;

/**
 * Generates "cloud" formations of ores or custom blocks scattered within a circular area.
 * <p>
 * Attempts up to size placements, skipping duplicates and positions outside range,
 * replacing matching blocks without physics or neighbor updates for performance.
 */
public class NormalCloud extends Feature<NormalCloudConfiguration> {
    public NormalCloud() {
        super(NormalCloudConfiguration.CODEC);
    }

    /**
     * Places scattered blocks (cloud) based on configuration parameters.
     * <p>
     * For each placement attempt, picks a random offset within the configured spread,
     * clamps Y to world bounds, skips duplicates, and replaces the block if it matches any target.
     * Stops early if maxAttempts reached (twice the desired count) to avoid infinite loops.
     *
     * @param context Placement context including world, random, origin, and config
     * @return true always (feature return value is ignored)
     */
    @Override
    public boolean place(FeaturePlaceContext<NormalCloudConfiguration> context) {
        NormalCloudConfiguration config = context.config();
        WorldGenLevel world = (WorldGenLevel) context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        // Compute half of configured spread as radius
        int radius = config.spread / 2;
        // Maximum distinct positions within circle
        int desiredCount = (int) (Math.PI * radius * radius);
        // Actual number of placements to attempt
        int placementsNeeded = Math.min(config.size, desiredCount);

        // World height constraints
        int minY = world.getMinBuildHeight();
        int maxY = world.getMaxBuildHeight(); // exclusive

        // Track positions already processed to avoid duplicates
        Set<BlockPos> seenPositions = new HashSet<>();
        int attempts = 0;
        int maxAttempts = placementsNeeded * 2;

        while (seenPositions.size() < placementsNeeded && attempts < maxAttempts) {
            attempts++;

            // Random offset within circular spread
            int offsetX = M.getPoint(0, config.spread, radius, random);
            int offsetZ = M.getPoint(0, config.spread, radius, random);
            int offsetY = random.nextInt(maxY - minY) + minY;

            BlockPos targetPos = new BlockPos(
                origin.getX() + offsetX,
                offsetY,
                origin.getZ() + offsetZ
            );

            // Skip duplicates
            if (!seenPositions.add(targetPos)) {
                continue;
            }

            // Check existing block and replace if it matches any target state
            BlockState existingState = world.getBlockState(targetPos);
            for (var targetState : config.targetStates) {
                if (targetState.target.test(existingState, random)) {
                    // Flag=2: no physics, no neighbor updates
                    world.setBlock(targetPos, targetState.state, 2);
                }
            }
        }

        return true;
    }
}
