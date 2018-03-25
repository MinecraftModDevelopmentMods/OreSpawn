package com.mcmoddev.orespawn.api;
import com.google.gson.JsonObject;

public interface FeatureEntry {
	public IFeature getFeature();
	public String getFeatureName();
	public JsonObject getFeatureParameters();
	public void setStringParameter(final String parameterName, final String parameterValue);
	public void setIntegerParameter(final String parameterName, final int parameterValue);
	public void setBooleanParameter(final String parameterName, final boolean parameterValue);
	public void setFloatParameter(final String parameterName, final float parameterValue);
}
