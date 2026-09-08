package zone.moddev.mc.orespawn.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;

public final class LangTestFiles {
	private LangTestFiles() { }
	public static JsonObject read(Path path) throws IOException {
		JsonObject result = new JsonObject();
		for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			if (line.trim().isEmpty() || line.startsWith("#")) continue;
			int separator = line.indexOf('=');
			if (separator <= 0) throw new IOException("Malformed .lang row in " + path + ": " + line);
			String key = line.substring(0, separator);
			if (result.has(key)) throw new IOException("Duplicate .lang key in " + path + ": " + key);
			result.addProperty(key, line.substring(separator + 1).replace("\\n", "\n")
					.replace("\\r", "\r").replace("\\\\", "\\"));
		}
		return result;
	}
}
