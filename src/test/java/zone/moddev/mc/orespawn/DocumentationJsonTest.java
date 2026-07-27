package zone.moddev.mc.orespawn;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class DocumentationJsonTest {
	private static final Path DOCS = Paths.get("docs");

	@Test
	void allSchemasAndExamplesAreValidJsonObjects() throws Exception {
		try (java.util.stream.Stream<Path> files = Files.walk(DOCS)) {
			for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".json"))::iterator) {
				assertTrue(read(file).isJsonObject(), file + " must contain a JSON object");
			}
		}
	}

	@Test
	void examplesValidateAgainstTheirPublishedSchemas() {
		assertValid("orespawn-global.schema.json", "orespawn-global.json");
		assertValid("orespawn-world.schema.json", "orespawn-world.json");
		assertValid("orespawn-provider.schema.json", "examplemod-orespawn.json");
	}

	private static void assertValid(String schemaName, String exampleName) {
		assertDoesNotThrow(() -> {
			Document schema = document(DOCS.resolve("schemas").resolve(schemaName));
			validate(schema.root, read(DOCS.resolve("examples").resolve(exampleName)), schema, "$", false);
		});
	}

	private static void validate(JsonObject schema, JsonElement value, Document document,
			String path, boolean probe) throws Exception {
		if (schema.has("$ref")) {
			Reference reference = resolve(document, schema.get("$ref").getAsString());
			validate(reference.schema, value, reference.document, path, probe);
			return;
		}
		if (schema.has("allOf")) {
			for (JsonElement child : schema.getAsJsonArray("allOf")) {
				validate(child.getAsJsonObject(), value, document, path, probe);
			}
		}
		if (schema.has("anyOf")) {
			boolean matched = false;
			for (JsonElement child : schema.getAsJsonArray("anyOf")) {
				try {
					validate(child.getAsJsonObject(), value, document, path, true);
					matched = true;
					break;
				} catch (ValidationFailure ignored) { }
			}
			require(matched, path + " does not match any allowed schema");
		}
		if (schema.has("if") && matches(schema.getAsJsonObject("if"), value, document, path)
				&& schema.has("then")) {
			validate(schema.getAsJsonObject("then"), value, document, path, probe);
		}

		if (schema.has("const")) require(schema.get("const").equals(value), path + " has wrong constant");
		if (schema.has("enum")) {
			boolean found = false;
			for (JsonElement allowed : schema.getAsJsonArray("enum")) found |= allowed.equals(value);
			require(found, path + " is outside enum");
		}
		if (schema.has("type")) validateType(schema.get("type").getAsString(), value, path);

		if (value.isJsonObject()) validateObject(schema, value.getAsJsonObject(), document, path, probe);
		if (value.isJsonArray()) validateArray(schema, value.getAsJsonArray(), document, path, probe);
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
			double number = value.getAsDouble();
			if (schema.has("minimum")) require(number >= schema.get("minimum").getAsDouble(), path + " below minimum");
			if (schema.has("maximum")) require(number <= schema.get("maximum").getAsDouble(), path + " above maximum");
		}
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && schema.has("pattern")) {
			require(Pattern.matches(schema.get("pattern").getAsString(), value.getAsString()), path + " has invalid format");
		}
	}

	private static void validateObject(JsonObject schema, JsonObject value, Document document,
			String path, boolean probe) throws Exception {
		if (schema.has("minProperties")) require(value.size() >= schema.get("minProperties").getAsInt(), path + " is empty");
		if (schema.has("required")) {
			for (JsonElement required : schema.getAsJsonArray("required")) {
				require(value.has(required.getAsString()), path + " missing " + required.getAsString());
			}
		}
		if (schema.has("dependentRequired")) {
			for (Map.Entry<String, JsonElement> dependency
					: schema.getAsJsonObject("dependentRequired").entrySet()) {
				if (!value.has(dependency.getKey())) continue;
				for (JsonElement required : dependency.getValue().getAsJsonArray()) {
					require(value.has(required.getAsString()), path + " missing " + required.getAsString());
				}
			}
		}
		JsonObject properties = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
		for (Map.Entry<String, JsonElement> property : properties.entrySet()) {
			if (value.has(property.getKey())) {
				validate(property.getValue().getAsJsonObject(), value.get(property.getKey()), document,
						path + "." + property.getKey(), probe);
			}
		}
		if (schema.has("additionalProperties") && schema.get("additionalProperties").isJsonObject()) {
			JsonObject additional = schema.getAsJsonObject("additionalProperties");
			for (Map.Entry<String, JsonElement> property : value.entrySet()) {
				if (!properties.has(property.getKey())) {
					validate(additional, property.getValue(), document, path + "." + property.getKey(), probe);
				}
			}
		}
		if (schema.has("additionalProperties") && schema.get("additionalProperties").isJsonPrimitive()
				&& !schema.get("additionalProperties").getAsBoolean()) {
			for (String property : value.keySet()) {
				require(properties.has(property), path + " has unsupported property " + property);
			}
		}
	}

	private static void validateArray(JsonObject schema, JsonArray value, Document document,
			String path, boolean probe) throws Exception {
		if (schema.has("uniqueItems") && schema.get("uniqueItems").getAsBoolean()) {
			Set<JsonElement> unique = new HashSet<>();
			for (JsonElement item : value) require(unique.add(item), path + " contains duplicates");
		}
		if (schema.has("items") && schema.get("items").isJsonObject()) {
			for (int i = 0; i < value.size(); i++) {
				validate(schema.getAsJsonObject("items"), value.get(i), document, path + "[" + i + "]", probe);
			}
		}
	}

	private static void validateType(String type, JsonElement value, String path) {
		boolean valid;
		switch (type) {
		case "object": valid = value.isJsonObject(); break;
		case "array": valid = value.isJsonArray(); break;
		case "string": valid = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(); break;
		case "boolean": valid = value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean(); break;
		case "number": valid = value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(); break;
		case "integer": valid = value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
				&& Math.rint(value.getAsDouble()) == value.getAsDouble(); break;
		default: valid = true;
		}
		require(valid, path + " is not a " + type);
	}

	private static boolean matches(JsonObject schema, JsonElement value, Document document, String path) throws Exception {
		try {
			validate(schema, value, document, path, true);
			return true;
		} catch (ValidationFailure ignored) {
			return false;
		}
	}

	private static Reference resolve(Document current, String reference) throws Exception {
		String[] parts = reference.split("#", 2);
		Document target = parts[0].isEmpty() ? current : document(DOCS.resolve("schemas").resolve(parts[0]));
		JsonElement value = target.root;
		if (parts.length == 2 && !parts[1].isEmpty()) {
			for (String token : parts[1].substring(1).split("/")) {
				value = value.getAsJsonObject().get(token.replace("~1", "/").replace("~0", "~"));
			}
		}
		return new Reference(target, value.getAsJsonObject());
	}

	private static Document document(Path path) throws Exception {
		return new Document(path, read(path).getAsJsonObject());
	}

	private static JsonElement read(Path path) throws Exception {
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return new JsonParser().parse(reader);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) throw new ValidationFailure(message);
	}

	private static final class Document {
		final Path path;
		final JsonObject root;
		Document(Path path, JsonObject root) { this.path = path; this.root = root; }
	}

	private static final class Reference {
		final Document document;
		final JsonObject schema;
		Reference(Document document, JsonObject schema) { this.document = document; this.schema = schema; }
	}

	private static final class ValidationFailure extends RuntimeException {
		private static final long serialVersionUID = 1L;
		ValidationFailure(String message) { super(message); }
	}
}
