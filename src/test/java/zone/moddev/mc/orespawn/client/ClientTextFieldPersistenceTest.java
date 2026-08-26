package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.text.StringTextComponent;

class ClientTextFieldPersistenceTest {
	private static final Path CLIENT_SOURCE = Paths.get(
			"src", "main", "java", "zone", "moddev", "mc", "orespawn", "client");
	private static final Pattern VALUE_BEFORE_MAX_LENGTH = Pattern.compile(
			"(?s)\\b([A-Za-z_$][A-Za-z0-9_$]*)\\.setValue\\([^;]*;"
			+ "\\s*\\1\\.setMaxLength\\(");

	@Test
	void everyTextFieldSetsItsMaximumBeforeLoadingSavedText() throws Exception {
		List<String> unsafe = new ArrayList<>();
		try (Stream<Path> files = Files.list(CLIENT_SOURCE)) {
			for (Path source : (Iterable<Path>) files
					.filter(path -> path.getFileName().toString().endsWith(".java"))::iterator) {
				String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
				if (VALUE_BEFORE_MAX_LENGTH.matcher(text).find()) {
					unsafe.add(source.getFileName().toString());
				}
			}
		}

		assertTrue(unsafe.isEmpty(),
				"Text fields must set their maximum length before loading saved text: " + unsafe);
	}

	@Test
	void targetTextFieldRetainsAValueLongerThanTheVanillaDefault() {
		String value = "minecraft:stone,minecraft:granite,minecraft:diorite,minecraft:andesite";
		TextFieldWidget field = new TextFieldWidget(null, 0, 0, 200, 20,
				new StringTextComponent("host_blocks"));

		field.setMaxLength(1024);
		field.setValue(value);

		assertEquals(value, field.getValue());
	}
}
