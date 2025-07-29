package com.mcmoddev.orespawn.features.configs;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

import java.util.List;

public class ClusterConfiguration implements FeatureConfiguration {
    public final List<VeinConfiguration.TargetBlockState> targetStates;
    public final int size;
    public final int spread;
    public final int nodeSize;

    public static final Codec<ClusterConfiguration> CODEC = RecordCodecBuilder.create(
            (builder) -> builder.group(
                    Codec.list(VeinConfiguration.TargetBlockState.CODEC).fieldOf("targets").forGetter((config) -> config.targetStates ),
                    Codec.INT.fieldOf("size").forGetter((config) -> config.size),
                    Codec.INT.fieldOf("spread").forGetter((config) -> config.spread),
                    Codec.INT.fieldOf("node_size").forGetter((config) -> config.nodeSize))
                    .apply(builder, ClusterConfiguration::new));

    public ClusterConfiguration(List<VeinConfiguration.TargetBlockState> targetStates, int size, int spread, int nodeSize) {
        this.targetStates = targetStates;
        this.size = size;
        this.spread = spread;
        this.nodeSize = nodeSize;
    }

    public ClusterConfiguration(RuleTest target, BlockState state, int size, int spread, int nodeSize) {
        this(ImmutableList.of(new VeinConfiguration.TargetBlockState(target, state)), size, spread, nodeSize);
    }
}
