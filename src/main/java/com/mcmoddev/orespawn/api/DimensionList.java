package com.mcmoddev.orespawn.api;

public interface DimensionList {
	default public boolean matches(final int dimensionId) {
		return false;
	}
}
