package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.util.JsonCopies;

/** Presents old inferred channel names without rewriting the world's saved choices. */
final class OreSourceEditorView {
    private static final String STANDARD = "orespawn:standard";
    private static final Set<String> OLD_CHANNELS = new HashSet<>(Arrays.asList(
            "orespawn:default", "orespawn:vein", "orespawn:normal_cloud",
            "orespawn:precision", "orespawn:clusters", "orespawn:underfluids"));

    private OreSourceEditorView() { }

    static JsonObject policy(JsonObject root, JsonObject saved) {
        JsonObject view = JsonCopies.copy(saved);
        JsonObject placements = object(view, "placement_sources");
        JsonArray candidates = view.has("candidates") && view.get("candidates").isJsonArray()
                ? view.getAsJsonArray("candidates") : new JsonArray();
        String domain = text(view, "domain");
        List<String> aliases = new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry : placements.entrySet()) {
            String channel = entry.getKey();
            if (!OLD_CHANNELS.contains(channel) || !entry.getValue().isJsonPrimitive()) continue;

            String source = entry.getValue().getAsString();
            if (inferredAlias(root, source, domain, channel)
                    && loadedCandidate(candidates, source, STANDARD) != null) {
                aliases.add(channel);
            }
        }

        // More than one old pattern may have been selected deliberately. Keep
        // those rows separate rather than choosing a winner on the player's behalf.
        boolean combine = placements.has(STANDARD) || aliases.size() == 1;
        if (combine) {
            for (String alias : aliases) {
                if (!placements.has(STANDARD)) placements.add(STANDARD, placements.get(alias));
                placements.remove(alias);
            }
        }

        JsonArray visible = new JsonArray();
        for (JsonElement element : candidates) {
            if (!element.isJsonObject()) continue;
            JsonObject candidate = element.getAsJsonObject();
            String source = text(candidate, "source_id");
            String channel = text(candidate, "placement_channel");
            JsonObject current = loadedCandidate(candidates, source, STANDARD);

            if (!combine && !aliases.isEmpty() && STANDARD.equals(channel) && current != null) {
                boolean projected = false;
                for (String alias : aliases) {
                    if (!inferredAlias(root, source, domain, alias)) continue;

                    JsonObject retained = JsonCopies.copy(current);
                    retained.addProperty("placement_channel", alias);
                    visible.add(retained);
                    projected = true;
                }
                if (projected) continue;
            }

            if (current != null && OLD_CHANNELS.contains(channel)
                    && inferredAlias(root, source, domain, channel)) {
                continue;
            }
            visible.add(candidate);
        }

        view.add("candidates", visible);
        view.add("placement_sources", placements);
        return view;
    }

    private static boolean inferredAlias(JsonObject root, String source, String domain, String channel) {
        JsonObject ore = object(object(root, "ores"), source);
        JsonObject dimensions = object(ore, "dimensions");
        JsonObject rule = dimensions.has(domain) ? object(dimensions, domain)
                : object(object(ore, "dimension_selectors"), domain);
        if (rule.has("placement_channel")) return false;

        JsonElement pattern = rule.get("pattern");
        if (pattern == null || (!pattern.isJsonObject() && !pattern.isJsonPrimitive())) return false;
        return channel.equals(pattern.isJsonObject()
                ? text(pattern.getAsJsonObject(), "type") : pattern.getAsString());
    }

    private static JsonObject loadedCandidate(JsonArray candidates, String source, String channel) {
        for (JsonElement element : candidates) {
            if (!element.isJsonObject()) continue;
            JsonObject candidate = element.getAsJsonObject();
            if (source.equals(text(candidate, "source_id"))
                    && channel.equals(text(candidate, "placement_channel"))
                    && candidate.has("loaded") && candidate.get("loaded").getAsBoolean()) {
                return candidate;
            }
        }
        return null;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static String text(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonPrimitive() ? parent.get(key).getAsString() : "";
    }
}
