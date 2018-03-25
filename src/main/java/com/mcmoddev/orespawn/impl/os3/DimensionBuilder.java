package com.mcmoddev.orespawn.impl.os3;

import java.util.LinkedList;
import java.util.List;

public class DimensionBuilder implements com.mcmoddev.orespawn.api.os3.DimensionBuilder {
	private final List<Integer> dimensionWhitelist = new LinkedList<>();
	private final List<Integer> dimensionBlacklist = new LinkedList<>();
	private boolean acceptAll = true;
	private boolean denyAll = false;
	
	public DimensionBuilder() {		
	}
	
	@Override
	public com.mcmoddev.orespawn.api.os3.DimensionBuilder addWhitelistEntry(int dimensionID) {
		this.dimensionWhitelist.add(dimensionID);
		return this;
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.DimensionBuilder addBlacklistEntry(int dimensionID) {
		this.dimensionBlacklist.add(dimensionID);
		return this;
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.DimensionBuilder setAcceptAll() {
		if (this.denyAll) this.denyAll = false;
		this.acceptAll = true;
		return this;
	}

	@Override
	public com.mcmoddev.orespawn.api.os3.DimensionBuilder setDenyAll() {
		if (this.acceptAll) this.acceptAll = false;
		this.denyAll = true;
		return this;
	}

	@Override
	public com.mcmoddev.orespawn.api.DimensionList create() {
		if (this.acceptAll ||
				((this.dimensionWhitelist.size() == 0) &&
						(this.dimensionBlacklist.size() == 0))) {
			return new DimensionListAcceptAll();
		} else if (this.denyAll) {
			return new DimensionListDenyAll();
		} else {
			return new DimensionList(this.dimensionWhitelist, this.dimensionBlacklist);
		}
	}

}
