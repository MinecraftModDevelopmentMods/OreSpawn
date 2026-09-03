package zone.moddev.mc.orespawn.worldgen;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode;

/**
 * Snapshots the exact Mineralogy geology contract used by an already-generated
 * world before OreSpawn becomes responsible for that world's geology.
 *
 * <p>The 1.10 and 1.12 Forge configuration files are related but not
 * interchangeable. Mineralogy 5.x uses a third, TOML-based contract and can
 * select either its Cyano layer engine or its geome engine. Saved world mod
 * metadata therefore chooses the lineage; merely finding an old file in a
 * reused instance is never enough to reclassify a fresh world.</p>
 */
final class LegacyMineralogyProfileMigration {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String CFG_FILE = "mineralogy.cfg";
    private static final String TOML_FILE = "mineralogy-common.toml";

    private static final List<String> IGNEOUS_110 = list(
            "mineralogy:diabase", "mineralogy:gabbro", "mineralogy:peridotite",
            "mineralogy:basaltic_glass", "mineralogy:scoria", "mineralogy:tuff",
            "mineralogy:andesite", "mineralogy:basalt", "mineralogy:diorite",
            "mineralogy:granite", "mineralogy:rhyolite", "mineralogy:pegmatite",
            "mineralogy:pumice");
    private static final List<String> METAMORPHIC_110 = list(
            "mineralogy:hornfels", "mineralogy:quartzite", "mineralogy:novaculite",
            "mineralogy:slate", "mineralogy:schist", "mineralogy:gneiss",
            "mineralogy:phyllite", "mineralogy:amphibolite");
    private static final List<String> SEDIMENTARY_110_BEFORE_COAL = list(
            "mineralogy:siltstone", "mineralogy:shale", "mineralogy:conglomerate",
            "mineralogy:dolomite", "mineralogy:limestone", "mineralogy:marble",
            "minecraft:sandstone");
    private static final List<String> SEDIMENTARY_110_AFTER_COAL = list(
            "mineralogy:chert", "mineralogy:gypsum", "mineralogy:chalk",
            "mineralogy:rock_salt");

    private static final List<String> IGNEOUS_112 = list(
            "mineralogy:andesite", "mineralogy:basalt", "mineralogy:diorite",
            "mineralogy:granite", "mineralogy:rhyolite", "mineralogy:pegmatite",
            "mineralogy:diabase", "mineralogy:gabbro", "mineralogy:peridotite",
            "mineralogy:basaltic_glass", "mineralogy:scoria", "mineralogy:tuff",
            "mineralogy:pumice");
    private static final List<String> METAMORPHIC_112 = list(
            "mineralogy:slate", "mineralogy:schist", "mineralogy:gneiss",
            "mineralogy:phyllite", "mineralogy:amphibolite", "mineralogy:hornfels",
            "mineralogy:quartzite", "mineralogy:novaculite");
    private static final List<String> SEDIMENTARY_112 = list(
            "mineralogy:shale", "mineralogy:conglomerate", "mineralogy:dolomite",
            "mineralogy:limestone", "mineralogy:siltstone", "mineralogy:marble",
            "minecraft:sandstone", "mineralogy:chert", "mineralogy:gypsum",
            "mineralogy:chalk", "mineralogy:rock_salt", "mineralogy:rock_salt");

    /* Exact registration order used by published Mineralogy 5.0.1 through 5.4.0. */
    private static final List<String> IGNEOUS_5 = list(
            "mineralogy:andesite", "mineralogy:basalt", "mineralogy:diorite",
            "mineralogy:granite", "mineralogy:rhyolite", "mineralogy:pegmatite",
            "mineralogy:diabase", "mineralogy:gabbro", "mineralogy:peridotite",
            "mineralogy:basaltic_glass", "mineralogy:scoria", "mineralogy:tuff",
            "mineralogy:pumice");
    private static final List<String> METAMORPHIC_5 = list(
            "mineralogy:marble", "mineralogy:slate", "mineralogy:schist",
            "mineralogy:gneiss", "mineralogy:phyllite", "mineralogy:amphibolite",
            "mineralogy:hornfels", "mineralogy:quartzite", "mineralogy:novaculite");
    private static final List<String> SEDIMENTARY_5 = list(
            "mineralogy:shale", "mineralogy:conglomerate", "mineralogy:dolomite",
            "mineralogy:limestone", "mineralogy:siltstone", "mineralogy:rock_salt",
            "minecraft:sandstone", "mineralogy:chert", "mineralogy:gypsum",
            "mineralogy:chalk");

