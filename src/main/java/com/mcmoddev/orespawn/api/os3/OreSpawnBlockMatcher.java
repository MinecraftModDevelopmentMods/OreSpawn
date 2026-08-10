package com.mcmoddev.orespawn.api.os3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

public class OreSpawnBlockMatcher implements Predicate<IBlockState> {
	private final List<IBlockState> possibles;
	public OreSpawnBlockMatcher(IBlockState... matches) { possibles = Arrays.asList(matches); }
	public OreSpawnBlockMatcher(List<IBlockState> matches) { possibles = new ArrayList<>(matches); }
	private boolean has(IBlockState state) { return possibles.contains(state); }
	@Override public boolean test(IBlockState state) { return state != null && state.getBlock() != Blocks.AIR && has(state); }
	public JsonArray serialize() {
		JsonArray result = new JsonArray();
		for (IBlockState state : possibles) {
			JsonObject value = new JsonObject();
			value.addProperty("name", state.getBlock().getRegistryName().toString());
			int metadata = state.getBlock().getMetaFromState(state);
			if (metadata != 0) value.addProperty("metadata", metadata);
			result.add(value);
		}
		return result;
	}
}
