package com.mcmoddev.orespawn.api;

import java.util.Random;

import com.mcmoddev.orespawn.api.os3.IBlockDefinition;

import net.minecraft.block.state.IBlockState;

public interface IBlockList {
	public void addBlock(IBlockDefinition block);
	public IBlockState getRandomBlock(Random rand);
	public void startNewSpawn();
	public void dump();
}
