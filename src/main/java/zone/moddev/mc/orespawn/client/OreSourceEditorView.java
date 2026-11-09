package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ResourceLocation;
import zone.moddev.mc.orespawn.client.GeologyEditorSession.OreSourceCandidate;
import zone.moddev.mc.orespawn.client.GeologyEditorSession.OreSourceGroup;
import zone.moddev.mc.orespawn.util.JsonCopies;

/** Builds the editor's view without rewriting the world's saved choices. */
final class OreSourceEditorView {
    private static final String STANDARD = "orespawn:standard";
    private static final Set<String> OLD_CHANNELS = new HashSet<>(Arrays.asList(
            "orespawn:default", "orespawn:vein", "orespawn:normal_cloud",
            "orespawn:precision", "orespawn:clusters", "orespawn:underfluids"));

    private OreSourceEditorView() { }

    static void addVanillaPreviews(JsonObject root, List<OreSourceGroup> groups) {
        // Forge ships these tags, but Create New World can open before server data loads.
        // Keep these rows out of the profile so they never participate in world generation.
        addVanillaPreview(root, groups, "coal", "Coal", "coal_ore", "minecraft:overworld");
        addVanillaPreview(root, groups, "diamond", "Diamond", "diamond_ore", "minecraft:overworld");
        addVanillaPreview(root, groups, "emerald", "Emerald", "emerald_ore", "minecraft:overworld");
        addVanillaPreview(root, groups, "gold", "Gold", "gold_ore", "minecraft:overworld");
        addVanillaPreview(root, groups, "iron", "Iron", "iron_ore", "minecraft:overworld");
        addVanillaPreview(root, groups, "lapis", "Lapis", "lapis_ore", "minecraft:overworld");
        addVanillaPreview(root, groups, "quartz", "Quartz", "nether_quartz_ore", "minecraft:the_nether");
        addVanillaPreview(root, groups, "redstone", "Redstone", "redstone_ore", "minecraft:overworld");
    }

    private static void addVanillaPreview(JsonObject root, List<OreSourceGroup> groups,
            String name, String label, String block, String domain) {
        String blockId = "minecraft:" + block;
        String material = "orespawn:" + name;
        String key = material + '|' + domain;
        String tag = "forge:ores/" + name;
        JsonObject ore = object(object(root, "ores"), blockId);
        String output = text(ore, "block");
        if (!flag(ore, "native_generation", false) || !object(ore, "dimensions").has(domain)
                || ore.has("material") || (!output.isEmpty() && !blockId.equals(output))
                || BlockTags.getCollection().get(new ResourceLocation(tag)) != null) return;
        for (OreSourceGroup group : groups) {
            if (key.equals(group.key)) return;
            if (domain.equals(group.domain) && group.candidates.stream()
                    .anyMatch(candidate -> blockId.equals(candidate.registryId))) return;
        }

        List<String> tags = Collections.singletonList(tag);
        boolean managed = flag(root, "manage_vanilla_ores", false) && flag(ore, "enabled", true)
                && flag(object(object(ore, "dimensions"), domain), "enabled", true);
        OreSourceCandidate candidate = new OreSourceCandidate(blockId, "minecraft", "Minecraft", "1.13.2",
                blockId, 0, STANDARD, tags, true, managed, false, false, false);
        groups.add(new OreSourceGroup(key, material, domain, label, tags, false, "keep_separate", "custom",
                "separate", Collections.singletonList(candidate), Collections.singletonMap(blockId, 1.0D),
                Collections.emptyMap(), true));
    }

    private static boolean flag(JsonObject object, String key, boolean fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : fallback;
    }

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
