package com.mcmoddev.orespawn.api.os3;

import javax.annotation.Nonnull;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.IFeature;

public interface FeatureBuilder {
	FeatureBuilder setGenerator(@Nonnull String name);
	FeatureBuilder addParameter(@Nonnull String name, @Nonnull boolean value);
	FeatureBuilder addParameter(@Nonnull String name, @Nonnull int value);
	FeatureBuilder addParameter(@Nonnull String name, @Nonnull float value);
	FeatureBuilder addParameter(@Nonnull String name, @Nonnull String value);
	FeatureBuilder setParameters(@Nonnull JsonObject parameters);
	FeatureBuilder setDefaultParameters();
	
	IFeature getGenerator();
	JsonObject getParameters();
	String getFeatureName();
}
