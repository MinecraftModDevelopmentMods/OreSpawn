package com.mcmoddev.orespawn.util;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/** Deprecated OS3 3.2 preset container. */
public class OS3V2PresetStorage {
	private final Map<String, Map<String, JsonElement>> storage = new LinkedHashMap<>();
	public void setSymbolSection(String symbol, String section, JsonElement value) {
		storage.computeIfAbsent(symbol, key -> new LinkedHashMap<>()).put(section, new JsonParser().parse(value.toString()));
	}
	public JsonElement getSymbolSection(String symbol, String section) {
		Map<String, JsonElement> values = storage.get(symbol); return values == null ? null : values.get(section);
	}
	public void copy(OS3V2PresetStorage source) { clear(); source.storage.forEach((s, values) -> values.forEach((k, v) -> setSymbolSection(s, k, v))); }
	public void clear() { storage.clear(); }
}
