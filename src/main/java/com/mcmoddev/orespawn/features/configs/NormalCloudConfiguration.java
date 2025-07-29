package com.mcmoddev.orespawn.features.configs;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

import java.util.List;

public class NormalCloudConfiguration  implements FeatureConfiguration {
    public final List<VeinConfiguration.TargetBlockState> targetStates;
    public final int size;
    public final int spread;

    public static final Codec<NormalCloudConfiguration> CODEC = RecordCodecBuilder.create(
            (builder) -> builder.group(
                    Codec.list(VeinConfiguration.TargetBlockState.CODEC).fieldOf("targets").forGetter((config) -> config.targetStates ),
                    Codec.INT.fieldOf("size").forGetter((config) -> config.size),
                    Codec.INT.fieldOf("spread").forGetter((config) -> config.spread) ).apply(builder, NormalCloudConfiguration::new));


    public NormalCloudConfiguration(List<VeinConfiguration.TargetBlockState> targetStates, int size, int spread) {
        this.targetStates = targetStates;
        this.size = size;
        this.spread = spread;
    }

    public NormalCloudConfiguration(RuleTest target, BlockState state, int size, int spread) {
        this(ImmutableList.of(new VeinConfiguration.TargetBlockState(target, state)), size, spread);
    }
}
