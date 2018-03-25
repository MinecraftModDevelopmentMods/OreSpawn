package com.mcmoddev.orespawn.impl.os3;

import com.mcmoddev.orespawn.api.DimensionList;

public class DimensionListAcceptAll implements DimensionList {
	@Override
	@SuppressWarnings("unused")
	public boolean matches(final int dimensionID) {
		return true;
	}
}
