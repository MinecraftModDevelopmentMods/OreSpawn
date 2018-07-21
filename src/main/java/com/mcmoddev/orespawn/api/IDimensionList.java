package com.mcmoddev.orespawn.api;

import com.google.gson.JsonObject;

public interface IDimensionList {

	public JsonObject serialize();

	public default boolean matches(final int dimensionId) {
		return false;
	}
}
