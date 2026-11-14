package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.api.*;
import zone.moddev.mc.orespawn.test.Forge25TestBootstrap;

class NaturalFormationTest {
    @BeforeAll static void bootstrap() { Forge25TestBootstrap.registerVanilla(); }
    static JsonObject rule(String type) {
        JsonObject r = new JsonObject();
        r.addProperty("type", "orespawn:" + type); r.addProperty("algorithm_revision", 1);
        r.addProperty("salt", 71); r.addProperty("chance", 1); r.addProperty("spacing_chunks", 16);
        r.addProperty("radius_min", 18); r.addProperty("radius_max", 28);
        r.addProperty("min_y", 32); r.addProperty("max_y", 32); r.addProperty("height", 40);
        JsonObject m = new JsonObject();
        String[] roles = {"syenite", "nepheline_syenite", "carbonatite", "granite", "skarn", "greisen",
                "serpentinite", "pipe", "vein_quartz", "ferricrete", "lateritic_clay", "saprolitic_rock"};
        for (int i = 0; i < roles.length; i++) {
            String[] variants = {"granite", "polished_granite", "diorite",
                    "polished_diorite", "andesite", "polished_andesite"};
            JsonObject material = new JsonObject();
            material.addProperty("block", "minecraft:" + variants[i % variants.length]);
            material.addProperty("family", "igneous_intrusive");
            m.add(roles[i], material);
        }
        r.add("materials", m); return r;
    }
    static GeologyColumn base(int x, int z) {
        return new GeologyColumn() {
            public ResourceLocation dimension() { return new ResourceLocation("minecraft:overworld"); }
            public ResourceLocation biome() { return new ResourceLocation("minecraft:jungle"); }
            public ResourceLocation geome() { return new ResourceLocation("orespawn:stable_craton"); }
            public int blockX() { return x; } public int blockZ() { return z; } public int surfaceY() { return 100; }
            public IBlockState rockAt(int y) { return Blocks.STONE.getDefaultState(); }
            public Optional<GeologyFamily> familyAt(int y) { return Optional.of(GeologyFamily.IGNEOUS_INTRUSIVE); }
        };
    }
    @Test void deterministicAcrossNegativeRegionsAndReverseColumnOrder() {
        BuiltinRockFormation f = new BuiltinRockFormation("alkaline_complex", rule("alkaline_complex"));
        List<String> forward = new ArrayList<>(), reverse = new ArrayList<>();
        for (int x = -512; x < 512; x += 8) for (int z = -256; z < 256; z += 8)
            forward.add(sample(f, x, z));
        for (int x = 504; x >= -512; x -= 8) for (int z = 248; z >= -256; z -= 8)
            reverse.add(sample(f, x, z));
        Collections.reverse(reverse); assertEquals(forward, reverse);
        assertTrue(forward.stream().anyMatch(s -> !s.equals("none")));
    }
    private static String sample(BuiltinRockFormation f, int x, int z) {
        RockFormationColumn column = f.prepare(new RockFormationContext(719, base(x,z), 1, 1, () -> 100));
        if (column == null) return "none";
        StringBuilder s = new StringBuilder();
        for (int y = 4; y < 100; y++) {
            FormationMaterial m = column.at(y);
            if (m != null) { assertTrue(y >= 5 && y <= 92); s.append(y).append(':').append(m.state()).append(';'); }
        }
        return s.length() == 0 ? "none" : s.toString();
    }
    @Test void unknownRevisionMissingOutputsAndOversizedBodiesFailClosed() {
        JsonObject r = rule("barren_pipe"); r.addProperty("algorithm_revision", 2);
        assertThrows(IllegalArgumentException.class, () -> new BuiltinRockFormation("barren_pipe", r));
        r.addProperty("algorithm_revision", 1); r.getAsJsonObject("materials").remove("pipe");
        assertThrows(IllegalArgumentException.class, () -> new BuiltinRockFormation("barren_pipe", r));
        r.getAsJsonObject("materials").add("pipe", r.getAsJsonObject("materials").get("granite"));
        r.addProperty("radius_max", 64);
        assertThrows(IllegalArgumentException.class, () -> new BuiltinRockFormation("barren_pipe", r));
    }
    @Test void providerBuilderCopiesDefinitionsAndEnablesSchemaSixOnlyWhenNeeded() {
        JsonObject r = rule("quartz_vein");
        WorldgenProvider provider = WorldgenProvider.builder("test", 1).rockFormation(new ResourceLocation("test:veins"),
                new ResourceLocation("orespawn:quartz_vein"), r).build();
        r.addProperty("algorithm_revision", 99);
        assertEquals(6, provider.toJson().get("schema_version").getAsInt());
        assertEquals(1, provider.toJson().getAsJsonObject("rock_formations").getAsJsonObject("test:veins")
                .get("algorithm_revision").getAsInt());
    }
    @Test void oldProfilesAndUnprofiledUpgradesDoNotInheritNewFormations() {
        JsonObject defaults = new JsonObject();
        JsonObject forms = new JsonObject(); forms.add("test:veins", rule("quartz_vein"));
        defaults.add("rock_formations", forms);
        WorldGeologyProfile fresh = WorldGeologyProfile.fromGlobalConfig(defaults,
                zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode.GEOME, false);
        assertTrue(fresh.toJson().has("rock_formations"));
        for (int schema : new int[]{1,2,3,6,7}) {
            JsonObject old = new JsonObject(); old.addProperty("schema_version", schema);
            assertFalse(WorldGeologyProfile.fromJson(old,fresh).toJson().has("rock_formations"));
        }
        assertFalse(WorldGeologyProfileManager.initializeNewWorldPolicies(fresh,true).toJson().has("rock_formations"));
        assertEquals(forms, WorldGeologyProfile.fromJson(fresh.toJson(),fresh).toJson().get("rock_formations"));
    }
    @Test void missingFormationExtensionsRemainDormantWithoutLosingTheirSettings() {
        JsonObject profile = new JsonObject(), formations = new JsonObject();
        JsonObject definition = rule("barren_pipe");
        definition.addProperty("type", "not_installed:pipe");
        formations.add("not_installed:pipe", definition);
        profile.add("rock_formations", formations);
        JsonObject before = zone.moddev.mc.orespawn.util.JsonCopies.copy(profile);
        try {
            assertDoesNotThrow(() -> NaturalFormations.install(profile));
            assertFalse(NaturalFormations.hasConfiguredFormations());
            assertEquals(before, profile);
        } finally { NaturalFormations.install(new JsonObject()); }
    }
    @Test void weatheringNeedsWarmWetClimateAndANaturalGraniteParent() {
        JsonObject definition = rule("weathering_profile");
        BuiltinRockFormation formation = new BuiltinRockFormation("weathering_profile", definition);
        int[] site = null;
        for (int x = -256; x < 256 && site == null; x += 4) for (int z = -256; z < 256; z += 4) {
            RockFormationColumn column = weatherColumn(formation, x, z, 1, 1, Blocks.GRANITE.getDefaultState());
            if (column != null && column.at(95) != null) { site = new int[]{x,z}; break; }
        }
        assertNotNull(site);
        RockFormationColumn wet = weatherColumn(formation, site[0], site[1], 1, 1, Blocks.GRANITE.getDefaultState());
        assertNotNull(wet.at(85)); assertNotNull(wet.at(91)); assertNotNull(wet.at(95));
        assertNotEquals(wet.at(85).state(), wet.at(91).state());
        assertNotEquals(wet.at(91).state(), wet.at(95).state());
        assertNull(wet.at(84)); assertNull(wet.at(98));
        for (IBlockState unsuitable : new IBlockState[]{Blocks.STONE.getDefaultState(),
                Blocks.POLISHED_GRANITE.getDefaultState(), Blocks.INFESTED_STONE.getDefaultState(), Blocks.CLAY.getDefaultState()}) {
            RockFormationColumn column = weatherColumn(formation, site[0], site[1], 1, 1, unsuitable);
            assertNull(column.at(95));
        }
        assertNull(weatherColumn(formation, site[0], site[1], .5F, 1, Blocks.GRANITE.getDefaultState()).at(95));
        assertNull(weatherColumn(formation, site[0], site[1], 1, .2F, Blocks.GRANITE.getDefaultState()).at(95));
    }
    static RockFormationColumn weatherColumn(BuiltinRockFormation formation, int x, int z,
            float temperature, float rain, IBlockState parent) {
        GeologyColumn original = base(x,z);
        GeologyColumn column = new GeologyColumn() {
            public ResourceLocation dimension(){return original.dimension();}
            public ResourceLocation biome(){return original.biome();}
            public ResourceLocation geome(){return original.geome();}
            public int blockX(){return x;} public int blockZ(){return z;} public int surfaceY(){return 100;}
            public IBlockState rockAt(int y){return parent;}
            public Optional<GeologyFamily> familyAt(int y){return Optional.empty();}
        };
        return formation.prepare(new RockFormationContext(713,column,temperature,rain,()->100));
    }
}
