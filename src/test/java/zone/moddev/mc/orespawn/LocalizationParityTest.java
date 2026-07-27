package zone.moddev.mc.orespawn;

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
import java.util.Locale;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class LocalizationParityTest {
	private static final Path LANG_DIR = Paths.get("src", "main", "resources", "assets", "orespawn", "lang");
	private static final Set<String> REQUIRED_LOCALES = new HashSet<>(Arrays.asList(
			"pt_br.json", "ru_ru.json", "ko_kr.json", "ja_jp.json"));
	private static final String[] MOJIBAKE_MARKERS = {
			"\u00c3", "\u00c2", "\u00e2\u20ac", "\u00d0", "\u00d1",
			"\u00e3\u0192", "\u00ea\u00b4", "\u00ec\u201a", "\u00e7\u0178"
	};
	private static final String[] OLD_BRAND_MARKERS = {
			"mineralog", "min\u00e9ralog", "\u043c\u0438\u043d\u0435\u0440\u0430\u043b\u043e\u0433",
			"\uad11\ubb3c\ud559", "\u9271\u7269\u5b66", "\u77ff\u7269\u5b66", "\u7926\u7269\u5b78"
	};

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
					String value = translated.getAsString();
					for (String marker : MOJIBAKE_MARKERS) {
						assertFalse(value.contains(marker), locale + " contains broken UTF-8 text for " + key);
					}
					String lower = value.toLowerCase(Locale.ROOT);
					for (String marker : OLD_BRAND_MARKERS) {
						assertFalse(lower.contains(marker), locale + " still names Mineralogy for " + key);
					}
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
