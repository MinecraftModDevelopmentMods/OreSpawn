package com.mcmoddev.orespawn.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.common.collect.ImmutableList;
import com.mcmoddev.orespawn.api.os3.OreBuilder;

import net.minecraft.block.state.IBlockState;

/** OS3 3.2 weighted output container retained for unchanged feature binaries. */
public class OreList {
	private final List<OreBuilder> values = new ArrayList<>();
	private int total;

	public void build(List<OreBuilder> ores) {
		values.clear(); total = 0;
		for (OreBuilder ore : ores) {
			if (ore != null && ore.getOre() != null && ore.getChance() > 0) {
				values.add(ore); total += ore.getChance();
			}
		}
	}

	public OreBuilder getRandomOre(Random random) {
		if (values.isEmpty()) return null;
		int selected = random.nextInt(Math.max(1, total));
		for (OreBuilder value : values) {
			selected -= value.getChance(); if (selected < 0) return value;
		}
		return values.get(values.size() - 1);
	}

	public ImmutableList<IBlockState> getOres() {
		ImmutableList.Builder<IBlockState> result = ImmutableList.builder();
		for (OreBuilder value : values) result.add(value.getOre());
		return result.build();
	}
}
