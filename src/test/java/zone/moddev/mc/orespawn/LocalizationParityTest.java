package zone.moddev.mc.orespawn;

import zone.moddev.mc.orespawn.util.JsonCopies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class LocalizationParityTest {
	private static final Path LANG_DIR = Paths.get("src", "main", "resources", "assets", "orespawn", "lang");
	private static final Set<String> EXPECTED_LOCALES = Set.of(
			"de_au.json", "de_de.json", "en_ca.json", "en_en.json",
			"en_gb.json", "en_pt.json", "en_us.json", "es_es.json",
			"es_mx.json", "fr_ca.json", "fr_fr.json", "ja_jp.json",
			"ko_kr.json", "pt_br.json", "ru_ru.json", "zh_cn.json");
	/**
	 * Brand names, format-only values, canonical engine/pattern names, and words
	 * whose spelling is already valid in at least one shipped target language.
	 * Human-facing prose must never be added here merely to make this test pass.
	 */
	private static final Map<String, Set<String>> INTENTIONAL_ENGLISH_VALUES = Map.ofEntries(
			Map.entry("button.orespawn.world_settings", Set.of(
					"de_au.json", "de_de.json", "es_es.json", "es_mx.json", "fr_ca.json",
					"fr_fr.json", "ja_jp.json", "ko_kr.json", "pt_br.json", "ru_ru.json",
					"zh_cn.json")),
			Map.entry("button.orespawn.biome_available", Set.of(
					"de_au.json", "de_de.json", "es_es.json", "es_mx.json", "fr_ca.json",
					"fr_fr.json", "ja_jp.json", "ko_kr.json", "pt_br.json", "ru_ru.json",
					"zh_cn.json")),
			Map.entry("option.orespawn.mod_filter", Set.of(
					"de_au.json", "de_de.json", "fr_ca.json", "fr_fr.json")),
			Map.entry("tab.orespawn.biomes", Set.of("fr_ca.json", "fr_fr.json")),
			Map.entry("tab.orespawn.geomes", Set.of("de_au.json", "de_de.json")),
			Map.entry("tab.orespawn.placement", Set.of("fr_ca.json", "fr_fr.json")),
			Map.entry("tab.orespawn.biome_placement", Set.of("fr_ca.json", "fr_fr.json")),
			Map.entry("tab.orespawn.biome_surface", Set.of("fr_ca.json", "fr_fr.json")),
			Map.entry("guide.orespawn.biomes.title", Set.of("fr_ca.json", "fr_fr.json")),
			Map.entry("value.orespawn.geology_mode.geome", Set.of(
					"es_es.json", "es_mx.json", "fr_ca.json", "fr_fr.json", "ja_jp.json",
					"ko_kr.json", "pt_br.json", "ru_ru.json", "zh_cn.json")),
			Map.entry("value.orespawn.geology_mode.legacy", Set.of("de_au.json", "de_de.json")),
			Map.entry("value.orespawn.height_distribution.uniform", Set.of("de_au.json", "de_de.json")),
			Map.entry("value.orespawn.height_distribution.triangle", Set.of("pt_br.json")),
			Map.entry("value.orespawn.ore_pattern.default", Set.of(
					"de_au.json", "de_de.json", "es_es.json", "es_mx.json", "fr_ca.json",
					"fr_fr.json", "ja_jp.json", "ko_kr.json", "pt_br.json", "ru_ru.json",
					"zh_cn.json")),
			Map.entry("value.orespawn.ore_pattern.precision", Set.of(
					"de_au.json", "de_de.json", "es_es.json", "es_mx.json", "fr_ca.json",
					"fr_fr.json", "ja_jp.json", "ko_kr.json", "pt_br.json", "ru_ru.json",
					"zh_cn.json")),
			Map.entry("value.orespawn.ore_pattern.underfluids", Set.of(
					"de_au.json", "de_de.json", "es_es.json", "es_mx.json", "fr_ca.json",
					"fr_fr.json", "ja_jp.json", "ko_kr.json", "pt_br.json", "ru_ru.json",
					"zh_cn.json")));
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
		Set<String> englishKeys = JsonCopies.keys(english);
		assertEquals(357, englishKeys.size(),
				"The target locale contract changed; review every shipped translation");
		Set<String> localeFiles = new HashSet<>();
		Set<String> observedIntentionalEnglishValues = new HashSet<>();

		try (java.util.stream.Stream<Path> files = Files.list(LANG_DIR)) {
			for (Path file : (Iterable<Path>) files
					.filter(path -> path.getFileName().toString().endsWith(".json"))
					.sorted()::iterator) {
				String locale = file.getFileName().toString();
				localeFiles.add(locale);
				JsonObject translations = read(file);

				assertEquals(englishKeys, JsonCopies.keys(translations), locale + " must match en_us keys exactly");
				for (String key : englishKeys) {
					JsonElement translated = translations.get(key);
					assertTrue(translated.isJsonPrimitive() && translated.getAsJsonPrimitive().isString(),
							locale + " value for " + key + " must be a string");
					assertFalse(translated.getAsString().trim().isEmpty(), locale + " value for " + key + " is blank");
					assertEquals(formatArgumentCount(english.get(key).getAsString()),
							formatArgumentCount(translated.getAsString()),
							locale + " changes the format arguments for " + key);
					String value = translated.getAsString();
					Set<String> intentionalLocales = INTENTIONAL_ENGLISH_VALUES
							.getOrDefault(key, Set.of());
					if (!locale.startsWith("en_") && !intentionalLocales.contains(locale)) {
						assertFalse(value.equals(english.get(key).getAsString()),
								locale + " still uses the English fallback for " + key);
					}
					if (!locale.startsWith("en_") && value.equals(english.get(key).getAsString())) {
						observedIntentionalEnglishValues.add(key + "\n" + locale);
					}
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

		assertEquals(EXPECTED_LOCALES, localeFiles,
				"The shipped locale set changed; add complete translations and review this guardrail");
		Set<String> expectedIntentionalEnglishValues = new HashSet<>();
		for (Map.Entry<String, Set<String>> entry : INTENTIONAL_ENGLISH_VALUES.entrySet()) {
			for (String locale : entry.getValue()) {
				expectedIntentionalEnglishValues.add(entry.getKey() + "\n" + locale);
			}
		}
		assertEquals(expectedIntentionalEnglishValues, observedIntentionalEnglishValues,
				"The locale-specific English exception list is stale; review and narrow it");
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
			return new JsonParser().parse(reader).getAsJsonObject();
		}
	}
}
