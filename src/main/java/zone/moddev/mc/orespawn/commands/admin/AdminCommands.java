package zone.moddev.mc.orespawn.commands.admin;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.worldgen.GeomeConfig;
import zone.moddev.mc.orespawn.worldgen.OreRetrogenManager;
import zone.moddev.mc.orespawn.worldgen.OreSpawnOreGeneration;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;
import zone.moddev.mc.orespawn.worldgen.WorldIds;

/** Target-native command adapter; selection never asks a chunk provider to load terrain. */
public final class AdminCommands {
    public static final List<String> ACTIONS = Arrays.asList("help", "status", "chunk", "survey", "explain", "retrogen", "jobs", "cancel", "clearchunk", "undo", "reload", "dump-biomes");
    private AdminCommands() { }

    public static boolean handle(MinecraftServer server, AdminSource sender, String[] args) throws AdminFailure {
        if (args.length == 0) return false;
        String action = args[0];
        boolean verbose = "status".equals(action) && args.length == 2 && "verbose".equals(args[1]);
        boolean retrogen = "retrogen".equals(action) && args.length > 1 && "status".equals(args[1]);
        if (!verbose && !retrogen && !Arrays.asList("help", "chunk", "survey", "explain", "jobs", "cancel", "clearchunk", "undo").contains(action)) return false;
        if (!sender.canUseCommand(2, "orespawn")) throw new AdminFailure("commands.generic.permission");
        if (("clearchunk".equals(action) || "undo".equals(action)) && !sender.canUseCommand(4, "orespawn"))
            throw new AdminFailure("commands.generic.permission");
        try {
            if ("help".equals(action)) {
                for (String key : new String[]{"help", "options", "safety", "scope"}) sender.sendMessage(new TextComponentTranslation("commands.orespawn." + key));
                return true;
            }
            if ("jobs".equals(action)) {
                if (args.length > 2) throw new IllegalArgumentException("Use jobs [job-id|backups]");
                if (args.length == 2 && "backups".equals(args[1])) AdminJobs.listBackups(sender, new Selection(sender, new String[0]).world);
                else data(sender, AdminJobs.jobs(args.length == 2 ? args[1] : null));
                return true;
            }
            if ("cancel".equals(action)) {
                if (args.length != 2) throw new IllegalArgumentException("Use cancel <job-id>");
                AdminJobs.cancel(sender, args[1]); return true;
            }
            if ("clearchunk".equals(action) && args.length == 3 && "confirm".equals(args[1])) {
                started(sender, AdminJobs.confirm(sender, args[2], false)); return true;
            }
            if ("clearchunk".equals(action) && args.length == 3 && "cancel".equals(args[1])) {
                boolean cancelled = AdminJobs.cancelPreview(sender, args[2]);
                sender.sendMessage(new TextComponentTranslation("commands.orespawn." + (cancelled ? "preview_cancelled" : "preview_inactive")));
                return true;
            }
            if ("undo".equals(action) && args.length == 4 && "confirm".equals(args[2])) {
                // The backup ID must match the preview as well as its token.
                started(sender, AdminJobs.confirmUndo(sender, args[1], args[3])); return true;
            }
            Selection selected = new Selection(sender, Arrays.copyOfRange(args, retrogen ? 2 : 1, args.length));
            if (verbose) {
                JsonObject data = status();
                data(sender, data);
                AdminJobs.publish(sender, selected.world, data);
            } else if (retrogen) {
                selected.chunkArguments();
                JsonObject data = OreRetrogenManager.diagnosticStatus(selected.world, new ChunkPos(selected.cx, selected.cz));
                data(sender, data);
                AdminJobs.publish(sender, selected.world, data);
            } else if ("chunk".equals(action)) {
                selected.chunkArguments();
                JsonObject data = chunk(selected);
                data(sender, data);
                AdminJobs.publish(sender, selected.world, data);
            } else if ("explain".equals(action)) {
                if (selected.words.size() != 1 && selected.words.size() != 4) throw new IllegalArgumentException("Use explain <rule-id> [x y z]");
                BlockPos position = selected.words.size() == 4 ? new BlockPos(integer(selected.words.get(1), -30000000, 30000000),
                        integer(selected.words.get(2), 0, selected.world.getHeight() - 1), integer(selected.words.get(3), -30000000, 30000000)) : selected.block;
                JsonObject data = OreSpawnOreGeneration.diagnosticRule(selected.world, position, new ResourceLocation(selected.words.get(0)));
                JsonObject chat = zone.moddev.mc.orespawn.util.JsonCopies.copy(data); chat.remove("configured_rule");
                data(sender, chat);
                AdminJobs.publish(sender, selected.world, data);
            } else if ("survey".equals(action)) {
                if (selected.words.size() > 1) throw new IllegalArgumentException("Use survey [radius]");
                int radius = selected.words.isEmpty() ? 1 : integer(selected.words.get(0), 0, 8);
                started(sender, AdminJobs.start(sender, selected.world, selected.cx, selected.cz, radius, selected.minY, selected.maxY, selected.filter, false));
            } else if ("clearchunk".equals(action)) {
                if (selected.words.size() == 1 && "preview".equals(selected.words.get(0))) selected.words.clear();
                selected.chunkArguments();
                if (!selected.filter.isEmpty()) throw new IllegalArgumentException("Block filters apply to surveys, not clearing");
                AdminJobs.start(sender, selected.world, selected.cx, selected.cz, 0, selected.minY, selected.maxY, "", true);
                sender.sendMessage(new TextComponentTranslation("commands.orespawn.preview_started"));
            } else if ("undo".equals(action)) {
                if (selected.words.size() != 1) throw new IllegalArgumentException("Use undo <backup-id> [--dimension id]");
                AdminJobs.undo(sender, selected.world, selected.words.get(0));
                sender.sendMessage(new TextComponentTranslation("commands.orespawn.preview_started"));
            }
            return true;
        } catch (RuntimeException e) { throw new AdminFailure("commands.orespawn.failed", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
    }

    private static void started(AdminSource sender, String id) { AdminFeedback.started(sender, id); }

    private static void data(AdminSource sender, JsonObject value) {
        // Chat is a short summary; the files keep the complete diagnostic data.
        String text = value.toString();
        if (text.length() > 1024) text = text.substring(0, 1021) + "...";
        sender.sendMessage(new TextComponentTranslation("commands.orespawn.data", text));
    }

    public static JsonObject status() {
        WorldGeologyProfile profile = WorldGeologyProfileManager.activeProfile();
        JsonObject data = new JsonObject();
        data.addProperty("version", OreSpawn.VERSION); data.addProperty("api", 1);
        data.addProperty("provider_schema", 5); data.addProperty("global_schema", 8); data.addProperty("world_schema", 7);
        data.addProperty("mode", profile.geologyMode().name()); data.addProperty("manage_vanilla_ores", profile.manageVanillaOres());
        data.addProperty("profile", DiagnosticFiles.fingerprint(profile.toJson()));
        data.addProperty("providers", zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager.activeProviderIds().toString());
        data.addProperty("generation_revision", profile.generationRevision());
        JsonObject versions = new JsonObject();
        for (String provider : zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager.activeProviderIds()) {
            java.util.Optional<? extends net.minecraftforge.fml.ModContainer> mod = net.minecraftforge.fml.ModList.get().getModContainerById(provider);
            versions.addProperty(provider, mod.isPresent() ? mod.get().getModInfo().getVersion().toString() : "provider ID is not a loaded mod ID");
        }
        data.add("provider_versions", versions);
        if (profile.toJson().has("formations")) data.add("formations", profile.toJson().get("formations"));
        return data;
    }

    private static JsonObject chunk(Selection selected) {
        Chunk chunk = AdminWorldAccess.loaded(selected.world, selected.cx, selected.cz);
        JsonObject data = new JsonObject();
        data.addProperty("dimension", WorldIds.dimension(selected.world).toString());
        data.addProperty("dimension_id", selected.world.dimension.getType().getId());
        data.addProperty("chunk_x", selected.cx); data.addProperty("chunk_z", selected.cz);
        data.addProperty("loaded", chunk != null);
        data.addProperty("geology_mode", WorldGeologyProfileManager.activeProfile().geologyMode().name());
        data.addProperty("predicted_base_geome", "unavailable");
        if (chunk == null) return data;
        data.addProperty("populated", chunk.getStatus().isAtLeast(net.minecraft.world.chunk.ChunkStatus.DECORATED)); data.addProperty("light_populated", chunk.getStatus().isAtLeast(net.minecraft.world.chunk.ChunkStatus.LIGHTED));
        data.addProperty("min_y", 0); data.addProperty("max_y", selected.world.getHeight() - 1);
        int low = Integer.MAX_VALUE, high = Integer.MIN_VALUE;
        Map<String, Integer> biomes = new TreeMap<>();
        for (int i = 0; i < 256; i++) {
            int height = AdminWorldAccess.height(chunk, i & 15, i >>> 4); low = Math.min(low, height); high = Math.max(high, height);
            Biome biome = chunk.getBiomes()[i];
            biomes.merge(biome == null ? "unknown" : String.valueOf(WorldIds.biome(biome)), 1, Integer::sum);
        }
        data.addProperty("lowest_surface_y", low - 1); data.addProperty("highest_surface_y", high - 1);
        data.add("observed_column_samples", columnSamples(chunk::getBlockState, selected.cx, selected.cz, selected.minY, selected.maxY));
        JsonObject histogram = new JsonObject(); biomes.forEach(histogram::addProperty); data.add("biome_columns", histogram);
        data.add("retrogen", OreRetrogenManager.diagnosticStatus(selected.world, new ChunkPos(selected.cx, selected.cz)));
        Biome biome = chunk.getBiomes()[136];
        zone.moddev.mc.orespawn.worldgen.BakedGeomeConfig config = GeomeConfig.baked(WorldIds.dimension(selected.world));
        if (config != null && biome != null && WorldGeologyProfileManager.activeProfile().geologyMode() == zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode.GEOME) {
            zone.moddev.mc.orespawn.worldgen.GeomeGeology.ColumnSample sample = new zone.moddev.mc.orespawn.worldgen.GeomeGeology(selected.world.getSeed(), config)
                    .sampleColumn(biome, WorldIds.biome(biome), (selected.cx << 4) + 8, (selected.cz << 4) + 8);
            data.addProperty("predicted_base_geome", sample.geomeName());
            data.addProperty("geology_note", "Current base-geology prediction, not saved historical provenance or private formation checks");
        }
        return data;
    }

    static com.google.gson.JsonArray columnSamples(java.util.function.Function<BlockPos, net.minecraft.block.state.IBlockState> states,
            int cx, int cz, int minY, int maxY) {
        com.google.gson.JsonArray samples = new com.google.gson.JsonArray();
        int x = (cx << 4) + 8, z = (cz << 4) + 8;
        for (int y = minY; ; y = Math.min(y + 16, maxY)) {
            JsonObject sample = new JsonObject();
            sample.addProperty("x", x); sample.addProperty("y", y); sample.addProperty("z", z);
            sample.addProperty("state", DiagnosticStates.key(states.apply(new BlockPos(x, y, z))));
            samples.add(sample);
            if (y == maxY) return samples;
        }
    }

    public static int integer(String text, int minimum, int maximum) {
        int number = Integer.parseInt(text);
        if (number < minimum || number > maximum) throw new IllegalArgumentException("Value outside " + minimum + ".." + maximum);
        return number;
    }

    private static MinecraftServer serverOf(AdminSource sender) { return WorldGeologyProfileManager.activeServer(); }

    static final class Selection {
        WorldServer world;
        BlockPos block;
        int cx, cz, minY, maxY;
        String filter = "";
        final List<String> words = new ArrayList<>();
        Selection(AdminSource sender, String[] args) {
            world = sender.getCommandSenderEntity() instanceof EntityPlayerMP ? (WorldServer) sender.getEntityWorld() : AdminWorldAccess.overworld(serverOf(sender));
            boolean center = false;
            for (int i = 0; i < args.length; i++) {
                String value = args[i];
                if ("--dimension".equals(value)) {
                    String id = args[++i]; WorldServer found = null;
                    for (WorldServer loaded : WorldGeologyProfileManager.activeServer().forgeGetWorldMap().values()) if (String.valueOf(loaded.dimension.getType().getId()).equals(id)
                            || WorldIds.dimension(loaded).toString().equals(id)) found = loaded;
                    if (found == null) throw new IllegalArgumentException("Dimension is not loaded");
                    world = found;
                } else if ("--center".equals(value)) { cx = integer(args[++i], -1874999, 1874999); cz = integer(args[++i], -1874999, 1874999); center = true; }
                else if ("--y".equals(value)) { minY = integer(args[++i], 0, 255); maxY = integer(args[++i], 0, 255) + 1; }
                else if ("--block".equals(value)) filter = args[++i];
                else if (value.startsWith("--")) throw new IllegalArgumentException("Unknown option " + value);
                else words.add(value);
            }
            if (world == null || world.getHeight() > 256) throw new IllegalArgumentException("Unsupported or inactive dimension");
            block = sender.getCommandSenderEntity() instanceof EntityPlayerMP && sender.getEntityWorld() == world ? sender.getPosition() : world.getSpawnPoint();
            if (!center) { cx = block.getX() >> 4; cz = block.getZ() >> 4; }
            maxY = maxY == 0 ? world.getHeight() - 1 : maxY - 1;
            if (minY > maxY || maxY >= world.getHeight()) throw new IllegalArgumentException("Invalid height range");
            if (!filter.isEmpty()) {
                com.mojang.brigadier.StringReader reader = new com.mojang.brigadier.StringReader(filter);
                try {
                    new net.minecraft.command.arguments.BlockStateParser(reader, false).parse(false);
                    if (reader.canRead()) throw new IllegalArgumentException("Unexpected block filter suffix");
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
                    throw new IllegalArgumentException("Invalid block filter: " + filter, ex);
                }
            }
        }
        void chunkArguments() {
            if (words.size() == 2) { cx = integer(words.get(0), -1874999, 1874999); cz = integer(words.get(1), -1874999, 1874999); }
            else if (!words.isEmpty()) throw new IllegalArgumentException("Use [chunkX chunkZ] or --center chunkX chunkZ");
        }
    }
}
