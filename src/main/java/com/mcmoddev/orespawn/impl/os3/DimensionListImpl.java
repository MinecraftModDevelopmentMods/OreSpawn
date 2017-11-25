package com.mcmoddev.orespawn.impl.os3;

import com.mcmoddev.orespawn.api.os3.DimensionList;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class DimensionListImpl implements DimensionList {
	private List<Integer> whitelist;
	private List<Integer> blacklist;

	DimensionListImpl() {
		this.whitelist = new LinkedList<> ();
		this.blacklist = new LinkedList<> ();
	}

	@Override
	public boolean match(int dimensionId) {
		return (whitelist.isEmpty() && blacklist.isEmpty() && dimensionId != -1 && dimensionId != 1) ||
		    whitelist.contains(dimensionId) || !blacklist.contains(dimensionId);
	}

	@Override
	public void create(int[] whitelist, int[] blacklist) {
		Arrays.stream(whitelist).map(Integer::new).forEach(this.whitelist::add);
		Arrays.stream(blacklist).map(Integer::new).forEach(this.blacklist::add);
	}
}
