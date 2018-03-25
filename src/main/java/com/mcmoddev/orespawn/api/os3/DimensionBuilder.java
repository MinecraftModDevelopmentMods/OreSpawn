package com.mcmoddev.orespawn.api.os3;

import com.mcmoddev.orespawn.api.DimensionList;

public interface DimensionBuilder {
	public DimensionBuilder addWhitelistEntry(final int dimensionID);
	public DimensionBuilder addBlacklistEntry(final int dimensionID);
	public DimensionBuilder setAcceptAll();
	public DimensionBuilder setDenyAll();
	public DimensionList create();
}
