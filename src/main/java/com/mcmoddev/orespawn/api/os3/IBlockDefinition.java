package com.mcmoddev.orespawn.api.os3;

import net.minecraft.block.state.IBlockState;

public interface IBlockDefinition {

    IBlockState getBlock();

    int getChance();

    default boolean isValid() {
        return true;
    }
}
