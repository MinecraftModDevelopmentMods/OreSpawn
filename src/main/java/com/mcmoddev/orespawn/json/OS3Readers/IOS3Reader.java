package com.mcmoddev.orespawn.json.os3Readers;

import com.google.gson.JsonObject;

public interface IOS3Reader {
	void parseJson(JsonObject entries, String fileName);
}
