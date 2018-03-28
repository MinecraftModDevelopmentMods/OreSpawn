package com.mcmoddev.orespawn.impl.os3;

import java.util.List;

import com.mcmoddev.orespawn.OreSpawn;

import java.util.ArrayList;

public class DimensionList implements com.mcmoddev.orespawn.api.IDimensionList {
	private final List<Integer> whitelist = new ArrayList<>();
	private final List<Integer> blacklist = new ArrayList<>();
	
	public DimensionList(List<Integer> whitelist, List<Integer> blacklist) {
		this.whitelist.addAll(whitelist);
		this.blacklist.addAll(blacklist);
	}
	
	@Override
	public boolean matches(final int dimensionID) {
		OreSpawn.LOGGER.fatal("Dimension Whitelist:");
		whitelist.stream().forEach(id -> OreSpawn.LOGGER.fatal("Dimension %d", id));
		OreSpawn.LOGGER.fatal("Dimension Blacklist:");
		blacklist.stream().forEach(id -> OreSpawn.LOGGER.fatal("Dimension %d", id));
		
		OreSpawn.LOGGER.fatal("Test ID is %d", dimensionID);
		
		if (this.whitelist.contains(Integer.valueOf(dimensionID))) return true;
		if (this.blacklist.contains(Integer.valueOf(dimensionID))) return false;
		if (this.whitelist.size() > 0) return false;
		if (this.blacklist.size() > 0) return true;
		
		OreSpawn.LOGGER.fatal("Reached a point we should not be able to reach");
		
		// if it gets here, the whitelist and blacklist are empty...
		// ***THAT*** should have resulted in a DimensionListAcceptAll being created, but...
		return true;
	}
}
