package com.mcmoddev.orespawn.features.configs;

import com.google.common.collect.ImmutableList;
import com.mcmoddev.orespawn.OreSpawn;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

import java.util.List;

public class VeinConfiguration implements FeatureConfiguration {
    public final List<TargetBlockState> targetStates;
    public final int size;
    public final int maxLength;
    public final int minLength;
    public final float frequency;

    public static final Codec<VeinConfiguration> CODEC = RecordCodecBuilder.create(
            (builder) -> builder.group(
                    Codec.list(TargetBlockState.CODEC).fieldOf("targets").forGetter((config) -> config.targetStates ),
                    Codec.INT.fieldOf("size").forGetter((config) -> config.size),
                    Codec.INT.fieldOf("max_length").forGetter((config) -> config.maxLength),
                    Codec.INT.fieldOf("min_length").forGetter((config) -> config.minLength),
                    Codec.FLOAT.fieldOf("frequency").forGetter((config) -> config.frequency)
            ).apply(builder, VeinConfiguration::new)
    );

    public VeinConfiguration(List<TargetBlockState> targets, int size, int maxlength, int minlength, float frequency) {
        this.targetStates = targets;
        this.size = size;
        this.maxLength = maxlength;
        this.minLength = minlength;
        this.frequency = frequency;
    }

    public VeinConfiguration(RuleTest target, BlockState state, int size, int maxlength, int minlength, float frequency) {
        this(ImmutableList.of(new TargetBlockState(target, state)), size, maxlength, minlength, frequency);
        OreSpawn.LOGGER.info("Vein of size {} and a length between {} and {} configured", size, minlength, maxlength);
    }


    public static class TargetBlockState {
        public static final Codec<VeinConfiguration.TargetBlockState> CODEC = RecordCodecBuilder.create(
                p_161039_ -> p_161039_.group(
                                RuleTest.CODEC.fieldOf("target").forGetter(p_161043_ -> p_161043_.target),
                                BlockState.CODEC.fieldOf("state").forGetter(p_161041_ -> p_161041_.state)
                        )
                        .apply(p_161039_, VeinConfiguration.TargetBlockState::new)
        );
        public final RuleTest target;
        public final BlockState state;

        TargetBlockState(RuleTest pRule, BlockState pState) {
            this.target = pRule;
            this.state = pState;
        }
    }
}
