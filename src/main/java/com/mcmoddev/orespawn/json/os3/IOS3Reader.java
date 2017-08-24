package com.mcmoddev.orespawn.json.os3;

import com.google.gson.JsonObject;

public interface IOS3Reader {
	void parseJson(JsonObject entries, String fileName);
}
