package com.mcmoddev.orespawn.util;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class OS3V2PresetStorage {
	private final Map<String, Map<String, JsonElement>> storage;

	public OS3V2PresetStorage() {
		storage = new TreeMap<>();
	}

	public void setSymbolSection(String sectionName, String itemName, JsonElement value) {
		Map<String, JsonElement> temp = storage.getOrDefault(sectionName, new HashMap<String, JsonElement>());
		temp.put(itemName, value);
		storage.put(sectionName, temp);
	}

	public JsonElement getSymbolSection(String sectionName, String itemName) {
		if (storage.containsKey(sectionName) && storage.get(sectionName).containsKey(itemName)) {
			return storage.get(sectionName).get(itemName);
		} else {
			return new JsonPrimitive(itemName);
		}
	}

	public void copy(OS3V2PresetStorage dest) {
		storage.entrySet().stream()
		.forEach(ensm -> {
			String section = ensm.getKey();
			ensm.getValue().entrySet().forEach(ensje -> dest.setSymbolSection(section, ensje.getKey(), ensje.getValue()));
		});
	}

	public void clear() {
		this.storage.clear();
	}
}
