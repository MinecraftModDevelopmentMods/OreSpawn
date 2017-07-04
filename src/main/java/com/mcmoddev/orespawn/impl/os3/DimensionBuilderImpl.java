package com.mcmoddev.orespawn.impl.os3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.mcmoddev.orespawn.api.os3.DimensionBuilder;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;

public class DimensionBuilderImpl implements DimensionBuilder {
	private static final String UNNAMED = "unnamed";
	private Map<String,List<SpawnBuilder>> spawns = new HashMap<>();
	
	@Override
	public SpawnBuilder SpawnBuilder( @Nullable String name) {
		String entName = (name == null)?UNNAMED:name;
		spawns.computeIfAbsent(entName, tempName -> new ArrayList<SpawnBuilder>() );
		SpawnBuilder sb = new SpawnBuilderImpl();
		spawns.get(entName).add(sb);
		return sb;
	}

	@Override
	public DimensionBuilder create(SpawnBuilder... addedSpawns) {
/*		if( !spawns.containsKey(UNNAMED) ) {
			spawns.put(UNNAMED, new ArrayList<>() );
		}
		spawns.get(UNNAMED).addAll(Arrays.asList(addedSpawns).stream().collect(Collectors.<SpawnBuilder>toList()));
*/		return this;
	}

	@Override
	public ImmutableList<SpawnBuilder> getSpawnByName(String name) {
		if( spawns.containsKey(name) ) {
			return ImmutableList.<SpawnBuilder>copyOf(spawns.get(name));
		}
		return null;
	}

	@Override
	public ImmutableList<SpawnBuilder> getAllSpawns() {
		List<SpawnBuilder> temp = new ArrayList<>();
		spawns.values().forEach(spawn -> temp.addAll(spawn) );
		return ImmutableList.<SpawnBuilder>copyOf(temp);
	}

}
