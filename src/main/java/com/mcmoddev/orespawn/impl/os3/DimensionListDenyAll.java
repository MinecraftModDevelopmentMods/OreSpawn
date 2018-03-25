package com.mcmoddev.orespawn.impl.os3;

import com.mcmoddev.orespawn.api.DimensionList;

public class DimensionListDenyAll implements DimensionList {
	@Override
	@SuppressWarnings("unused")
	public boolean matches(final int dimensionID) {
		return false;
	}
}
