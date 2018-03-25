package com.mcmoddev.orespawn.impl.os3;

import com.mcmoddev.orespawn.api.IDimensionList;

public class DimensionListDenyAll implements IDimensionList {
	@Override
	@SuppressWarnings("unused")
	public boolean matches(final int dimensionID) {
		return false;
	}
}
