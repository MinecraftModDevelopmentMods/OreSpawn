package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import net.minecraft.util.text.StringTextComponent;

class ClientButtonTextTest {
	private static final Path CLIENT_SOURCE = Paths.get(
			"src", "main", "java", "zone", "moddev", "mc", "orespawn", "client");
	private static final Path ENGLISH = Paths.get(
			"src", "main", "resources", "assets", "orespawn", "lang", "en_us.json");
	private static final Pattern LITERAL_TRANSLATION = Pattern.compile(
			"new\\s+TranslationTextComponent\\(\\s*\\\"([^\\\"]+)\\\"\\s*[,)]");
	private static final Set<String> MINECRAFT_1_15_KEYS = new HashSet<>(Arrays.asList(
			"gui.cancel", "gui.done", "options.off", "options.on"));

	@Test
	void cycleButtonsUseTheMinecraft115LabelConvention() {
		CycleButton<String> button = CycleButton.builder(StringTextComponent::new)
				.withValues(Arrays.asList("Value"))
				.withInitialValue("Value")
				.create(0, 0, 100, 20, new StringTextComponent("Label"),
						(ignored, value) -> { });

		assertEquals("Label: Value", button.getMessage());
	}

	@Test
	void everyLiteralClientTranslationKeyExistsOnTheTarget() throws Exception {
		JsonObject english = new JsonParser().parse(new String(
				Files.readAllBytes(ENGLISH), StandardCharsets.UTF_8)).getAsJsonObject();
		List<String> missing = new ArrayList<>();
		try (Stream<Path> files = Files.list(CLIENT_SOURCE)) {
			for (Path source : (Iterable<Path>) files
					.filter(path -> path.getFileName().toString().endsWith(".java"))::iterator) {
				String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
				Matcher matcher = LITERAL_TRANSLATION.matcher(text);
				while (matcher.find()) {
					String key = matcher.group(1);
					if (!english.has(key) && !MINECRAFT_1_15_KEYS.contains(key)) {
						missing.add(source.getFileName() + ": " + key);
					}
				}
			}
		}

		assertTrue(missing.isEmpty(),
				"Client labels must exist in OreSpawn or Minecraft 1.15: " + missing);
	}

}
