package com.mcmoddev.orespawn.api.os3;

import com.mcmoddev.orespawn.api.IDimensionList;

public interface IDimensionBuilder {
	public IDimensionBuilder addWhitelistEntry(final int dimensionID);
	public IDimensionBuilder addBlacklistEntry(final int dimensionID);
	public IDimensionBuilder setAcceptAll();
	public IDimensionBuilder setDenyAll();
	public IDimensionBuilder setAcceptAllOverworld();
	public IDimensionList create();
}
