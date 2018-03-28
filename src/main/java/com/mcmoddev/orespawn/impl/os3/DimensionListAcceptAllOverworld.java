package com.mcmoddev.orespawn.impl.os3;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.IDimensionList;
import com.mcmoddev.orespawn.data.Constants;

public class DimensionListAcceptAllOverworld implements IDimensionList {
	@Override
	public boolean matches(final int dimensionID) {
		return dimensionID != -1 && dimensionID != 1;
	}

	@Override
	public JsonObject serialize() {
		JsonObject rv = new JsonObject();
		JsonArray bl = new JsonArray();
		bl.add(-1);
		bl.add(1);
		rv.add(Constants.ConfigNames.BLACKLIST, bl);

		return rv;
	}

}
