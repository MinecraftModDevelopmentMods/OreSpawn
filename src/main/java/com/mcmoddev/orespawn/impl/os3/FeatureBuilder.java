package com.mcmoddev.orespawn.impl.os3;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.os3.IFeatureBuilder;
import com.mcmoddev.orespawn.api.os3.IFeatureEntry;
import com.mcmoddev.orespawn.data.FeatureRegistry;

import net.minecraft.util.ResourceLocation;

public class FeatureBuilder implements IFeatureBuilder {
	private IFeature feature;
	private JsonObject parameters;
	private boolean useDefaults;

	public FeatureBuilder() {
		this.useDefaults = false;
		this.parameters = new JsonObject();
	}
	
	@Override
	public IFeatureBuilder setFeature(String featureName) {
		return this.setFeature(new ResourceLocation(featureName));
	}

	@Override
	public IFeatureBuilder setFeature(ResourceLocation featureResourceLocation) {
		if (!OreSpawn.API.featureExists(featureResourceLocation)) {
			OreSpawn.LOGGER.warn("Feature %s is not known, feature for this will be set to the default feature", featureResourceLocation.getResourcePath());
		}
		return this.setFeature(OreSpawn.API.getFeature(featureResourceLocation));
	}

	@Override
	public IFeatureBuilder setFeature(IFeature feature) {
		this.feature = feature;
		return this;
	}

	@Override
	public IFeatureBuilder setParameter(String parameterName, String parameterValue) {
		this.parameters.addProperty(parameterName, parameterValue);
		return this;
	}

	@Override
	public IFeatureBuilder setParameter(String parameterName, int parameterValue) {
		this.parameters.addProperty(parameterName, parameterValue);
		return this;
	}

	@Override
	public IFeatureBuilder setParameter(String parameterName, float parameterValue) {
		this.parameters.addProperty(parameterName, parameterValue);
		return this;
	}

	@Override
	public IFeatureBuilder setParameter(String parameterName, boolean parameterValue) {
		this.parameters.addProperty(parameterName, parameterValue);
		return this;
	}

	@Override
	public IFeatureBuilder setParameter(String parameterName, JsonElement parameterValue) {
		this.parameters.add(parameterName, parameterValue);
		return this;
	}

	private void setFeatureParameter(String parameterName, JsonElement parameterValue, FeatureEntry feat) {
		if (parameterValue.getAsJsonPrimitive().isBoolean()) {
			feat.setParameter(parameterName,parameterValue.getAsBoolean());
		} else if (parameterValue.getAsJsonPrimitive().isString()) {
			feat.setParameter(parameterName, parameterValue.getAsString());
		} else {
			float paramAsFloat = parameterValue.getAsFloat();
			if ( (paramAsFloat - Math.floor(paramAsFloat)) > 0 ) {
				feat.setParameter(parameterName, parameterValue.getAsFloat());
			} else {
				feat.setParameter(parameterName, parameterValue.getAsInt());
			}
		}
	}
	
	@Override
	public IFeatureBuilder setUseFeatureDefaults() {
		this.useDefaults = true;
		return this;
	}

	@Override
	public IFeatureEntry create() {
		FeatureEntry res = new FeatureEntry(this.feature);
		if (!this.useDefaults) {
			// only copy in the parameters we need
			this.feature.getDefaultParameters().entrySet().stream()
			.filter(ent -> !this.parameters.has(ent.getKey()))
			.forEach(ent -> this.parameters.add(ent.getKey(), ent.getValue()));
		} else {
			// overwrite - they've said to just use the defaults
			this.feature.getDefaultParameters().entrySet().stream()
			.forEach(ent -> this.parameters.add(ent.getKey(), ent.getValue()));
		}
		
		this.parameters.entrySet().stream()
		.forEach(ent -> this.setFeatureParameter(ent.getKey(), ent.getValue(), res));
		
		return res;
	}

}
