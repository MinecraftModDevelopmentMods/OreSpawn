package zone.moddev.mc.orespawn.commands.admin;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.TreeMap;

/** Counts observed cells, not attempts or inferred vein sizes. */
public final class SurveyCounts {
    private final Map<String, Long> blocks = new TreeMap<>();
    private final Map<String, Long> heights = new TreeMap<>();
    private final Map<String, Long> biomes = new TreeMap<>();
    private final Map<String, Long> chunks = new TreeMap<>();
    private final Map<String, String> categories = new TreeMap<>();
    private long volume;
    private long nonAir;
    private final int rowLimit;
    public SurveyCounts() { this(500000); }
    SurveyCounts(int rowLimit) { this.rowLimit = rowLimit; }

    public void add(String state, String category, int y, String biome, int chunkX, int chunkZ) {
        String heightKey = y + "\t" + state, biomeKey = biome + "\t" + state, chunkKey = chunkX + "," + chunkZ + "\t" + state;
        int rows = blocks.size() + heights.size() + biomes.size() + chunks.size();
        int additions = (blocks.containsKey(state) ? 0 : 1) + (heights.containsKey(heightKey) ? 0 : 1)
                + (biomes.containsKey(biomeKey) ? 0 : 1) + (chunks.containsKey(chunkKey) ? 0 : 1);
        if (rows + additions > rowLimit) throw new IllegalStateException("Survey report is too large; reduce the radius or height range");
        volume++;
        if (!"air".equals(category)) nonAir++;
        categories.put(state, category);
        increment(blocks, state);
        increment(heights, heightKey);
        increment(biomes, biomeKey);
        increment(chunks, chunkKey);
    }

    private static void increment(Map<String, Long> values, String key) { values.merge(key, 1L, Long::sum); }
    public long volume() { return volume; }

    public JsonObject json() {
        JsonObject result = new JsonObject();
        result.addProperty("sampled_volume", volume);
        result.addProperty("non_air_volume", nonAir);
        result.add("blocks", json(blocks));
        result.add("by_y", json(heights));
        result.add("by_biome", json(biomes));
        result.add("by_chunk", json(chunks));
        JsonObject classified = new JsonObject();
        categories.forEach(classified::addProperty);
        result.add("categories", classified);
        return result;
    }

    private static JsonObject json(Map<String, Long> values) {
        JsonObject result = new JsonObject();
        values.forEach(result::addProperty);
        return result;
    }

    public Map<String, String> csv() {
        Map<String, String> files = new TreeMap<>();
        files.put("blocks.csv", csv("state", blocks));
        files.put("height.csv", csv("y,state", heights));
        files.put("biomes.csv", csv("biome,state", biomes));
        files.put("chunks.csv", csv("chunk,state", chunks));
        return files;
    }

    private static String csv(String header, Map<String, Long> rows) {
        StringBuilder text = new StringBuilder(header).append(",count\n");
        rows.forEach((key, count) -> {
            for (String field : key.split("\t", -1)) text.append('"').append(field.replace("\"", "\"\"")).append("\",");
            text.append(count).append('\n');
        });
        return text.toString();
    }
}
