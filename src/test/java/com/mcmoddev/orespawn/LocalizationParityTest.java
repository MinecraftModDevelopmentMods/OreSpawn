package com.mcmoddev.orespawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class LocalizationParityTest {
	private static final Path LANG_DIR = Paths.get("src", "main", "resources", "assets", "orespawn", "lang");
	private static final Set<String> REQUIRED_LOCALES = new HashSet<>(Arrays.asList(
			"pt_br.json", "ru_ru.json", "ko_kr.json", "ja_jp.json"));

	@Test
	void everyLocaleMatchesEnglishKeysAndFormatting() throws Exception {
		JsonObject english = read(LANG_DIR.resolve("en_us.json"));
		Set<String> englishKeys = english.keySet();
		Set<String> localeFiles = new HashSet<>();

		try (java.util.stream.Stream<Path> files = Files.list(LANG_DIR)) {
			for (Path file : (Iterable<Path>) files
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()::iterator) {
				String locale = file.getFileName().toString();
				localeFiles.add(locale);
				JsonObject translations = read(file);

				assertEquals(englishKeys, translations.keySet(), locale + " must match en_us keys exactly");
				for (String key : englishKeys) {
					JsonElement translated = translations.get(key);
					assertTrue(translated.isJsonPrimitive() && translated.getAsJsonPrimitive().isString(),
							locale + " value for " + key + " must be a string");
					assertFalse(translated.getAsString().trim().isEmpty(), locale + " value for " + key + " is blank");
					assertEquals(formatArgumentCount(english.get(key).getAsString()),
							formatArgumentCount(translated.getAsString()),
							locale + " changes the format arguments for " + key);
				}
			}
		}

		assertTrue(localeFiles.containsAll(REQUIRED_LOCALES), "Required new locales are missing");
	}

	private static int formatArgumentCount(String value) {
		int count = 0;
		for (int i = 0; i < value.length() - 1; i++) {
			if (value.charAt(i) == '%' && value.charAt(i + 1) == 's') count++;
		}
		return count;
	}

	private static JsonObject read(Path path) throws Exception {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}
}
