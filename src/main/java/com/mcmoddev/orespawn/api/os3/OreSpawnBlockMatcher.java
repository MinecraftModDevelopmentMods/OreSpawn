package com.mcmoddev.orespawn.api.os3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.util.StateUtil;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

public class OreSpawnBlockMatcher implements Predicate<IBlockState> {
	private final List<IBlockState> possibles;
	
	public OreSpawnBlockMatcher(final IBlockState...matches) {
		this.possibles = Arrays.asList(matches);
	}
	
	public OreSpawnBlockMatcher(final List<IBlockState> matches) {
		this.possibles = new ArrayList<>();
		this.possibles.addAll(matches);
	}
	
	private boolean has(final IBlockState bs) {
		return this.possibles.stream().filter(bs::equals).count() > 0;
	}
	
	public boolean test(final IBlockState other) {
		return (other != null) && (!other.getBlock().equals(Blocks.AIR)) && this.has(other);
	}
	
	public JsonArray serialize() {
		JsonArray rv = new JsonArray();
		
		possibles.stream()
		.forEach(bs -> {
			JsonObject t = new JsonObject();
			t.addProperty(Constants.ConfigNames.NAME, bs.getBlock().getRegistryName().toString());
			if(!bs.equals(bs.getBlock().getDefaultState())) {
				String state = StateUtil.serializeState(bs);
				t.addProperty(Constants.ConfigNames.STATE, state);
			}
			rv.add(t);
		});
		
		return rv;
	}
}
