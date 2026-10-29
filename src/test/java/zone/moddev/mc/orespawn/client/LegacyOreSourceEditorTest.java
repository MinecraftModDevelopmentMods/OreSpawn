package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import zone.moddev.mc.orespawn.client.GeologyEditorSession.OreSourceGroup;
import zone.moddev.mc.orespawn.test.MinecraftTestBootstrap;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;

class LegacyOreSourceEditorTest {
    private static final String GROUP = "orespawn:iron|minecraft:overworld";

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.registerVanilla();
    }

    @Test
    void importedSelectionsHaveOneLiveStandardRowWithoutChangingTheSavedProfile() throws Exception {
        for (String name : Arrays.asList("os1-import", "os3-import")) {
            JsonObject fixture = fixture(name);
            GeologyEditorSession session = session(fixture);
            String before = session.root().toString();
            OreSourceGroup group = session.oreSourceGroups().get(0);

            assertEquals(Collections.singletonList("orespawn:standard"),
                    OreSourceGroupSettingsScreen.placementChannels(group), name);
            assertEquals("consolidated", group.status, name);
            assertEquals(2, group.candidates.size(), name);
            assertEquals(2, OreSourceListScreen.placementCandidates(group, "orespawn:standard").size());
            assertEquals(before, session.root().toString(), "Viewing must not rewrite saved choices");
        }
    }

    @Test
    void explicitCustomChannelsAndFreshStandardSelectionsStaySeparate() throws Exception {
        for (String name : Arrays.asList("custom-channel", "fresh-standard")) {
            GeologyEditorSession session = session(fixture(name));
            String before = session.root().toString();
            OreSourceGroup group = session.oreSourceGroups().get(0);
            String expected = name.equals("custom-channel") ? "example:custom_iron" : "orespawn:standard";

            assertEquals(Collections.singletonList(expected),
                    OreSourceGroupSettingsScreen.placementChannels(group));
            assertEquals("consolidated", group.status);
            assertEquals(before, session.root().toString());
        }
    }

    @Test
    void anExplicitOldDefaultChannelIsNotAnInferredAlias() throws Exception {
        JsonObject fixture = fixture("custom-channel");
        fixture.getAsJsonObject("ores").getAsJsonObject("example:iron")
                .getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
                .addProperty("placement_channel", "orespawn:default");
        JsonObject policy = policy(fixture);
        policy.getAsJsonObject("placement_sources").remove("example:custom_iron");
        policy.getAsJsonObject("placement_sources").addProperty("orespawn:default", "example:iron");
        for (com.google.gson.JsonElement entry : policy.getAsJsonArray("candidates")) {
            entry.getAsJsonObject().addProperty("placement_channel", "orespawn:default");
        }

        assertEquals(Collections.singletonList("orespawn:default"),
                OreSourceGroupSettingsScreen.placementChannels(session(fixture).oreSourceGroups().get(0)));
    }

    @Test
    void aNewStandardChoiceWinsButAnActuallyMissingSourceStillNeedsAttention() throws Exception {
        JsonObject fixture = fixture("os3-import");
        policy(fixture).getAsJsonObject("placement_sources")
                .addProperty("orespawn:standard", "example:alternative_iron");
        OreSourceGroup group = session(fixture).oreSourceGroups().get(0);

        assertEquals(Collections.singletonList("orespawn:standard"),
                OreSourceGroupSettingsScreen.placementChannels(group));
        assertEquals("example:alternative_iron", group.placements.get("orespawn:standard"));
        assertEquals("consolidated", group.status);

        policy(fixture).getAsJsonObject("placement_sources").addProperty("orespawn:standard", "missing:iron");
        assertEquals("missing_source", session(fixture).oreSourceGroups().get(0).status);
    }

    @Test
    void editsRoundTripWithoutChangingRulesAndCancelLeavesTheOriginalUntouched() throws Exception {
        JsonObject fixture = fixture("os3-import");
        String original = fixture.toString();
        GeologyEditorSession session = session(fixture);
        String rules = session.root().get("ores").toString();

        session.setOreSourcePlacement(GROUP, "orespawn:standard", "example:alternative_iron");
        assertEquals("example:alternative_iron",
                session(session.profile().toJson()).oreSourceGroups().get(0).placements.get("orespawn:standard"));
        assertEquals(rules, session.root().get("ores").toString());
        assertEquals(original, fixture.toString(), "Discarding a session must leave its input intact");
    }

    @Test
    void loadedInactiveSourcesDoNotLookMissing() throws Exception {
        JsonObject fixture = fixture("os1-import");
        for (com.google.gson.JsonElement entry : policy(fixture).getAsJsonArray("candidates")) {
            entry.getAsJsonObject().addProperty("placement_active", false);
        }

        OreSourceGroup group = session(fixture).oreSourceGroups().get(0);
        assertEquals("consolidated", group.status);
        assertNotNull(OreSourceGroupSettingsScreen.selectedPlacementCandidate(group, "orespawn:standard"));
        assertFalse(OreSourceGroupSettingsScreen.placementSelectable(group, "orespawn:standard"));
    }

    @Test
    void manageVanillaSwitchKeepsImportedChoicesAndCustomRules() throws Exception {
        for (String name : Arrays.asList("os1-import", "os3-import", "custom-channel", "fresh-standard")) {
            GeologyEditorSession session = session(fixture(name));
            String rules = session.root().get("ores").toString();
            String choices = policy(session.root()).get("placement_sources").toString();

            session.setManageVanillaOres(false);
            assertEquals(choices, policy(session.root()).get("placement_sources").toString(), name);
            session.setManageVanillaOres(true);
            assertEquals(choices, policy(session.root()).get("placement_sources").toString(), name);
            assertEquals(rules, session.root().get("ores").toString(), name);
        }
    }

    @Test
    void unrelatedAliasesAndMultipleSavedPatternChoicesAreNotSilentlyMerged() throws Exception {
        JsonObject fixture = fixture("os3-import");
        JsonObject placements = policy(fixture).getAsJsonObject("placement_sources");
        placements.addProperty("orespawn:vein", "example:alternative_iron");
        fixture.getAsJsonObject("ores").getAsJsonObject("example:alternative_iron")
                .getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
                .addProperty("pattern", "orespawn:vein");
        String before = fixture.toString();
        OreSourceGroup group = session(fixture).oreSourceGroups().get(0);

        assertEquals(placements.toString(), new com.google.gson.Gson().toJsonTree(group.placements).toString());
        assertNotNull(OreSourceGroupSettingsScreen.selectedPlacementCandidate(group, "orespawn:default"));
        assertNotNull(OreSourceGroupSettingsScreen.selectedPlacementCandidate(group, "orespawn:vein"));
        assertEquals(Arrays.asList("orespawn:default", "orespawn:vein"),
                OreSourceGroupSettingsScreen.placementChannels(group));
        assertEquals(before, fixture.toString());
    }

    static JsonObject fixture(String name) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                LegacyOreSourceEditorTest.class.getResourceAsStream("/ore-source-editor/" + name + ".json"),
                StandardCharsets.UTF_8)) {
            return new JsonParser().parse(reader).getAsJsonObject();
        }
    }

    private static GeologyEditorSession session(JsonObject root) {
        return new GeologyEditorSession(WorldGeologyProfile.fromJson(root, WorldGeologyProfile.recommended(false)));
    }

    private static JsonObject policy(JsonObject root) {
        return root.getAsJsonObject("ore_source_policies").getAsJsonObject(GROUP);
    }
}
