package com.mcmoddev.orespawn.impl.os3;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.os3.IFeatureEntry;

public class FeatureEntry implements IFeatureEntry {
	private final IFeature feature;
	private final JsonObject parameters;
	
	public FeatureEntry(IFeature feature) {
		this.feature = feature;
		this.parameters = new JsonObject();
	}
	
	@Override
	public IFeature getFeature() {
		return this.feature;
	}

	@Override
	public String getFeatureName() {
		return this.feature.getRegistryName().getPath();
	}

	@Override
	public JsonObject getFeatureParameters() {
		JsonObject defs = feature.getDefaultParameters();
		this.parameters.entrySet().stream()
		.forEach(ent -> defs.add(ent.getKey(), ent.getValue()));
		return defs;
	}

	@Override
	public void setParameter(String parameterName, String parameterValue) {
		this.parameters.addProperty(parameterName, parameterValue);
	}

	@Override
	public void setParameter(String parameterName, int parameterValue) {
		this.parameters.addProperty(parameterName, parameterValue);
	}

	@Override
	public void setParameter(String parameterName, boolean parameterValue) {
		this.parameters.addProperty(parameterName, parameterValue);
	}

	@Override
	public void setParameter(String parameterName, float parameterValue) {
		this.parameters.addProperty(parameterName, parameterValue);
	}

}
