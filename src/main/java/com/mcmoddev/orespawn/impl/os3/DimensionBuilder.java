package com.mcmoddev.orespawn.impl.os3;

import java.util.LinkedList;
import java.util.List;

import com.mcmoddev.orespawn.api.IDimensionList;
import com.mcmoddev.orespawn.api.os3.IDimensionBuilder;

public class DimensionBuilder implements IDimensionBuilder {

	private final List<Integer>	dimensionWhitelist	= new LinkedList<>();
	private final List<Integer>	dimensionBlacklist	= new LinkedList<>();
	private boolean				acceptAll			= false;
	private boolean				denyAll				= false;
	private boolean				acceptAllOverworld	= true;

	public DimensionBuilder() {
		//
	}

	@Override
	public IDimensionBuilder addWhitelistEntry(final int dimensionID) {
		this.acceptAllOverworld = false;
		this.dimensionWhitelist.add(dimensionID);
		return this;
	}

	@Override
	public IDimensionBuilder addBlacklistEntry(final int dimensionID) {
		this.acceptAllOverworld = false;
		this.dimensionBlacklist.add(dimensionID);
		return this;
	}

	@Override
	public IDimensionBuilder setAcceptAll() {
		if (this.denyAll) {
			this.denyAll = false;
		}
		this.acceptAll = true;
		return this;
	}

	@Override
	public IDimensionBuilder setDenyAll() {
		if (this.acceptAll) {
			this.acceptAll = false;
		}
		this.denyAll = true;
		return this;
	}

	@Override
	public IDimensionList create() {
		if (this.acceptAll
				|| ((this.dimensionWhitelist.isEmpty()) && (this.dimensionBlacklist.isEmpty()))
						&& !(this.acceptAllOverworld)) {
			return new DimensionListAcceptAll();
		} else if (this.denyAll) {
			return new DimensionListDenyAll();
		} else if (this.acceptAllOverworld) {
			return new DimensionListAcceptAllOverworld();
		} else {
			return new DimensionList(this.dimensionWhitelist, this.dimensionBlacklist);
		}
	}

	@Override
	public IDimensionBuilder setAcceptAllOverworld() {
		this.acceptAll = false;
		this.denyAll = false;
		this.acceptAllOverworld = true;
		return this;
	}

}
