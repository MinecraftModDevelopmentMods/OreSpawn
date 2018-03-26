package com.mcmoddev.orespawn.impl.os3;

import com.mcmoddev.orespawn.api.IDimensionList;

public class DimensionListAcceptAllOverworld implements IDimensionList {
	@Override
	public boolean matches(final int dimensionID) {
		return dimensionID != -1 && dimensionID != 1;
	}

}
