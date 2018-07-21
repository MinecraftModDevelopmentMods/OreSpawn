package com.mcmoddev.orespawn.impl.os3;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.IDimensionList;
import com.mcmoddev.orespawn.data.Constants;

public class DimensionListDenyAll implements IDimensionList {
	@Override
	public boolean matches(final int dimensionID) {
		return false;
	}
	
	@Override
	public JsonObject serialize() {
		JsonObject rv = new JsonObject();
		rv.add(Constants.ConfigNames.WHITELIST, new JsonArray());

		return rv;
	}

}
