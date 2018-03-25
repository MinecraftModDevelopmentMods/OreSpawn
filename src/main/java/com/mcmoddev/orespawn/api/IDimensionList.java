package com.mcmoddev.orespawn.api;

public interface IDimensionList {
	default public boolean matches(final int dimensionId) {
		return false;
	}
}
