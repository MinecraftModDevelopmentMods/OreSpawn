package zone.moddev.mc.orespawn.worldgen;

import com.google.gson.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import zone.moddev.mc.orespawn.api.*;

/** Immutable deterministic bodies in region coordinates, not independent random stone bands. */
final class BuiltinRockFormation implements CompiledRockFormation {
    private final int type;
    private final long salt;
    private final int spacing, minY, maxY, minRadius, maxRadius, height, cover;
    private final double chance;
    private final Set<ResourceLocation> geomes = new HashSet<>();
    private final Set<ResourceLocation> dimensions = new HashSet<>();
    private final Map<String, FormationMaterial> outputs = new HashMap<>();
    private final FormationMaterial syenite, nepheline, carbonatite, granite, skarn, greisen, serpentinite,
            pipe, quartz, ferricrete, clay, saprolitic;
    private final Set<Block> carbonates = new HashSet<>(), ultramafics = new HashSet<>(), weatherParents = new HashSet<>();
    BuiltinRockFormation(String type, JsonObject rule) {
        this.type = Arrays.asList("alkaline_complex", "granitic_contact", "ultramafic_alteration",
                "barren_pipe", "quartz_vein", "weathering_profile").indexOf(type);
        if (this.type < 0) throw new IllegalArgumentException("Unknown formation type " + type);
        if (integer(rule, "algorithm_revision", 1) != 1) throw new IllegalArgumentException("Unsupported formation algorithm");
        salt = rule.has("salt") ? rule.get("salt").getAsLong() : type.hashCode();
        int chunks = integer(rule, "spacing_chunks", 32);
        if (chunks < 8 || chunks > 65536) throw new IllegalArgumentException("Invalid formation spacing");
        spacing = chunks * 16;
        minY = integer(rule, "min_y", 12); maxY = integer(rule, "max_y", 64);
        minRadius = integer(rule, "radius_min", 12); maxRadius = integer(rule, "radius_max", 28);
        height = integer(rule, "height", 40); cover = integer(rule, "cover", 8);
        chance = rule.has("chance") ? rule.get("chance").getAsDouble() : .12;
        if (spacing < 128 || maxRadius * 1.1 >= spacing / 4 || minRadius < 2 || maxRadius < minRadius
                || minY < 5 || maxY > 240 || minY > maxY || height < 4 || height > 128
                || cover < 4 || !Double.isFinite(chance) || chance < 0 || chance > 1)
            throw new IllegalArgumentException("Invalid rock formation geometry");
        if (rule.has("geomes")) for (JsonElement g : rule.getAsJsonArray("geomes"))
            geomes.add(new ResourceLocation(g.getAsString().indexOf(':') < 0 ? "orespawn:" + g.getAsString() : g.getAsString()));
        if (rule.has("dimensions")) for (JsonElement d : rule.getAsJsonArray("dimensions"))
            dimensions.add(new ResourceLocation(d.getAsString()));
        else dimensions.add(new ResourceLocation("minecraft:overworld"));
        JsonObject materials = rule.getAsJsonObject("materials");
        if (materials == null || materials.entrySet().isEmpty()) throw new IllegalArgumentException("Formation has no materials");
        for (Map.Entry<String, JsonElement> e : materials.entrySet()) {
            JsonObject m = e.getValue().getAsJsonObject();
            ResourceLocation id = new ResourceLocation(m.get("block").getAsString());
            if (!net.minecraftforge.registries.ForgeRegistries.BLOCKS.containsKey(id)) throw new IllegalArgumentException("Missing formation material: " + id);
            Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(id);
            if (block == Blocks.AIR || block == Blocks.BEDROCK) throw new IllegalArgumentException("Invalid formation output: " + id);
            GeologyFamily family = m.has("family") ? GeologyFamily.valueOf(m.get("family").getAsString().toUpperCase(Locale.ROOT)) : null;
            outputs.put(e.getKey(), new FormationMaterial(
                    zone.moddev.mc.orespawn.util.ConfiguredBlockState.read(m), family));
        }
        String[][] required = {{"syenite", "nepheline_syenite", "carbonatite"}, {"granite", "skarn", "greisen"},
                {"serpentinite"}, {"pipe"}, {"vein_quartz"}, {"ferricrete", "lateritic_clay", "saprolitic_rock"}};
        for (String role : required[this.type]) if (!outputs.containsKey(role))
            throw new IllegalArgumentException("Missing formation material role " + role);
        syenite = outputs.get("syenite"); nepheline = outputs.get("nepheline_syenite");
        carbonatite = outputs.get("carbonatite"); granite = outputs.get("granite");
        skarn = outputs.get("skarn"); greisen = outputs.get("greisen"); serpentinite = outputs.get("serpentinite");
        pipe = outputs.get("pipe"); quartz = outputs.get("vein_quartz"); ferricrete = outputs.get("ferricrete");
        clay = outputs.get("lateritic_clay"); saprolitic = outputs.get("saprolitic_rock");
        collect(carbonates, "limestone", "dolomite");
        collect(ultramafics, "peridotite");
        collect(weatherParents, "peridotite", "syenite", "nepheline_syenite", "granite");
    }
    private static void collect(Set<Block> into, String... names) {
        for (String name : names) {
            ResourceLocation key = new ResourceLocation("mineralogy", name);
            if (net.minecraftforge.registries.ForgeRegistries.BLOCKS.containsKey(key)) into.add(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(key));
        }
    }
    static int integer(JsonObject rule, String key, int fallback) { return rule.has(key) ? rule.get(key).getAsInt() : fallback; }
    static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
    static double unit(long hash) { return (hash >>> 11) * 0x1.0p-53; }
    @Override public RockFormationColumn prepare(RockFormationContext c) {
        if (!dimensions.contains(c.base().dimension())) return null;
        if (!geomes.isEmpty() && !geomes.contains(c.base().geome())) return null;
        int x = c.base().blockX(), z = c.base().blockZ();
        int rx = Math.floorDiv(x, spacing), rz = Math.floorDiv(z, spacing);
        long h = mix(c.seed() ^ salt ^ mix(rx) ^ Long.rotateLeft(mix(rz), 27));
        if (unit(h) >= chance) return null;
        int cx = rx * spacing + spacing / 2 + (int) (unit(mix(h + 1)) * spacing / 2) - spacing / 4;
        int cz = rz * spacing + spacing / 2 + (int) (unit(mix(h + 2)) * spacing / 2) - spacing / 4;
        double radius = minRadius + unit(mix(h + 3)) * (maxRadius - minRadius);
        double dx = (x - cx) / radius, dz = (z - cz) / (radius * .75);
        double horizontal = dx * dx + dz * dz;
        if (horizontal > 1.15) return null;
        int surface = c.surfaceY();
        if (surface < 12) return null;
        // One body has one elevation: terrain may clip it, but must not bend an
        // intrusion up and down independently in each surface-height column.
        int centre = minY + (int) (unit(mix(h + 4)) * (maxY - minY + 1));
        final long identity = h;
        final IBlockState weatherParent = type == 5 ? c.base().rockAt(surface - 18) : null;
        final boolean weatherable = type == 5 && (weatherParents.contains(weatherParent.getBlock())
                || weatherParent.equals(Blocks.GRANITE.getDefaultState()));
        return new RockFormationColumn() {
          @Override public FormationMaterial at(int y) {
            if (y < 5 || y > surface - 3) return null;
            IBlockState parent = c.base().rockAt(y);
            Block host = parent.getBlock();
            double vertical = (y - centre) / (height * .5);
            double body = horizontal + vertical * vertical;
            switch (type) {
                case 0:
                    if (y > surface - cover || body > 1) return null;
                    if (body < .16 && unit(mix(identity + 7)) < .3) return carbonatite;
                    return unit(mix(identity + 8)) < .35 && body < .6 ? nepheline : syenite;
                case 1:
                    if (y > surface - cover || body > 1.18) return null;
                    if (body > .82) return carbonates.contains(host) ? skarn : null;
                    if (vertical > .3 && Math.abs((x - cx) + (z - cz) * .5) < 4) return greisen;
                    return granite;
                case 2:
                    return body <= 1 && ultramafics.contains(host)
                            ? serpentinite : null;
                case 3:
                    if (y > surface - cover || y < 8 || y > maxY + height) return null;
                    double taper = .2 + .8 * Math.max(0, Math.min(1, (y - 8.0) / (maxY + height - 8.0)));
                    return horizontal < taper * taper ? pipe : null;
                case 4:
                    if (y > surface - cover || body > 1) return null;
                    return Math.abs((x - cx) * .65 + z - cz + Math.sin(y * .19) * 2) < 1.5
                            ? quartz : null;
                case 5:
                    if (c.temperature() < .8F || c.rainfall() < .6F || horizontal > 1) return null;
                    int depth = surface - y;
                    if (depth < 3 || depth > 15) return null;
                    // Parent is assessed below the whole weathering profile, never by a generic clay identity.
                    if (!weatherable) return null;
                    return depth <= 5 ? ferricrete : depth <= 9 ? clay : saprolitic;
                default: throw new IllegalStateException("Uncompiled formation");
            }
          }
          @Override public IBlockState parentAt(int y) {
              if (type == 5) return weatherParent;
              if (type == 1 && at(y) == greisen) return granite.state();
              return null;
          }
        };
    }
}
