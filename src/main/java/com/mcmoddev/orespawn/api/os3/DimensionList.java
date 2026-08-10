package com.mcmoddev.orespawn.api.os3;

public interface DimensionList {
	boolean match(int dimensionId);
	void create(int[] whitelist, int[] blacklist);
}