    private LegacyMineralogyProfileMigration() {
    }

    static WorldGeologyProfile migrateIfNeeded(Path worldRoot, Path configDirectory,
            WorldGeologyProfile installedPackProfile) {
        // A per-world OreSpawn profile is authoritative. Keep this guard here as
        // well as in the server lifecycle caller so future call sites cannot
        // accidentally reclassify an established OS4 world from stale files.
        if (Files.isRegularFile(worldRoot.resolve("serverconfig")
                .resolve("orespawn-worldgen.json"))) return null;
        if (!hasGeneratedOverworldChunks(worldRoot)) return null;

        MineralogyIdentity identity = legacyMineralogyIdentity(worldRoot);
        if (identity == null) return null;

        Lineage lineage = Lineage.forVersion(identity.version);
        Path configPath = configDirectory.resolve(lineage == Lineage.MINERALOGY_5
                ? TOML_FILE : CFG_FILE);
        ConfigValues values = lineage == Lineage.MINERALOGY_5
                ? readToml(configPath) : readForgeCfg(configPath);
        boolean configFound = Files.isRegularFile(configPath);
        List<String> warnings = new ArrayList<>();
        if (!configFound) {
            Path other = configDirectory.resolve(lineage == Lineage.MINERALOGY_5
                    ? CFG_FILE : TOML_FILE);
            if (Files.isRegularFile(other)) {
                warnings.add("Found " + other.getFileName() + " but saved world metadata selects "
                        + lineage.label + "; published " + lineage.label + " defaults were used.");
            }
        }

        boolean hybridConfig = values.scalars.containsKey("place_mineralogy_rock")
                && values.scalars.containsKey("realistic_coal_layers");
        boolean enabled = lineage == Lineage.MINERALOGY_110
                ? true : bool(values, "place_mineralogy_rock", true);
        boolean realisticCoal = lineage == Lineage.MINERALOGY_110
                && bool(values, "realistic_coal_layers", false);
        int geomeSize = integer(values, "geome_size", 100, 4, Short.MAX_VALUE);
        double rockLayerNoise = decimal(values, "rock_layer_noise", 32.0D,
                1.0D, Short.MAX_VALUE);
        int layerThickness = integer(values, "rock_layer_thickness", 8, 1, 255);
        GeologyMode engine = lineage == Lineage.MINERALOGY_5
                ? geologyMode(values, warnings) : GeologyMode.LEGACY;

        List<String> igneous = effectiveList(lineage.igneous, values,
                "igneous_whitelist", "igneous_blacklist", lineage == Lineage.MINERALOGY_5);
        List<String> metamorphic = effectiveList(lineage.metamorphic, values,
                "metamorphic_whitelist", "metamorphic_blacklist", lineage == Lineage.MINERALOGY_5);
        List<String> sedimentaryBase;
        if (lineage == Lineage.MINERALOGY_110) {
            sedimentaryBase = new ArrayList<>(SEDIMENTARY_110_BEFORE_COAL);
            if (realisticCoal) sedimentaryBase.add("minecraft:coal_ore");
            sedimentaryBase.addAll(SEDIMENTARY_110_AFTER_COAL);
        } else {
            sedimentaryBase = new ArrayList<>(lineage.sedimentary);
        }
        List<String> sedimentary = effectiveList(sedimentaryBase, values,
                "sedimentary_whitelist", "sedimentary_blacklist",
                lineage == Lineage.MINERALOGY_5);

        JsonObject root = installedPackProfile.rootCopy();
        root.addProperty("geology_mode", engine.name().toLowerCase(Locale.ROOT));
        JsonObject cyano = root.has("cyano") && root.get("cyano").isJsonObject()
                ? root.getAsJsonObject("cyano") : new JsonObject();
        cyano.addProperty("enabled", enabled);
        cyano.addProperty("geome_size", geomeSize);
        cyano.addProperty("rock_layer_noise", rockLayerNoise);
        cyano.addProperty("rock_layer_thickness", layerThickness);
        cyano.addProperty("realistic_coal_layers", realisticCoal);
        cyano.addProperty("migrated_from", "mineralogy-" + identity.version);
        cyano.addProperty("legacy_lineage", lineage.label);
        cyano.addProperty("legacy_engine", engine.name().toLowerCase(Locale.ROOT));
        cyano.addProperty("legacy_metadata_source", identity.sourceFile);
        cyano.addProperty("legacy_config_source", configPath.toAbsolutePath().toString());
        cyano.addProperty("legacy_config_found", configFound);
        cyano.addProperty("hybrid_config", hybridConfig);
        cyano.add("igneous_rocks", array(igneous));
        cyano.add("metamorphic_rocks", array(metamorphic));
        cyano.add("sedimentary_rocks", array(sedimentary));
        for (String key : LIST_KEYS) cyano.add(key, array(values.list(key)));
        root.add("cyano", cyano);

        writeUpgradeReport(worldRoot, configPath, identity, lineage, engine,
                configFound, hybridConfig, enabled, geomeSize, rockLayerNoise,
                layerThickness, realisticCoal, values, igneous, metamorphic,
                sedimentary, warnings);

        LOGGER.info("Existing Mineralogy {} world detected from {}; pinned OreSpawn to {} "
                + "behavior (engine={}, enabled={}, geomeSize={}, layerNoise={}, "
                + "layerThickness={}, realisticCoal={}, configFound={})",
                identity.version, identity.sourceFile, lineage.label, engine, enabled,
                geomeSize, rockLayerNoise, layerThickness, realisticCoal, configFound);
        return WorldGeologyProfile.fromJson(root, installedPackProfile);
    }

