package com.mcmoddev.orespawn.impl.os3;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.os3.FeatureBuilder;
import com.mcmoddev.orespawn.impl.features.DefaultFeatureGenerator;

public class FeatureBuilderImpl implements FeatureBuilder {
	private String featureName;
	private JsonObject parameters;
	private IFeature feature;
	
	public FeatureBuilderImpl() {
		this.featureName = "default";
		this.feature = new DefaultFeatureGenerator();
		this.parameters = new JsonObject();
	}
	
	@Override
	public FeatureBuilder setGenerator(String name) {
		if( OreSpawn.FEATURES.hasFeature(name) ) {
			this.featureName = name;
			this.feature = OreSpawn.FEATURES.getFeature(name);
		}
		return this;
	}

	@Override
	public FeatureBuilder addParameter(String name, boolean value) {
		this.parameters.addProperty(name, value);
		return this;
	}

	@Override
	public FeatureBuilder addParameter(String name, int value) {
		this.parameters.addProperty(name, value);
		return this;
	}

	@Override
	public FeatureBuilder addParameter(String name, float value) {
		this.parameters.addProperty(name, value);
		return this;
	}

	@Override
	public FeatureBuilder addParameter(String name, String value) {
		this.parameters.addProperty(name, value);
		return this;
	}

	@Override 
	public FeatureBuilder setParameters(JsonObject parameters) {
		this.parameters = parameters;
		return this;
	}
	
	@Override
	public FeatureBuilder setDefaultParameters() {
		this.parameters = this.feature.getDefaultParameters();
		return this;
	}

	@Override
	public IFeature getGenerator() {
		return this.feature;
	}

	@Override
	public JsonObject getParameters() {
		return this.parameters;
	}
	
	@Override
	public String getFeatureName() {
		return this.featureName;
	}
}
