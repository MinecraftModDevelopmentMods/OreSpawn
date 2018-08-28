package com.mcmoddev.orespawn.impl.os3;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.data.Constants;

public class DimensionList implements com.mcmoddev.orespawn.api.IDimensionList {

	private final List<Integer> whitelist = new ArrayList<>();
	private final List<Integer> blacklist = new ArrayList<>();

	public DimensionList(final List<Integer> whitelist, final List<Integer> blacklist) {
		this.whitelist.addAll(whitelist);
		this.blacklist.addAll(blacklist);
	}

	@Override
	public boolean matches(final int dimensionID) {
		if (this.whitelist.contains(Integer.valueOf(dimensionID))) {
			return true;
		}
		if (this.blacklist.contains(Integer.valueOf(dimensionID))) {
			return false;
		}
		if (!this.whitelist.isEmpty()) {
			return false;
		}
		if (!this.blacklist.isEmpty()) {
			return true;
		}

		// if it gets here, the whitelist and blacklist are empty...
		// ***THAT*** should have resulted in a DimensionListAcceptAll being created, but...
		return true;
	}

	@Override
	public JsonObject serialize() {
		final JsonObject rv = new JsonObject();
		if (!this.whitelist.isEmpty()) {
			final JsonArray wl = new JsonArray();
			whitelist.stream().forEach(wl::add);
			rv.add(Constants.ConfigNames.WHITELIST, wl);
		}

		if (!this.blacklist.isEmpty()) {
			final JsonArray bl = new JsonArray();
			blacklist.stream().forEach(bl::add);
			rv.add(Constants.ConfigNames.WHITELIST, bl);
		} else if (this.whitelist.isEmpty()) {
			return new DimensionListAcceptAllOverworld().serialize();
		}

		return rv;
	}
}
