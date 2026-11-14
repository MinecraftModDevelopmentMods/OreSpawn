package zone.moddev.mc.orespawn.commands.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;
import zone.moddev.mc.orespawn.worldgen.WorldIds;

/** One cooperative server job; the file worker never receives a world or chunk. */
public final class AdminJobs {
    public static final int MAX_VISITS = 16384;
    public static final long TICK_NANOS = 4000000L;
    private static final Map<String, JsonObject> HISTORY = new LinkedHashMap<>();
    private static final Map<String, Preview> PREVIEWS = new LinkedHashMap<>();
    private static ExecutorService files;
    private static Job active;
    private AdminJobs() { }

    private static ExecutorService files() {
        if (files == null || files.isShutdown()) files = newFileWorker();
        return files;
    }

    static ExecutorService newFileWorker() {
        return new java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.ArrayBlockingQueue<>(32), task -> {
            Thread thread = new Thread(task, "OreSpawn diagnostic files"); thread.setDaemon(true); return thread;
        }, new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    }

    private static Future<?> queueReport(java.util.concurrent.Callable<Void> writer) {
        try { return files().submit(writer); }
        catch (java.util.concurrent.RejectedExecutionException e) {
            java.util.concurrent.CompletableFuture<Void> failed = new java.util.concurrent.CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    public static String owner(AdminSource sender) {
        return sender.getCommandSenderEntity() == null ? "console:" + sender.getName()
                : sender.getCommandSenderEntity().getUniqueID().toString();
    }

    public static Path root(WorldServer world) {
        return AdminWorldAccess.overworld(world.getServer()).getSaveHandler().getWorldDirectory()
                .toPath().toAbsolutePath().normalize().resolve("orespawn/diagnostics");
    }

    private static String worldKey(WorldServer world) {
        return DiagnosticFiles.hash((root(world).toString() + ":" + world.getSeed()).getBytes(StandardCharsets.UTF_8));
    }

    public static String start(AdminSource sender, WorldServer world, int cx, int cz,
            int radius, int minY, int maxY, String filter, boolean preview) {
        ensureIdle();
        if (preview) safePlayers(sender, world, cx, cz);
        Job job = new Job(sender, world, cx, cz, radius, minY, maxY, filter, preview ? "clear_preview" : "survey");
        active = job;
        return job.id;
    }

    public static String undo(AdminSource sender, WorldServer world, String backupId) {
        ensureIdle();
        Path backup = DiagnosticFiles.child(root(world).resolve("backups"), backupId).resolve("journal.json");
        Job job = new Job(sender, world, 0, 0, 0, 0, world.getHeight() - 1, "", "undo_read");
        job.future = files().submit(() -> EditJournal.read(DiagnosticFiles.readBackup(backup)));
        job.backupId = backupId;
        active = job;
        return job.id;
    }

    public static String confirm(AdminSource sender, String token, boolean undo) {
        ensureIdle();
        Preview preview = PREVIEWS.get(token);
        if (preview == null || preview.undo != undo || !preview.lease.accepts(owner(sender), profileHash(), System.nanoTime()))
            throw new IllegalArgumentException("Preview expired, profile changed or operator does not match");
        safePlayers(sender, preview.world, preview.cx, preview.cz);
        PREVIEWS.remove(token);
        Job job = new Job(sender, preview.world, preview.cx, preview.cz, 0, preview.minY, preview.maxY, "", "verify");
        if (!job.catalog.fingerprint().equals(preview.catalogFingerprint)) throw new IllegalArgumentException("Material identities changed; request a new preview");
        job.journal = preview.journal;
        job.expectedHash = preview.snapshot;
        job.undo = undo;
        job.backupId = undo ? preview.backupId : job.id;
        active = job;
        return job.id;
    }

    public static String confirmUndo(AdminSource sender, String backupId, String token) {
        Preview preview = PREVIEWS.get(token);
        if (preview == null || !backupId.equals(preview.backupId)) throw new IllegalArgumentException("Backup does not match this preview");
        return confirm(sender, token, true);
    }

    public static boolean cancelPreview(AdminSource sender, String token) {
        if (!sender.canUseCommand(4, "orespawn")) throw new IllegalArgumentException("Permission level 4 is required");
        Preview preview = PREVIEWS.get(token);
        if (preview == null) return false;
        if (!preview.owner.equals(owner(sender))) throw new IllegalArgumentException("This preview belongs to another operator");
        PREVIEWS.remove(token);
        return true;
    }

    private static void ensureIdle() { if (active != null) throw new IllegalStateException("A diagnostic job is already running"); }
    private static String profileHash() { return DiagnosticFiles.fingerprint(WorldGeologyProfileManager.activeProfile().toJson()); }

    static void safePlayers(AdminSource sender, WorldServer world, int cx, int cz) {
        if (!sender.canUseCommand(4, "orespawn")) throw new IllegalArgumentException("Permission level 4 is required");
        if (sender.getCommandSenderEntity() instanceof EntityPlayerMP && !((EntityPlayerMP) sender.getCommandSenderEntity()).isSpectator()
                && !((EntityPlayerMP) sender.getCommandSenderEntity()).isCreative()) throw new IllegalArgumentException("Use Creative or Spectator mode");
        for (net.minecraft.entity.player.EntityPlayer player : world.playerEntities)
            if ((player.getPosition().getX() >> 4) == cx && (player.getPosition().getZ() >> 4) == cz
                    && !player.isSpectator() && !player.isCreative())
                throw new IllegalArgumentException("A Survival or Adventure player occupies this chunk");
    }

    public static JsonObject jobs(String id) {
        if (id != null) {
            if (active != null && active.id.equals(id)) return active.summary();
            JsonObject result = HISTORY.get(id);
            if (result == null) throw new IllegalArgumentException("Unknown job; saved reports remain in the world's diagnostics directory");
            return zone.moddev.mc.orespawn.util.JsonCopies.copy(result);
        }
        JsonObject result = new JsonObject();
        if (active != null) result.add(active.id, active.summary());
        HISTORY.forEach(result::add);
        return result;
    }

    public static String publish(AdminSource sender, WorldServer world, JsonObject value) {
        String id = UUID.randomUUID().toString();
        Path directory = DiagnosticFiles.child(root(world).resolve("reports"), id);
        JsonObject snapshot = zone.moddev.mc.orespawn.util.JsonCopies.copy(value);
        snapshot.addProperty("format", 1); snapshot.addProperty("id", id);
        snapshot.addProperty("generated_utc_ms", System.currentTimeMillis());
        snapshot.addProperty("profile", profileHash());
        Future<?> output = queueReport(() -> { DiagnosticFiles.write(directory.resolve("report.json"), snapshot); return null; });
        JsonObject summary = new JsonObject(); summary.addProperty("status", "complete");
        summary.addProperty("export", "pending"); summary.addProperty("report_directory", directory.toString());
        exportResults.put(id, output); HISTORY.put(id, summary);
        while (HISTORY.size() > 32) { String oldest = HISTORY.keySet().iterator().next(); HISTORY.remove(oldest); exportResults.remove(oldest); }
        sender.sendMessage(new TextComponentTranslation("commands.orespawn.report", id, directory.toString()));
        return id;
    }

    public static void listBackups(AdminSource sender, WorldServer world) {
        Path directory = root(world).resolve("backups");
        sender.sendMessage(new TextComponentTranslation("commands.orespawn.backups", directory.toString()));
    }

    public static void cancel(AdminSource sender, String id) {
        if (active == null || !active.id.equals(id)) throw new IllegalArgumentException("No such running job");
        if (!owner(sender).equals(owner(active.sender)) && !sender.canUseCommand(4, "orespawn"))
            throw new IllegalArgumentException("Only the owner or a level 4 operator can cancel this job");
        active.requestStop("cancelled", "Cancelled; any partial edit remains in its backup journal");
    }

    public static void tick() {
        long deadline = System.nanoTime() + TICK_NANOS;
        int visits = AdminLighting.tick(deadline, MAX_VISITS);
        if (active == null) return;
        Job job = active;
        try {
            while (active == job && visits < MAX_VISITS && System.nanoTime() < deadline) {
                if (!job.step()) break;
                visits++;
            }
            job.maxVisits = Math.max(job.maxVisits, visits);
        } catch (Exception e) { job.requestStop("failed", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
    }

    public static void unload(net.minecraft.world.World world) {
        if (!(world instanceof WorldServer)) return;
        if (active != null && active.world == world) active.finish("interrupted", "Dimension unloaded; no automatic resume");
        PREVIEWS.values().removeIf(preview -> preview.world == world);
    }

    public static void shutdown() {
        if (active != null) active.finish("interrupted", "Server stopped; no automatic resume");
        PREVIEWS.clear(); HISTORY.clear(); exportResults.clear();
        if (files != null) files.shutdown();
    }

    private static final class Preview {
        final WorldServer world;
        final int cx, cz, minY, maxY;
        final String owner, snapshot, profile, backupId, catalogFingerprint;
        final EditJournal journal;
        final boolean undo;
        final PreviewLease lease;
        Preview(Job job) {
            world = job.world; cx = job.cx; cz = job.cz; minY = job.minY; maxY = job.maxY;
            owner = owner(job.sender); snapshot = job.hash(); profile = job.profile; journal = job.journal;
            undo = job.undo; backupId = job.backupId;
            catalogFingerprint = job.catalog.fingerprint();
            lease = new PreviewLease(owner, profile, System.nanoTime());
        }
    }

    private static final class Job {
        final String id = UUID.randomUUID().toString();
        final AdminSource sender;
        final WorldServer world;
        final long started = System.currentTimeMillis();
        final String profile;
        final String filter;
        final java.util.function.Predicate<IBlockState> filterTest;
        final DiagnosticCatalog catalog;
        final Path directory;
        final String dimension;
        final SurveyCounts counts = new SurveyCounts();
        final JsonArray skipped = new JsonArray();
        final List<ChunkPos> positions = new ArrayList<>();
        final List<EditJournal.Cell> changes = new ArrayList<>();
        final Map<IBlockState, String> stateKeys = new IdentityHashMap<>();
        final Map<String, IBlockState> resolved = new java.util.HashMap<>();
        int cx, cz, minY, maxY, position, cursor, edited, editCursor, lightCursor, sampledChunks, maxVisits;
        String phase, expectedHash, backupId, finishedHash;
        String terminalStatus = "complete", terminalMessage;
        boolean undo, changed, scanningUndo, lightingComplete;
        Chunk chunk;
        MessageDigest digest;
        EditJournal journal;
        Future<?> future;

        Job(AdminSource sender, WorldServer world, int cx, int cz, int radius,
                int minY, int maxY, String filter, String phase) {
            this.sender = sender; this.world = world; this.cx = cx; this.cz = cz;
            this.minY = minY; this.maxY = maxY; this.filter = filter; this.phase = phase;
            this.filterTest = DiagnosticStates.filter(filter);
            this.profile = profileHash();
            this.directory = root(world); this.dimension = WorldIds.dimension(world).toString();
            this.catalog = new DiagnosticCatalog(WorldGeologyProfileManager.activeProfile().toJson());
            for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) positions.add(new ChunkPos(x, z));
            resetDigest();
        }

        private void resetDigest() {
            try { digest = MessageDigest.getInstance("SHA-256"); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
            finishedHash = null;
        }
        String hash() {
            if (finishedHash == null) {
                byte[] value = digest.digest();
                StringBuilder result = new StringBuilder();
                for (byte item : value) result.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
                finishedHash = result.toString();
            }
            return finishedHash;
        }
        private String key(IBlockState state) { return stateKeys.computeIfAbsent(state, DiagnosticStates::key); }
        private BlockPos pos(int index) { return new BlockPos((cx << 4) + (index & 15), index >>> 8, (cz << 4) + ((index >>> 4) & 15)); }
        private boolean loaded() { return chunk != null && AdminWorldAccess.loaded(world, cx, cz) == chunk; }

        boolean step() throws Exception {
            if (world.getServer() != WorldGeologyProfileManager.activeServer()) throw new IOException("World is no longer active");
            if ("undo_read".equals(phase)) {
                if (!future.isDone()) return false;
                journal = (EditJournal) future.get();
                if (!worldKey(world).equals(journal.header.get("world").getAsString())
                        || !WorldIds.dimension(world).toString().equals(journal.header.get("dimension").getAsString()))
                    throw new IOException("Backup belongs to another world or dimension");
                cx = journal.header.get("chunk_x").getAsInt(); cz = journal.header.get("chunk_z").getAsInt();
                positions.clear(); positions.add(new ChunkPos(cx, cz));
                safePlayers(sender, world, cx, cz);
                undo = true; scanningUndo = true; phase = "undo_preview";
                return true;
            }
            if ("backup".equals(phase)) {
                if (!future.isDone()) return false;
                future.get();
                cursor = 0; position = 0; chunk = null; resetDigest(); phase = "verify_after_backup";
                return true;
            }
            if ("edit".equals(phase)) return edit();
            if ("light".equals(phase)) {
                if (!loaded()) throw new IOException("Chunk unloaded during lighting; use the retained journal to recover");
                if (lightCursor < journal.cells.size()) {
                    if (!world.checkLight(pos(journal.cells.get(lightCursor++).index))) throw new IOException("Lighting area unloaded; cleanup remains pending");
                    return true;
                }
                lightingComplete = true;
                 chunk.markDirty();
                finish(terminalStatus, terminalMessage == null ? "Changed " + edited + " cells; backup " + backupId : terminalMessage); return false;
            }
            if (scanningUndo) {
                if (chunk == null) chunk = AdminWorldAccess.loaded(world, cx, cz);
                if (!loaded()) throw new IOException("Undo requires the existing chunk to be loaded");
                if (editCursor < journal.cells.size()) {
                    EditJournal.Cell cell = journal.cells.get(editCursor++);
                    resolved.computeIfAbsent(cell.before, DiagnosticStates::resolve);
                    resolved.computeIfAbsent(cell.after, DiagnosticStates::resolve);
                    IBlockState state = chunk.getBlockState(pos(cell.index));
                    if (!EditJournal.canUndo(key(state), cell) || state.getBlock().hasTileEntity(state)) throw new IOException("Undo conflict; no blocks restored");
                    return true;
                }
                scanningUndo = false; editCursor = 0;
            }
            return scan();
        }

        private boolean scan() throws Exception {
            boolean survey = "survey".equals(phase);
            if (position >= positions.size()) { scanned(); return false; }
            ChunkPos at = positions.get(position);
            cx = at.x; cz = at.z;
            if (chunk == null) {
                chunk = AdminWorldAccess.loaded(world, cx, cz);
                if (chunk == null) {
                    if (!survey) throw new IOException("Target chunk is not loaded");
                    skipped.add(new com.google.gson.JsonPrimitive(cx + "," + cz + ":unloaded")); position++; return true;
                }
                cursor = minY << 8;
            }
            if (!loaded()) {
                if (!survey) throw new IOException("Target chunk unloaded");
                skipped.add(new com.google.gson.JsonPrimitive(cx + "," + cz + ":unloaded_during_scan")); position++; chunk = null; return true;
            }
            if (cursor >= (maxY + 1) * 256) { sampledChunks++; position++; chunk = null; return true; }
            BlockPos pos = pos(cursor);
            IBlockState state = chunk.getBlockState(pos);
            String key = key(state);
            digest.update(key.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0);
            if (survey || "clear_preview".equals(phase)) {
                String category = catalog.category(state);
                if (matches(state)) {
                    Biome biome = chunk.getBiomes()[((cursor >>> 4) & 15) * 16 + (cursor & 15)];
                    counts.add(key, category, pos.getY(), biome == null ? "unknown" : String.valueOf(WorldIds.biome(biome)), cx, cz);
                }
                if (!survey && catalog.removable(state, state.getBlock().hasTileEntity(state) || chunk.getTileEntityMap().containsKey(pos))) {
                    if (!state.equals(resolved.computeIfAbsent(key, DiagnosticStates::resolve)))
                        throw new IOException("This state cannot be restored exactly: " + key);
                    changes.add(new EditJournal.Cell(cursor, key, key(Blocks.AIR.getDefaultState())));
                }
            }
            cursor++;
            return true;
        }

        private boolean matches(IBlockState state) { return filterTest.test(state); }

        private void scanned() throws Exception {
            if ("survey".equals(phase)) { finish(skipped.size() == 0 ? "complete" : "partial", "Measured blocks, not generator attribution"); return; }
            if ("clear_preview".equals(phase)) {
                JsonObject header = new JsonObject();
                header.addProperty("minecraft_version", "1.13.2");
                header.addProperty("orespawn_version", zone.moddev.mc.orespawn.OreSpawn.VERSION);
                header.addProperty("world", worldKey(world)); header.addProperty("dimension", WorldIds.dimension(world).toString());
                header.addProperty("chunk_x", cx); header.addProperty("chunk_z", cz);
                header.addProperty("profile", profile); header.addProperty("operator", owner(sender)); header.addProperty("snapshot", hash());
                header.addProperty("min_y", minY); header.addProperty("max_y", maxY);
                header.addProperty("catalog", catalog.fingerprint());
                journal = new EditJournal(header, changes);
            }
            if ("clear_preview".equals(phase) || "undo_preview".equals(phase)) {
                String token = UUID.randomUUID().toString();
                while (PREVIEWS.size() >= 32) PREVIEWS.remove(PREVIEWS.keySet().iterator().next());
                PREVIEWS.put(token, new Preview(this));
                JsonObject report = summary(); report.addProperty("confirmation_token", token);
                report.addProperty("warning", "This changes matching player-placed terrain too. Undo restores blocks, not entities or elapsed time. Use a disposable world.");
                report.addProperty("removal_cells", journal.cells.size());
                report.addProperty("preserved_cells", ((maxY - minY + 1) * 256) - journal.cells.size());
                finish("preview", "chunk=" + cx + "," + cz + "; y=" + minY + ".." + maxY + "; cells=" + journal.cells.size()
                        + "; preserved=" + (((maxY - minY + 1) * 256) - journal.cells.size())
                        + "; " + (undo ? "/orespawn undo " + backupId + " confirm " : "/orespawn clearchunk confirm ") + token, report);
                return;
            }
            if (!profile.equals(profileHash()) || !hash().equals(expectedHash)) throw new IOException("Snapshot or profile changed; request a new preview");
            if (!catalog.fingerprint().equals(new DiagnosticCatalog(WorldGeologyProfileManager.activeProfile().toJson()).fingerprint()))
                throw new IOException("Material identities changed; request a new preview");
            safePlayers(sender, world, cx, cz);
            chunk = AdminWorldAccess.loaded(world, cx, cz);
            if (!loaded()) throw new IOException("Chunk unloaded");
            if (!world.isAreaLoaded(new BlockPos((cx << 4) - 17, 0, (cz << 4) - 17),
                    new BlockPos((cx << 4) + 32, world.getHeight() - 1, (cz << 4) + 32), false))
                throw new IOException("Keep the surrounding lighting area loaded before editing");
            if (!undo && !"verify_after_backup".equals(phase)) {
                Path target = DiagnosticFiles.child(directory.resolve("backups"), backupId).resolve("journal.json");
                JsonObject payload = journal.json();
                future = files().submit(() -> { DiagnosticFiles.backup(target, payload); return null; }); phase = "backup";
            } else { phase = "edit"; editCursor = 0; }
        }

        private boolean edit() throws IOException {
            if (!loaded()) throw new IOException("Chunk unloaded; partial edit is recoverable from its journal");
            safePlayers(sender, world, cx, cz);
            if (editCursor >= journal.cells.size()) {
                if (!changed) { finish("complete", "No recorded cells needed changes; backup " + backupId); return false; }
                AdminWorldAccess.repairHeightmaps(chunk); chunk.markDirty(); phase = "light"; return true;
            }
            EditJournal.Cell cell = journal.cells.get(editCursor++);
            BlockPos pos = pos(cell.index);
            IBlockState state = chunk.getBlockState(pos);
            String current = key(state);
            if (undo && current.equals(cell.before)) return true;
            if (!current.equals(undo ? cell.after : cell.before) || state.getBlock().hasTileEntity(state)
                    || chunk.getTileEntityMap().containsKey(pos)) throw new IOException("Intervening edit; stopped without overwriting it");
            String destination = undo ? cell.before : cell.after;
            IBlockState replacement = resolved.computeIfAbsent(destination, DiagnosticStates::resolve);
            ChunkSection[] sections = chunk.getSections();
            int section = cell.index >>> 12;
            if (sections[section] == null) sections[section] = new ChunkSection(section << 4, !!world.dimension.hasSkyLight());
            sections[section].set(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, replacement);
            AdminLighting.changed(world, chunk, pos);

            changed = true; edited++; chunk.markDirty();
            return true;
        }

        JsonObject summary() {
            JsonObject result = new JsonObject();
            result.addProperty("format", 1); result.addProperty("id", id); result.addProperty("phase", phase);
            result.addProperty("owner", owner(sender)); result.addProperty("dimension", dimension);
            result.addProperty("chunk_x", cx); result.addProperty("chunk_z", cz);
            result.addProperty("min_y", minY); result.addProperty("max_y", maxY); result.addProperty("profile", profile);
            result.addProperty("started_utc_ms", started); result.addProperty("sampled_chunks", sampledChunks);
            result.addProperty("requested_chunks", positions.size()); result.addProperty("edited_cells", edited);
            result.addProperty("maximum_block_visits_per_tick", maxVisits);
            if (backupId != null) result.addProperty("backup_id", backupId);
            return result;
        }

        void requestStop(String status, String message) {
            if (active != this) return;
            if (changed && "light".equals(phase) && !"failed".equals(status)) {
                terminalStatus = status; terminalMessage = message;
                return;
            }
            if (changed && loaded() && !"light".equals(phase)) {
                terminalStatus = status; terminalMessage = message;
                AdminWorldAccess.repairHeightmaps(chunk); chunk.markDirty();
                lightCursor = 0; phase = "light";
                return;
            }
            finish(status, message);
        }

        void finish(String status, String message) { finish(status, message, summary()); }
        void finish(String status, String message, JsonObject report) {
            if (active != this) return;
            if (lightingComplete && chunk != null) AdminLighting.complete(world, chunk);
            report.addProperty("lighting_complete", !changed || lightingComplete);
            report.addProperty("lighting_recovery_pending", changed && chunk != null && AdminLighting.isPending(world, chunk));
            try { syncChunk(); }
            catch (RuntimeException e) {
                status = "failed";
                message = "The edit is journaled, but client synchronisation failed: " + e.getMessage();
                org.apache.logging.log4j.LogManager.getLogger().warn("Could not synchronise OreSpawn edit {}", id, e);
            }
            report.addProperty("status", status); report.addProperty("message", message);
            report.addProperty("finished_utc_ms", System.currentTimeMillis()); report.add("skipped_chunks", skipped);
            JsonObject saved = zone.moddev.mc.orespawn.util.JsonCopies.copy(report);
            SurveyCounts outputCounts = counts;
            Path target = DiagnosticFiles.child(directory.resolve("reports"), id);
            Future<?> output = queueReport(() -> {
                saved.add("counts", outputCounts.json());
                DiagnosticFiles.write(target.resolve("report.json"), saved);
                for (Map.Entry<String, String> file : outputCounts.csv().entrySet()) DiagnosticFiles.writeText(target.resolve(file.getKey()), file.getValue());
                return null;
            });
            report.addProperty("report_directory", target.toString());
            // I/O completion is visible through jobs; a pending export is never reported as written.
            report.addProperty("export", "pending");
            exportResults.put(id, output);
            HISTORY.put(id, report);
            while (HISTORY.size() > 32) { String oldest = HISTORY.keySet().iterator().next(); HISTORY.remove(oldest); exportResults.remove(oldest); }
            active = null;
            try {
                if ("preview".equals(status)) AdminFeedback.preview(sender, id, report.get("confirmation_token").getAsString(), undo, backupId,
                        dimension, cx, cz, minY, maxY, journal.cells.size(), ((maxY - minY + 1) * 256) - journal.cells.size());
                else if ("complete".equals(status) && backupId != null) AdminFeedback.completed(sender, id, edited, undo, backupId, dimension);
                else AdminFeedback.result(sender, id, status, message);
            }
            catch (RuntimeException e) { org.apache.logging.log4j.LogManager.getLogger().warn("OreSpawn job {} finished, but its operator feedback was unavailable", id, e); }
        }

        private void syncChunk() {
            if (changed && loaded()) {
                if (!lightingComplete) AdminWorldAccess.repairHeightmaps(chunk);
                chunk.markDirty();
                List<EntityPlayerMP> watchers = new ArrayList<>();
                for (net.minecraft.entity.player.EntityPlayer player : world.playerEntities)
                    if (player instanceof EntityPlayerMP && world.getPlayerChunkMap().isPlayerWatchingChunk((EntityPlayerMP) player, cx, cz))
                        watchers.add((EntityPlayerMP) player);
                if (!watchers.isEmpty()) {
                    SPacketChunkData packet = new SPacketChunkData(chunk, 65535);
                    watchers.forEach(player -> player.connection.sendPacket(packet));
                }
            }
        }
    }

    private static final Map<String, Future<?>> exportResults = new LinkedHashMap<>();
    public static void pollExports() {
        PREVIEWS.values().removeIf(preview -> preview.lease.expired(System.nanoTime()));
        for (Map.Entry<String, Future<?>> entry : exportResults.entrySet()) {
            if (!entry.getValue().isDone() || !HISTORY.containsKey(entry.getKey())) continue;
            JsonObject result = HISTORY.get(entry.getKey());
            if (!"pending".equals(result.get("export").getAsString())) continue;
            try { entry.getValue().get(); result.addProperty("export", "written"); }
            catch (Exception e) {
                result.addProperty("export", "failed: " + e.getMessage());
                org.apache.logging.log4j.LogManager.getLogger().warn("Could not export OreSpawn diagnostic report {}", entry.getKey(), e);
            }
        }
    }
}
