package com.mcmoddev.orespawn.api.os3;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.IFeature;

public interface IFeatureEntry {

    IFeature getFeature();

    String getFeatureName();

    JsonObject getFeatureParameters();

    void setParameter(String parameterName, String parameterValue);

    void setParameter(String parameterName, int parameterValue);

    void setParameter(String parameterName, boolean parameterValue);

    void setParameter(String parameterName, float parameterValue);
}
