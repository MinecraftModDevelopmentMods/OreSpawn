package com.mcmoddev.orespawn.api;

import java.util.Random;

import com.mcmoddev.orespawn.api.os3.IBlockDefinition;

import net.minecraft.block.state.IBlockState;

public interface IBlockList {

    void addBlock(IBlockDefinition block);

    IBlockState getRandomBlock(Random rand);

    void startNewSpawn();

    void dump();

    int count();
}
