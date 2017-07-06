package com.mcmoddev.orespawn.impl.os3;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import com.mcmoddev.orespawn.util.StateUtil;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class OreBuilderImpl implements OreBuilder {
	private IBlockState ore;
	private int chance;

	public OreBuilderImpl() {
		this.ore = null;
		this.chance = 100;
	}
	@Override
	public OreBuilder setOre(String name) {
		Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(name));
		if( block == null ) {
			OreSpawn.LOGGER.warn("Block {} not found!", name);
			return this;
		}
		this.ore = block.getDefaultState();
		return this;
	}

	@Override
	public OreBuilder setOre(String name, String serializedState) {
		this.setOre(name);
		
		if( this.ore == null )
			return this;
		
		this.ore = StateUtil.deserializeState(this.ore.getBlock(), serializedState);
		return this;
	}

	@SuppressWarnings("deprecation")
	@Override
	public OreBuilder setOre(String name, int metaData) {
		this.setOre(name);
		
		if( this.ore == null )
			return this;

		this.ore = this.ore.getBlock().getStateFromMeta(metaData);
		return null;
	}

	@Override
	public OreBuilder setOre(Block base) {
		this.ore = base.getDefaultState();
		return this;
	}

	@Override
	public OreBuilder setOre(Block base, String serializedState) {
		this.ore = StateUtil.deserializeState(base, serializedState);
		return this;
	}

	@SuppressWarnings("deprecation")
	@Override
	public OreBuilder setOre(Item base, int metaData) {
		this.ore = Block.getBlockFromItem(base).getStateFromMeta(metaData);
		return null;
	}

	@Override
	public OreBuilder setOre(ItemStack item) {
		return this.setOre(Block.getBlockFromItem(item.getItem()));
	}

	@Override
	public OreBuilder setChance(int chance) {
		this.chance = chance;
		return this;
	}

	@Override
	public IBlockState getOre() {
		return this.ore;
	}

	@Override
	public int getChance() {
		return this.chance;
	}
}
