package com.mcmoddev.orespawn.api.os3;

import com.mcmoddev.orespawn.api.IFeature;

import net.minecraft.util.ResourceLocation;

public interface IFeatureBuilder {
	public IFeatureBuilder setFeature(final String featureName);
	public IFeatureBuilder setFeature(final ResourceLocation featureResourceLocation);
	public IFeatureBuilder setFeature(final IFeature feature);
	public IFeatureBuilder setParameter(final String parameterName, final String parameterValue);
	public IFeatureBuilder setParameter(final String parameterName, final int parameterValue);
	public IFeatureBuilder setParameter(final String parameterName, final float parameterValue);
	public IFeatureBuilder setParameter(final String parameterName, final boolean parameterValue);
	public IFeatureBuilder setUseFeatureDefaults();
	public IFeatureEntry create();
}