    private static void writeUpgradeReport(Path worldRoot, Path configPath,
            MineralogyIdentity identity, Lineage lineage, GeologyMode engine,
            boolean configFound, boolean hybridConfig, boolean enabled,
            int geomeSize, double rockLayerNoise, int layerThickness,
            boolean realisticCoal, ConfigValues values, List<String> igneous,
            List<String> metamorphic, List<String> sedimentary, List<String> warnings) {
        Path report = worldRoot.resolve("serverconfig/orespawn-upgrade-report.txt");
        List<String> missing = missingBlocks(igneous, metamorphic, sedimentary);
        List<String> lines = new ArrayList<>();
        lines.add("OreSpawn 4.0.16.2602002 Upgrade Report");
        lines.add("================================");
        lines.add("");
        lines.add("RESULT: Existing Mineralogy " + identity.version + " world detected.");
        lines.add(enabled
                ? "Geology remains on the " + engineLabel(engine) + " using "
                        + lineage.label + " behavior."
                : "Legacy Mineralogy geology was disabled and remains disabled for this world.");
        lines.add("This prevents an implicit settings change between old and newly generated chunks.");
        lines.add("");
        lines.add("Legacy world detection");
        lines.add("- Saved mod metadata: " + identity.sourceFile);
        lines.add("- Saved Mineralogy version: " + identity.version);
        lines.add("- Selected config lineage: " + lineage.label);
        lines.add("- Selected engine: " + engine.name());
        lines.add("- Hybrid 1.10/1.12 keys found: " + hybridConfig);
        lines.add("");
        lines.add("Legacy Mineralogy configuration");
        lines.add("- Source: " + configPath.toAbsolutePath());
        lines.add("- Source file found: " + (configFound ? "yes"
                : "no; published " + lineage.label + " defaults used"));
        lines.add("- Geology enabled: " + enabled);
        lines.add("- Geome size: " + geomeSize);
        lines.add("- Rock layer noise: " + rockLayerNoise);
        lines.add("- Rock layer thickness: " + layerThickness);
        lines.add("- Realistic coal layers: " + realisticCoal
                + (lineage == Lineage.MINERALOGY_110 ? "" : " (not used by this lineage)"));
        for (String key : LIST_KEYS) {
            lines.add("- " + key + " (" + values.list(key).size() + "): "
                    + String.join(", ", values.list(key)));
        }
        lines.add("");
        lines.add("Effective rock outputs");
        lines.add("- Igneous order (" + igneous.size() + "): " + String.join(", ", igneous));
        lines.add("- Metamorphic order (" + metamorphic.size() + "): "
                + String.join(", ", metamorphic));
        lines.add("- Sedimentary order (" + sedimentary.size() + "): "
                + String.join(", ", sedimentary));
        lines.add("");
        if (missing.isEmpty() && warnings.isEmpty()) {
            lines.add("WARNINGS: None. Every preserved rock ID is registered.");
        } else {
            lines.add("WARNINGS:");
            for (String warning : warnings) lines.add("- " + warning);
            for (String id : missing) lines.add("- Rock ID is not currently registered: " + id);
        }
        lines.add("");
        lines.add("OreSpawn did not rewrite the source Mineralogy configuration or existing chunks.");
        lines.add("The generated OreSpawn world profile and this report are written atomically and are byte-stable on reload.");
        lines.add("To change this world's geology later, make that choice explicitly and expect a generation seam.");
        writeTextAtomically(report, lines);
    }

