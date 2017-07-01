package com.mcmoddev.orespawn.api.os3;

public interface FeatureBuilder {
	FeatureBuilder setGenerator(String name);
	FeatureBuilder addParameter(String name, boolean value);
	FeatureBuilder addParameter(String name, int value);
	FeatureBuilder addParameter(String name, float value);
	FeatureBuilder addParameter(String name, String value);
	FeatureData create();
}
