package com.mcmoddev.orespawn.api.os3;

import com.mcmoddev.orespawn.api.IDimensionList;

public interface IDimensionBuilder {

	IDimensionBuilder addWhitelistEntry(int dimensionID);

	IDimensionBuilder addBlacklistEntry(int dimensionID);

	IDimensionBuilder setAcceptAll();

	IDimensionBuilder setDenyAll();

	IDimensionBuilder setAcceptAllOverworld();

	IDimensionList create();
}
