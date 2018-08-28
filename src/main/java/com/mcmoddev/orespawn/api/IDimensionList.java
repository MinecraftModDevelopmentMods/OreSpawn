package com.mcmoddev.orespawn.api;

import com.google.gson.JsonObject;

public interface IDimensionList {

    JsonObject serialize();

    default boolean matches(int dimensionId) {
        return false;
    }
}
