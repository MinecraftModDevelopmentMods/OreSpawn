package com.mcmoddev.orespawn.impl.location;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;

import net.minecraft.world.biome.Biome;

public class BiomeLocationAcceptAny implements BiomeLocation {

    @Override
    public boolean matches(final Biome biome) {
        return true;
    }

    @Override
    public JsonElement serialize() {
        final JsonObject rv = new JsonObject();
        rv.add(ConfigNames.BLACKLIST, new JsonArray());
        return rv;
    }

}
