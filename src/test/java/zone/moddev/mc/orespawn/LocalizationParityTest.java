package zone.moddev.mc.orespawn;

import zone.moddev.mc.orespawn.util.JsonCopies;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

class LocalizationParityTest {
	private static final Path LANG_DIR = Paths.get("src", "main", "resources", "assets", "orespawn", "lang");
	private static final Set<String> EXPECTED_LOCALES = ImmutableSet.of(
			"de_au.lang", "de_de.lang", "en_ca.lang", "en_en.lang",
			"en_gb.lang", "en_pt.lang", "en_us.lang", "es_es.lang",
			"es_mx.lang", "fr_ca.lang", "fr_fr.lang", "ja_jp.lang",
			"ko_kr.lang", "pt_br.lang", "ru_ru.lang", "zh_cn.lang");
	/**
	 * Brand names, format-only values, canonical engine/pattern names, and words
	 * whose spelling is already valid in at least one shipped target language.
	 * Human-facing prose must never be added here merely to make this test pass.
	 */
	private static final Map<String, Set<String>> INTENTIONAL_ENGLISH_VALUES =
			ImmutableMap.<String, Set<String>>builder()
			.put("button.orespawn.world_settings", ImmutableSet.of(
					"de_au.lang", "de_de.lang", "es_es.lang", "es_mx.lang", "fr_ca.lang",
					"fr_fr.lang", "ja_jp.lang", "ko_kr.lang", "pt_br.lang", "ru_ru.lang",
					"zh_cn.lang"))
			.put("button.orespawn.biome_available", ImmutableSet.of(
					"de_au.lang", "de_de.lang", "es_es.lang", "es_mx.lang", "fr_ca.lang",
					"fr_fr.lang", "ja_jp.lang", "ko_kr.lang", "pt_br.lang", "ru_ru.lang",
					"zh_cn.lang"))
			.put("option.orespawn.mod_filter", ImmutableSet.of(
					"de_au.lang", "de_de.lang", "fr_ca.lang", "fr_fr.lang"))
			.put("tab.orespawn.biomes", ImmutableSet.of("fr_ca.lang", "fr_fr.lang"))
			.put("tab.orespawn.geomes", ImmutableSet.of("de_au.lang", "de_de.lang"))
			.put("tab.orespawn.placement", ImmutableSet.of("fr_ca.lang", "fr_fr.lang"))
			.put("tab.orespawn.biome_placement", ImmutableSet.of("fr_ca.lang", "fr_fr.lang"))
			.put("tab.orespawn.biome_surface", ImmutableSet.of("fr_ca.lang", "fr_fr.lang"))
			.put("guide.orespawn.biomes.title", ImmutableSet.of("fr_ca.lang", "fr_fr.lang"))
			.put("value.orespawn.geology_mode.geome", ImmutableSet.of(
					"es_es.lang", "es_mx.lang", "fr_ca.lang", "fr_fr.lang", "ja_jp.lang",
					"ko_kr.lang", "pt_br.lang", "ru_ru.lang", "zh_cn.lang"))
			.put("value.orespawn.geology_mode.legacy", ImmutableSet.of("de_au.lang", "de_de.lang"))
			.put("value.orespawn.height_distribution.uniform", ImmutableSet.of("de_au.lang", "de_de.lang"))
			.put("value.orespawn.height_distribution.triangle", ImmutableSet.of("pt_br.lang"))
			.put("value.orespawn.ore_pattern.default", ImmutableSet.of(
					"de_au.lang", "de_de.lang", "es_es.lang", "es_mx.lang", "fr_ca.lang",
					"fr_fr.lang", "ja_jp.lang", "ko_kr.lang", "pt_br.lang", "ru_ru.lang",
					"zh_cn.lang"))
			.put("value.orespawn.ore_pattern.precision", ImmutableSet.of(
					"de_au.lang", "de_de.lang", "es_es.lang", "es_mx.lang", "fr_ca.lang",
					"fr_fr.lang", "ja_jp.lang", "ko_kr.lang", "pt_br.lang", "ru_ru.lang",
					"zh_cn.lang"))
			.put("value.orespawn.ore_pattern.underfluids", ImmutableSet.of(
					"de_au.lang", "de_de.lang", "es_es.lang", "es_mx.lang", "fr_ca.lang",
					"fr_fr.lang", "ja_jp.lang", "ko_kr.lang", "pt_br.lang", "ru_ru.lang",
					"zh_cn.lang"))
			.build();
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
		JsonObject english = read(LANG_DIR.resolve("en_us.lang"));
		Set<String> englishKeys = JsonCopies.keys(english);
		assertEquals(357, englishKeys.size(),
				"The target locale contract changed; review every shipped translation");
		Set<String> localeFiles = new HashSet<>();
		Set<String> observedIntentionalEnglishValues = new HashSet<>();

		try (java.util.stream.Stream<Path> files = Files.list(LANG_DIR)) {
			for (Path file : (Iterable<Path>) files
					.filter(path -> path.getFileName().toString().endsWith(".lang"))
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
							.getOrDefault(key, ImmutableSet.of());
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
		byte[] bytes = Files.readAllBytes(path);
		assertFalse(bytes.length >= 3 && (bytes[0] & 0xff) == 0xef
				&& (bytes[1] & 0xff) == 0xbb && (bytes[2] & 0xff) == 0xbf,
				path.getFileName() + " must be UTF-8 without a BOM");
		JsonObject result = new JsonObject();
		for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			if (line.trim().isEmpty() || line.startsWith("#")) continue;
			int separator = line.indexOf('=');
			assertTrue(separator > 0, path.getFileName() + " has a malformed .lang row: " + line);
			String key = line.substring(0, separator);
			assertFalse(result.has(key), path.getFileName() + " repeats key " + key);
			String value = line.substring(separator + 1).replace("\\n", "\n")
					.replace("\\r", "\r").replace("\\\\", "\\");
			result.addProperty(key, value);
		}
		return result;
	}
}
