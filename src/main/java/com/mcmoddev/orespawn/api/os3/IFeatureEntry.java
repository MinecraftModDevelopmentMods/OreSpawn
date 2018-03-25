package com.mcmoddev.orespawn.api.os3;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.IFeature;

public interface IFeatureEntry {
	public IFeature getFeature();
	public String getFeatureName();
	public JsonObject getFeatureParameters();
	public void setParameter(final String parameterName, final String parameterValue);
	public void setParameter(final String parameterName, final int parameterValue);
	public void setParameter(final String parameterName, final boolean parameterValue);
	public void setParameter(final String parameterName, final float parameterValue);
}