    private static String engineLabel(GeologyMode engine) {
        return engine == GeologyMode.LEGACY ? "Cyano layer engine" : "Mineralogy geome engine";
    }

    @SafeVarargs
    private static List<String> missingBlocks(List<String>... families) {
        Set<String> missing = new LinkedHashSet<>();
        for (List<String> family : families) {
            for (String idText : family) {
                try {
                    Identifier id = Identifier.parse(idText);
                    if (!BuiltInRegistries.BLOCK.containsKey(id)) missing.add(id.toString());
                } catch (RuntimeException e) {
                    missing.add(idText + " (invalid registry name)");
                }
            }
        }
        return new ArrayList<>(missing);
    }

    private static void writeTextAtomically(Path report, List<String> lines) {
        Path temporary = report.resolveSibling(report.getFileName().toString() + ".tmp");
        try {
            Files.createDirectories(report.getParent());
            byte[] data = (String.join(System.lineSeparator(), lines) + System.lineSeparator())
                    .getBytes(StandardCharsets.UTF_8);
            if (Files.isRegularFile(report) && Arrays.equals(Files.readAllBytes(report), data)) return;
            Files.write(temporary, data);
            try {
                Files.move(temporary, report, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, report, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            LOGGER.warn("Could not write legacy Mineralogy upgrade report '{}'", report, e);
        }
    }

    private static boolean hasGeneratedOverworldChunks(Path worldRoot) {
        Path regions = worldRoot.resolve("region");
        if (!Files.isDirectory(regions)) return false;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(regions, "r.*.*.mca")) {
            return files.iterator().hasNext();
        } catch (IOException e) {
            LOGGER.warn("Could not inspect existing world regions in '{}'", regions, e);
            return false;
        }
    }

    private static MineralogyIdentity legacyMineralogyIdentity(Path worldRoot) {
        for (String fileName : new String[] { "level.dat", "level.dat_old" }) {
            Path levelDat = worldRoot.resolve(fileName);
            if (!Files.isRegularFile(levelDat)) continue;
            try (FileInputStream input = new FileInputStream(levelDat.toFile())) {
                CompoundTag root = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap());
                MineralogyIdentity identity = identity(root, fileName);
                if (identity != null) return identity.legacy ? identity : null;
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Could not inspect '{}' for legacy Mineralogy metadata", levelDat, e);
            }
        }
        return null;
    }

    private static MineralogyIdentity identity(CompoundTag root, String sourceFile) {
        for (ModListPath path : MOD_LIST_PATHS) {
            CompoundTag container = root.getCompoundOrEmpty(path.compound);
            ListTag mods = container.getListOrEmpty(path.list);
            for (int i = 0; i < mods.size(); i++) {
                CompoundTag mod = mods.getCompoundOrEmpty(i);
                String id = firstNonBlank(mod.getStringOr("ModId", ""), mod.getStringOr("modid", ""));
                if (!"mineralogy".equalsIgnoreCase(id)) continue;
                String version = firstNonBlank(mod.getStringOr("ModVersion", ""), mod.getStringOr("version", "")).trim();
                return new MineralogyIdentity(version.isEmpty() ? "legacy" : version,
                        sourceFile + " (" + path.compound + "/" + path.list + ")",
                        isLegacyVersion(version));
            }
        }
        return null;
    }

    private static boolean isLegacyVersion(String version) {
        if (version == null || version.trim().isEmpty() || "legacy".equalsIgnoreCase(version)) return true;
        List<Integer> parts = versionParts(version);
        return !parts.isEmpty() && (parts.get(0) == 3 || parts.get(0) == 5);
    }

