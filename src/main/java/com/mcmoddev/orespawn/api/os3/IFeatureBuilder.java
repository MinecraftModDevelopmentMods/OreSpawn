package com.mcmoddev.orespawn.api.os3;

import com.google.gson.JsonElement;
import com.mcmoddev.orespawn.api.IFeature;

import net.minecraft.util.ResourceLocation;

public interface IFeatureBuilder {

	IFeatureBuilder setFeature(String featureName);

	IFeatureBuilder setFeature(ResourceLocation featureResourceLocation);

	IFeatureBuilder setFeature(IFeature feature);

	IFeatureBuilder setParameter(String parameterName, String parameterValue);

	IFeatureBuilder setParameter(String parameterName, int parameterValue);

	IFeatureBuilder setParameter(String parameterName, float parameterValue);

	IFeatureBuilder setParameter(String parameterName, boolean parameterValue);

	IFeatureBuilder setParameter(String parameterName,
			JsonElement parameterValue);

	IFeatureBuilder setUseFeatureDefaults();

	IFeatureEntry create();
}
