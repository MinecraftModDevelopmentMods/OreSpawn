package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class OreSourceEditorViewTest {
	@Test
	void oldInferredChannelShowsOnceWithoutRewritingTheSavedChoice() {
		JsonObject profile = parse("{\"ores\":{\"example:copper\":{\"dimensions\":"
				+ "{\"minecraft:overworld\":{\"pattern\":\"orespawn:precision\"}}}}}");
		JsonObject saved = parse("{\"domain\":\"minecraft:overworld\","
				+ "\"placement_sources\":{\"orespawn:precision\":\"example:copper\"},"
				+ "\"candidates\":[{\"source_id\":\"example:copper\",\"placement_channel\":\"orespawn:standard\",\"loaded\":true},"
				+ "{\"source_id\":\"example:copper\",\"placement_channel\":\"orespawn:precision\",\"loaded\":false}]}");
		String before = saved.toString();

		JsonObject view = OreSourceEditorView.policy(profile, saved);
		assertEquals(1, view.getAsJsonArray("candidates").size());
		assertTrue(view.getAsJsonObject("placement_sources").has("orespawn:standard"));
		assertFalse(view.getAsJsonObject("placement_sources").has("orespawn:precision"));
		assertEquals(before, saved.toString());
	}

	private static JsonObject parse(String json) {
		return new JsonParser().parse(json).getAsJsonObject();
	}
}