    private static List<Integer> versionParts(String version) {
        List<Integer> result = new ArrayList<>();
        if (version == null) return result;
        for (String text : version.split("[^0-9]+")) {
            if (text.isEmpty()) continue;
            try { result.add(Integer.parseInt(text)); }
            catch (NumberFormatException ignored) { }
        }
        return result;
    }

    private static ConfigValues readForgeCfg(Path path) {
        ConfigValues values = new ConfigValues();
        if (!Files.isRegularFile(path)) return values;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.length() < 4 || trimmed.charAt(1) != ':') continue;
                char type = Character.toUpperCase(trimmed.charAt(0));
                if (type != 'B' && type != 'I' && type != 'D' && type != 'S') continue;
                int equals = trimmed.indexOf('=', 2);
                if (equals <= 2) continue;
                String key = normalizeKey(trimmed.substring(2, equals));
                String value = trimmed.substring(equals + 1).trim();
                if (isListKey(key)) values.lists.put(key, parseDelimitedList(value, ";"));
                else values.scalars.put(key, value);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read legacy Mineralogy configuration '{}'; using published defaults", path, e);
            values.clear();
        }
        return values;
    }

    private static ConfigValues readToml(Path path) {
        ConfigValues values = new ConfigValues();
        if (!Files.isRegularFile(path)) return values;
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = stripTomlComment(lines.get(i)).trim();
                if (line.isEmpty() || line.startsWith("[")) continue;
                int equals = indexOutsideQuotes(line, '=');
                if (equals <= 0) continue;
                String key = normalizeKey(line.substring(0, equals));
                String value = line.substring(equals + 1).trim();
                if (value.startsWith("[") && !arrayComplete(value)) {
                    StringBuilder joined = new StringBuilder(value);
                    while (++i < lines.size()) {
                        joined.append(' ').append(stripTomlComment(lines.get(i)).trim());
                        if (arrayComplete(joined.toString())) break;
                    }
                    value = joined.toString();
                }
                if (isListKey(key)) values.lists.put(key, parseTomlArray(value));
                else values.scalars.put(key, unquote(value));
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read legacy Mineralogy TOML configuration '{}'; using published defaults", path, e);
            values.clear();
        }
        return values;
    }

    private static String stripTomlComment(String line) {
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && quoted) { escaped = true; continue; }
            if (c == '"') quoted = !quoted;
            else if (c == '#' && !quoted) return line.substring(0, i);
        }
        return line;
    }

    private static int indexOutsideQuotes(String text, char wanted) {
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) quoted = !quoted;
            if (c == wanted && !quoted) return i;
        }
        return -1;
    }

    private static boolean arrayComplete(String text) {
        boolean quoted = false;
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) quoted = !quoted;
            if (!quoted && c == '[') depth++;
            if (!quoted && c == ']') depth--;
        }
        return depth <= 0 && !quoted;
    }

    private static List<String> parseTomlArray(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return Collections.emptyList();
        trimmed = trimmed.substring(1, trimmed.length() - 1);
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (escaped) { current.append(c); escaped = false; continue; }
            if (c == '\\' && quoted) { escaped = true; continue; }
            if (c == '"') { quoted = !quoted; continue; }
            if (c == ',' && !quoted) {
                addConfiguredId(result, current.toString());
                current.setLength(0);
            } else current.append(c);
        }
        addConfiguredId(result, current.toString());
        return result;
    }

    private static List<String> parseDelimitedList(String value, String delimiter) {
        List<String> result = new ArrayList<>();
        for (String entry : value.split(java.util.regex.Pattern.quote(delimiter), -1)) {
            addConfiguredId(result, entry);
        }
        return result;
    }

    private static void addConfiguredId(List<String> result, String raw) {
        String value = unquote(raw.trim());
        if (value.isEmpty()) return;
        try { result.add(Identifier.parse(value).toString()); }
        catch (RuntimeException e) {
            LOGGER.warn("Ignoring invalid legacy Mineralogy rock registry name '{}'", value);
        }
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1)
                    .replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return trimmed;
    }

    private static GeologyMode geologyMode(ConfigValues values, List<String> warnings) {
        String configured = values.scalar("geology_mode");
        if (configured == null || configured.trim().isEmpty()) return GeologyMode.GEOME;
        try { return GeologyMode.valueOf(configured.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            warnings.add("Invalid GEOLOGY_MODE '" + configured + "'; published GEOME default used.");
            return GeologyMode.GEOME;
        }
    }

    private static List<String> effectiveList(List<String> defaults, ConfigValues values,
            String whitelistKey, String blacklistKey, boolean deduplicateWhitelist) {
        List<String> result = new ArrayList<>(defaults);
        for (String id : values.list(whitelistKey)) {
            if (!deduplicateWhitelist || !result.contains(id)) result.add(id);
        }
        for (String id : values.list(blacklistKey)) result.remove(id);
        return result;
    }

    private static int integer(ConfigValues values, String key, int fallback, int min, int max) {
        try {
            int value = values.scalar(key) == null ? fallback : Integer.parseInt(values.scalar(key));
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException e) { return fallback; }
    }

    private static double decimal(ConfigValues values, String key,
            double fallback, double min, double max) {
        try {
            double value = values.scalar(key) == null ? fallback : Double.parseDouble(values.scalar(key));
            return Math.max(min, Math.min(max, value));
        } catch (RuntimeException e) { return fallback; }
    }

    private static boolean bool(ConfigValues values, String key, boolean fallback) {
        String value = values.scalar(key);
        if (value == null) return fallback;
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return fallback;
    }

    private static JsonArray array(List<String> values) {
        JsonArray result = new JsonArray();
        for (String value : values) result.add(new JsonPrimitive(value));
        return result;
    }

    private static String normalizeKey(String value) {
        return unquote(value).trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static boolean isListKey(String key) {
        for (String candidate : LIST_KEYS) if (candidate.equals(key)) return true;
        return false;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.trim().isEmpty() ? first : second == null ? "" : second;
    }

    private static List<String> list(String... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }

    private static final List<String> LIST_KEYS = list(
            "igneous_whitelist", "igneous_blacklist",
            "metamorphic_whitelist", "metamorphic_blacklist",
            "sedimentary_whitelist", "sedimentary_blacklist");

    private static final List<ModListPath> MOD_LIST_PATHS = Arrays.asList(
            new ModListPath("fml", "LoadingModList"),
            new ModListPath("fml", "ModList"),
            new ModListPath("FML", "ModList"));

    private enum Lineage {
        MINERALOGY_110("Mineralogy 1.10", IGNEOUS_110, METAMORPHIC_110,
                Collections.<String>emptyList()),
        MINERALOGY_112("Mineralogy 1.12", IGNEOUS_112, METAMORPHIC_112, SEDIMENTARY_112),
        MINERALOGY_5("Mineralogy 5.x", IGNEOUS_5, METAMORPHIC_5, SEDIMENTARY_5);

        final String label;
        final List<String> igneous;
        final List<String> metamorphic;
        final List<String> sedimentary;

        Lineage(String label, List<String> igneous, List<String> metamorphic,
                List<String> sedimentary) {
            this.label = label;
            this.igneous = igneous;
            this.metamorphic = metamorphic;
            this.sedimentary = sedimentary;
        }

        static Lineage forVersion(String version) {
            List<Integer> parts = versionParts(version);
            if (!parts.isEmpty() && parts.get(0) == 5) return MINERALOGY_5;
            if (parts.size() >= 2 && parts.get(0) == 3 && parts.get(1) <= 3) {
                return MINERALOGY_110;
            }
            return MINERALOGY_112;
        }
    }

    private static final class ConfigValues {
        final Map<String, String> scalars = new LinkedHashMap<>();
        final Map<String, List<String>> lists = new LinkedHashMap<>();
        String scalar(String key) { return scalars.get(normalizeKey(key)); }
        List<String> list(String key) {
            List<String> value = lists.get(normalizeKey(key));
            return value == null ? Collections.<String>emptyList() : Collections.unmodifiableList(value);
        }
        void clear() { scalars.clear(); lists.clear(); }
    }

    private static final class ModListPath {
        final String compound;
        final String list;
        ModListPath(String compound, String list) { this.compound = compound; this.list = list; }
    }

    private static final class MineralogyIdentity {
        final String version;
        final String sourceFile;
        final boolean legacy;
        MineralogyIdentity(String version, String sourceFile, boolean legacy) {
            this.version = version;
            this.sourceFile = sourceFile;
            this.legacy = legacy;
        }
    }
}
