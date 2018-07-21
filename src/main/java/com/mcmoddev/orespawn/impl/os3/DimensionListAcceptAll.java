package com.mcmoddev.orespawn.impl.os3;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.IDimensionList;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;

public class DimensionListAcceptAll implements IDimensionList {
	@Override
	public boolean matches(final int dimensionID) {
		return true;
	}

	@Override
	public JsonObject serialize() {
		JsonObject rv = new JsonObject();
		rv.add(ConfigNames.BLACKLIST, new JsonArray());
		return rv;
	}
}
