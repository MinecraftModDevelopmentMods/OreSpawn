package local.adminprobe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.oredict.OreDictionary;
import zone.moddev.mc.orespawn.commands.admin.AdminJobs;
import zone.moddev.mc.orespawn.commands.admin.DiagnosticCatalog;
import zone.moddev.mc.orespawn.commands.admin.DiagnosticFiles;
import zone.moddev.mc.orespawn.commands.admin.DiagnosticStates;
import zone.moddev.mc.orespawn.commands.admin.EditJournal;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

/** Destructive fixtures are confined to the explicitly named disposable save. */
@Mod(modid="admintoolsprobe", name="Local admin-tools qualification", version="1", dependencies="required-after:orespawn")
public final class AdminProbe {
    private MinecraftServer server;
    private WorldServer world;
    private Chunk chunk;
    private Sender sender;
    private int stage, ticks, loaded, maxObservedVisits, scheduledBefore, dropsBefore;
    private String pending, baseline, profile, rng, clearId, corruptId, token, initialConflict, retrogenBefore;
    private IBlockState depositState;
    private EditJournal journal;
    private boolean ready, lightBeforeRepeat;
    private final JsonObject evidence = new JsonObject();
    private final com.google.gson.JsonArray ambientDrops = new com.google.gson.JsonArray();
    private int scheduledAtEnd, dropsAtEnd;
    private String rngAtEnd;
    private long adminWindowStart;
    private long adminCpuStart;
    private final java.lang.management.ThreadMXBean threadClock = java.lang.management.ManagementFactory.getThreadMXBean();
    private final java.util.List<Long> activeTickNanos = new java.util.ArrayList<>();
    private final java.util.List<Long> activeCpuNanos = new java.util.ArrayList<>();
    private static final int CX = -40, CZ = -40;
    private static final int LEAF = (150 << 8) + 17;

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance().bus().register(this); MinecraftForge.EVENT_BUS.register(this);
    }
    @Mod.EventHandler public void before(FMLServerAboutToStartEvent event) {
        if (Boolean.getBoolean("admin.probe.collision")) ((net.minecraft.command.CommandHandler) event.getServer().getCommandManager()).registerCommand(new CommandBase() {
            public String getName() { return "clearchunks"; }
            public String getUsage(ICommandSender sender) { return "probe-owned"; }
            public void execute(MinecraftServer server, ICommandSender sender, String[] args) { }
        });
    }
    @Mod.EventHandler public void start(FMLServerStartedEvent event) throws Exception {
        server = FMLCommonHandler.instance().getMinecraftServerInstance(); world = server.getWorld(0);
        if (!world.getSaveHandler().getWorldDirectory().getName().equals("admin-tools-test")) throw new AssertionError("Not a disposable save");
        sender = new Sender("admin-probe", 4);
        for (int x = CX - 2; x <= CX + 2; x++) for (int z = CZ - 2; z <= CZ + 2; z++) world.getChunkProvider().provideChunk(x, z);
        chunk = world.getChunkProvider().getLoadedChunk(CX, CZ);
        world.getGameRules().setOrCreateGameRule("randomTickSpeed", "0");
        world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        world.getWorldInfo().setRaining(false); world.getWorldInfo().setRainTime(999999);
        OreDictionary.registerOre("oreAdminProbe", new ItemStack(Blocks.WOOL, 1, 3));
        Path checkpoint = java.nio.file.Paths.get("restart-checkpoint.json");
        if (Files.isRegularFile(checkpoint)) {
            JsonObject saved = DiagnosticFiles.read(checkpoint);
            baseline = saved.get("baseline").getAsString(); clearId = saved.get("backup").getAsString();
            stage = 30; pending = begin("orespawn undo " + clearId);
        } else {
            if (!chunk.getTileEntityMap().isEmpty()) throw new AssertionError("Fixture chunk unexpectedly has a block entity");
            for (int index = 0; index < 65536; index++) put(index, index < 256 ? Blocks.BEDROCK.getDefaultState()
                    : index < 32768 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState());
            put(8 << 8, Blocks.DIAMOND_ORE.getDefaultState()); put((8 << 8) + 1, Blocks.GOLD_BLOCK.getDefaultState());
            put((8 << 8) + 2, Blocks.WOOL.getStateFromMeta(3));
            put((8 << 8) + 4, Blocks.LAVA.getDefaultState());
            put((8 << 8) + 6, Blocks.GRASS.getDefaultState());
            put(250 << 8, Blocks.GRASS.getDefaultState());
            put(LEAF, Blocks.LEAVES.getDefaultState().withProperty(net.minecraft.block.BlockLeaves.CHECK_DECAY, true));
            put(LEAF + 1, Blocks.LOG.getDefaultState());
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : WorldGeologyProfileManager.activeProfile().toJson().getAsJsonObject("fluid_deposits").entrySet()) {
                JsonObject rule = entry.getValue().getAsJsonObject();
                if (rule.has("block")) {
                    net.minecraft.block.Block block = net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.getValue(new net.minecraft.util.ResourceLocation(rule.get("block").getAsString()));
                    if (block != null && block != Blocks.AIR) { depositState = block.getDefaultState(); put((8 << 8) + 3, depositState); break; }
                }
            }
            world.setBlockState(pos((8 << 8) + 5), Blocks.CHEST.getDefaultState(), 2);
            ((TileEntityChest) world.getTileEntity(pos((8 << 8) + 5))).setInventorySlotContents(0, new ItemStack(Items.COAL, 37));
            java.util.Arrays.fill(chunk.getBiomeArray(), (byte) 2);
            for (int i = 0; i < 64; i++) chunk.getBiomeArray()[i] = 1;
            chunk.generateSkylightMap(); chunk.setModified(true);
            world.checkLight(pos((8 << 8) + 4));
            require(chunk.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, pos((8 << 8) + 4)) == 15, "Lava-light fixture was not established");
            baseline = hash(); profile = WorldGeologyProfileManager.activeProfile().toJson().toString();
            loaded = world.getChunkProvider().getLoadedChunkCount(); rng = randomState();
            ProbeOperator unsafePlayer = new ProbeOperator(); unsafePlayer.setPosition(CX * 16 + 8, 130, CZ * 16 + 8);
            require(server.getCommandManager().executeCommand(unsafePlayer, "orespawn clearchunk --center " + CX + " " + CZ) == 0, "Survival operator accepted");
            world.playerEntities.add(unsafePlayer);
            try { require(server.getCommandManager().executeCommand(sender, "orespawn clearchunk --center " + CX + " " + CZ) == 0, "Occupied Survival chunk accepted"); }
            finally { world.playerEntities.remove(unsafePlayer); }
            evidence.addProperty("survival_operator_and_occupant_rejected", true);
            require(server.getCommandManager().executeCommand(new Sender("non-op", 0), "orespawn survey") == 0, "Non-operator survey accepted");
            require(server.getCommandManager().executeCommand(new Sender("level-two", 2), "orespawn clearchunk") == 0, "Level-two clear accepted");
            loaded = world.getChunkProvider().getLoadedChunkCount(); rng = randomState();
            retrogenBefore = zone.moddev.mc.orespawn.worldgen.OreRetrogenManager.diagnosticStatus(world, new net.minecraft.util.math.ChunkPos(CX, CZ)).toString();
            scheduledBefore = scheduled(); dropsBefore = drops();
            command("orespawn help"); command("orespawn status verbose"); command("orespawn chunk --center " + CX + " " + CZ);
            command("orespawn retrogen status --center " + CX + " " + CZ);
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : WorldGeologyProfileManager.activeProfile().toJson().getAsJsonObject("ores").entrySet()) {
                JsonObject rule = entry.getValue().getAsJsonObject();
                if ("minecraft:iron_ore".equals(rule.has("block") ? rule.get("block").getAsString() : entry.getKey())) {
                    command("orespawn explain " + entry.getKey() + " " + (CX * 16) + " 8 " + (CZ * 16));
                    JsonObject explanation = zone.moddev.mc.orespawn.worldgen.OreSpawnOreGeneration.diagnosticRule(world, pos(8 << 8), new net.minecraft.util.ResourceLocation(entry.getKey()));
                    boolean managed = WorldGeologyProfileManager.activeProfile().manageVanillaOres();
                    if (rule.has("native_generation") && rule.get("native_generation").getAsBoolean()) {
                        if (!managed) require(!explanation.get("in_effective_placement_budget").getAsBoolean()
                                && "vanilla_management_off".equals(explanation.get("reason").getAsString()), "OFF explanation is incorrect");
                        else if (!Boolean.getBoolean("admin.probe.stack")) require(explanation.get("in_effective_placement_budget").getAsBoolean(), "ON explanation omitted native placement");
                        evidence.addProperty("native_explanation_manage_vanilla", managed);
                    }
                    break;
                }
            }
            if (Boolean.getBoolean("admin.probe.collision")) require("probe-owned".equals(server.getCommandManager().getCommands().get("clearchunks").getUsage(sender)), "Alias collision overwritten");
            require(rng.equals(randomState()), "Immediate diagnostics consumed world RNG");
            String cancelled = begin("orespawn survey 8 --center " + CX + " " + CZ);
            command("orespawn cancel " + cancelled);
            pending = begin("orespawn survey 0 --center " + CX + " " + CZ + " --y 0 15");
            String surveyRandom = randomState(); AdminJobs.tick();
            require(surveyRandom.equals(randomState()), "Survey step consumed world RNG");
        }
        ready = true;
    }

    @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public void beforeEnd(TickEvent.ServerTickEvent event) throws Exception {
        if (!ready || event.phase != TickEvent.Phase.END) return;
        scheduledAtEnd = scheduled(); dropsAtEnd = drops(); rngAtEnd = randomState();
        adminWindowStart = System.nanoTime();
        if (!threadClock.isThreadCpuTimeEnabled()) threadClock.setThreadCpuTimeEnabled(true);
        adminCpuStart = threadClock.getCurrentThreadCpuTime();
    }

    @SubscribeEvent public void entityJoined(net.minecraftforge.event.entity.EntityJoinWorldEvent event) {
        if (!ready || !(event.getEntity() instanceof net.minecraft.entity.item.EntityItem)) return;
        JsonObject item = new JsonObject();
        item.addProperty("position", event.getEntity().posX + "," + event.getEntity().posY + "," + event.getEntity().posZ);
        com.google.gson.JsonArray trace = new com.google.gson.JsonArray();
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (frame.getClassName().startsWith("zone.moddev.mc.orespawn.commands.admin."))
                throw new AssertionError("Admin command spawned an item drop: " + frame);
            if (trace.size() < 18) trace.add(new com.google.gson.JsonPrimitive(frame.toString()));
        }
        item.add("trace", trace);
        if (ambientDrops.size() < 20) ambientDrops.add(item);
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (!ready || event.phase != TickEvent.Phase.END) return;
        try {
            long elapsed = System.nanoTime() - adminWindowStart;
            if (pending != null && !AdminJobs.jobs(pending).has("status")) {
                activeTickNanos.add(elapsed);
                activeCpuNanos.add(threadClock.getCurrentThreadCpuTime() - adminCpuStart);
            }
            if (++ticks > 10000) throw new AssertionError("Qualification timed out");
            require(scheduled() <= scheduledAtEnd, "Admin tick scheduled an update flood");
            require(drops() <= dropsAtEnd, "Admin tick spawned item drops");
            require(randomState().equals(rngAtEnd), "Admin tick consumed world RNG");
            JsonObject result = AdminJobs.jobs(pending);
            if (result.has("maximum_block_visits_per_tick")) maxObservedVisits = Math.max(maxObservedVisits, result.get("maximum_block_visits_per_tick").getAsInt());
            if (stage == 19 && result.has("edited_cells") && result.get("edited_cells").getAsInt() > 0 && !result.has("status")) {
                world.getChunkProvider().queueUnload(chunk); world.getChunkProvider().tick(); stage = 20; return;
            }
            if (stage == 11 && result.has("edited_cells") && result.get("edited_cells").getAsInt() > 0 && !result.has("status")) {
                command("orespawn cancel " + pending);
                require(!hash().equals(baseline), "Cancelled edit never wrote blocks");
                stage = 15;
                return;
            }
            if (stage == 15 && result.has("status") && "written".equals(result.has("export") ? result.get("export").getAsString() : "")) {
                require("cancelled".equals(result.get("status").getAsString()), "Cancellation cleanup failed: " + result);
                int observedLight = chunk.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, pos((8 << 8) + 4));
                int expectedLight = nativeLight(pos((8 << 8) + 4));
                evidence.addProperty("cancel_observed_block_light", observedLight);
                evidence.addProperty("cancel_native_expected_block_light", expectedLight);
                require(chunk.getBlockState(pos((8 << 8) + 4)).getBlock() == Blocks.AIR, "Cancellation fixture emitter was not removed");
                require(observedLight == expectedLight, "Cancellation left inconsistent block light");
                JsonObject checkpoint = new JsonObject(); checkpoint.addProperty("baseline", baseline); checkpoint.addProperty("backup", pending);
                DiagnosticFiles.write(java.nio.file.Paths.get("restart-checkpoint.json").toAbsolutePath(), checkpoint);
                evidence.addProperty("partial_cancel", true); complete("initial"); return;
            }
            if (!result.has("status") || !"written".equals(result.has("export") ? result.get("export").getAsString() : "")) return;
            String status = result.get("status").getAsString();
            if (stage == 0) {
                require("complete".equals(status), "Survey failed: " + result);
                JsonObject saved = DiagnosticFiles.read(AdminJobs.root(world).resolve("reports").resolve(pending).resolve("report.json"));
                require(saved.getAsJsonObject("counts").get("sampled_volume").getAsInt() == 4096, "Wrong volume");
                require(saved.getAsJsonObject("counts").getAsJsonObject("blocks").get(DiagnosticStates.key(Blocks.DIAMOND_ORE.getDefaultState())).getAsInt() == 1, "Wrong ore count");
                require(hash().equals(baseline) && profile.equals(WorldGeologyProfileManager.activeProfile().toJson().toString()), "Read-only mutation");
                require(loaded == world.getChunkProvider().getLoadedChunkCount(), "Read-only chunk load");
                require(retrogenBefore.equals(zone.moddev.mc.orespawn.worldgen.OreRetrogenManager.diagnosticStatus(world, new net.minecraft.util.math.ChunkPos(CX, CZ)).toString()), "Read-only retrogen bookkeeping changed");
                evidence.addProperty("read_only_hash_profile_rng_loaded_count", true);
                stage = 13; pending = begin("orespawn survey 1 --center 999999 999999");
            } else if (stage == 13) {
                require("partial".equals(status) && result.get("sampled_chunks").getAsInt() == 0, "Unloaded area did not report partial coverage");
                require(world.getChunkProvider().getLoadedChunkCount() == loaded, "Survey loaded an absent chunk");
                stage = 14; pending = begin("orespawn survey 0 --center " + CX + " " + CZ + " --block minecraft:wool@3");
            } else if (stage == 14) {
                JsonObject saved = DiagnosticFiles.read(AdminJobs.root(world).resolve("reports").resolve(pending).resolve("report.json"));
                require(saved.getAsJsonObject("counts").get("sampled_volume").getAsInt() == 1, "Metadata filter counted the wrong cells");
                evidence.addProperty("unloaded_coverage_metadata_filter_and_read_cancel", true);
                stage = 1; pending = preview();
            } else if (stage == 1) {
                token = result.get("confirmation_token").getAsString();
                put((8 << 8) + 10, Blocks.GOLD_BLOCK.getDefaultState());
                stage = 2; pending = begin("orespawn clearchunk confirm " + token);
            } else if (stage == 2) {
                require("failed".equals(status) && result.get("edited_cells").getAsInt() == 0, "Stale preview wrote blocks");
                put((8 << 8) + 10, Blocks.STONE.getDefaultState()); stage = 3; pending = preview();
            } else if (stage == 3) {
                token = result.get("confirmation_token").getAsString();
                require(server.getCommandManager().executeCommand(new Sender("other-operator", 4), "orespawn clearchunk confirm " + token) == 0, "Foreign token accepted");
                stage = 4; pending = begin("orespawn clearchunk confirm " + token);
                Path occupied = AdminJobs.root(world).resolve("backups").resolve(pending).resolve("journal.json");
                JsonObject sentinel = new JsonObject(); sentinel.addProperty("negative_fixture", "occupied backup target"); DiagnosticFiles.write(occupied, sentinel);
            } else if (stage == 4) {
                require("failed".equals(status) && hash().equals(baseline), "Backup failure did not fail closed");
                evidence.addProperty("stale_foreign_token_and_backup_failure", true);
                stage = 5; pending = preview();
            } else if (stage == 5) {
                checkLeaf();
                baseline = hash();
                token = result.get("confirmation_token").getAsString(); stage = 6;
                pending = begin("orespawn clearchunk confirm " + token); clearId = pending;
            } else if (stage == 6) {
                require("complete".equals(status) && !hash().equals(baseline), "Clear did not complete: " + result);
                journal = EditJournal.read(DiagnosticFiles.readBackup(AdminJobs.root(world).resolve("backups").resolve(clearId).resolve("journal.json")));
                DiagnosticCatalog catalog = new DiagnosticCatalog(WorldGeologyProfileManager.activeProfile().toJson());
                for (EditJournal.Cell cell : journal.cells) require(catalog.removable(DiagnosticStates.resolve(cell.before), false), "Protected state in journal");
                require(chunk.getBlockState(pos(8 << 8)).getBlock() == Blocks.DIAMOND_ORE, "Ore removed");
                require(chunk.getBlockState(pos((8 << 8) + 1)).getBlock() == Blocks.GOLD_BLOCK, "Unknown block removed");
                require(chunk.getBlockState(pos((8 << 8) + 2)).getBlock() == Blocks.WOOL, "Ore dictionary state removed");
                if (depositState != null) {
                    require(chunk.getBlockState(pos((8 << 8) + 3)).equals(depositState), "Configured fluid deposit removed");
                    evidence.addProperty("real_fluid_deposit_preserved", true);
                }
                inventory();
                require(chunk.getBlockStorageArray()[15].isEmpty() && !chunk.getBlockStorageArray()[15].needsRandomTick(), "Empty-section/random-tick accounting differs");
                corruptId = UUID.randomUUID().toString();
                JsonObject corrupt = new JsonObject(); corrupt.addProperty("format", 1); corrupt.addProperty("sha256", "bad"); corrupt.add("payload", journal.json());
                DiagnosticFiles.write(AdminJobs.root(world).resolve("backups").resolve(corruptId).resolve("journal.json"), corrupt);
                stage = 7; pending = begin("orespawn undo " + corruptId);
            } else if (stage == 7) {
                require("failed".equals(status), "Corrupt backup accepted");
                put(journal.cells.get(0).index, Blocks.GOLD_BLOCK.getDefaultState()); initialConflict = hash();
                stage = 8; pending = begin("orespawn undo " + clearId);
            } else if (stage == 8) {
                require("failed".equals(status) && hash().equals(initialConflict), "Undo conflict overwrote an edit");
                put(journal.cells.get(0).index, Blocks.AIR.getDefaultState());
                put(LEAF, chunk.getBlockState(pos(LEAF)).withProperty(net.minecraft.block.BlockLeaves.CHECK_DECAY, true));
                stage = 9; pending = begin("orespawn undo " + clearId);
            } else if (stage == 9) {
                require("preview".equals(status), "Undo preview failed: " + result);
                checkLeaf();
                evidence.addProperty("native_leaf_checks_allow_clear_and_undo", true);
                stage = 10; pending = begin("orespawn undo " + clearId + " confirm " + result.get("confirmation_token").getAsString());
            } else if (stage == 10) {
                require("complete".equals(status) && hash().equals(baseline), "Undo did not restore exact states: " + result);
                require(!chunk.getBlockStorageArray()[15].isEmpty() && chunk.getBlockStorageArray()[15].needsRandomTick(), "Undo lost section/random-tick accounting");
                require(chunk.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, pos((8 << 8) + 4)) == 15, "Undo did not restore emitted light");
                inventory(); evidence.addProperty("clear_undo_corruption_conflicts_inventory", true);
                lightBeforeRepeat = chunk.isLightPopulated();
                stage = 16; pending = begin("orespawn undo " + clearId);
            } else if (stage == 16) {
                require("preview".equals(status), "Repeated undo preview failed");
                stage = 17; pending = begin("orespawn undo " + clearId + " confirm " + result.get("confirmation_token").getAsString());
            } else if (stage == 17) {
                require("complete".equals(status) && result.get("edited_cells").getAsInt() == 0, "Repeated undo changed cells");
                require(hash().equals(baseline) && chunk.isLightPopulated() == lightBeforeRepeat, "Repeated undo changed states or lighting flags");
                evidence.addProperty("idempotent_undo", true);
                stage = 24; pending = begin("orespawn clearchunk --center " + CX + " " + CZ + " --y 0 0");
            } else if (stage == 24) {
                require("preview".equals(status) && result.get("removal_cells").getAsInt() == 0, "Expected an empty clearing selection");
                lightBeforeRepeat = chunk.isLightPopulated();
                stage = 25; pending = begin("orespawn clearchunk confirm " + result.get("confirmation_token").getAsString());
            } else if (stage == 25) {
                require("complete".equals(status) && result.get("edited_cells").getAsInt() == 0 && chunk.isLightPopulated() == lightBeforeRepeat,
                        "Empty clear changed blocks or lighting flags");
                evidence.addProperty("empty_selection_and_random_tick_accounting", true);
                stage = 18; pending = preview();
            } else if (stage == 18) {
                stage = 19; pending = begin("orespawn clearchunk confirm " + result.get("confirmation_token").getAsString());
            } else if (stage == 20) {
                require("failed".equals(status) && world.getChunkProvider().getLoadedChunk(CX, CZ) == null, "Unload did not stop the edit");
                clearId = pending; chunk = world.getChunkProvider().provideChunk(CX, CZ);
                require(!chunk.isLightPopulated() || chunk.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, pos((8 << 8) + 4)) == nativeLight(pos((8 << 8) + 4)),
                        "Interrupted chunk lost its relighting flag without repair");
                stage = 21; pending = begin("orespawn undo " + clearId);
            } else if (stage == 21) {
                require("preview".equals(status), "Unload recovery preview failed: " + result);
                stage = 22; pending = begin("orespawn undo " + clearId + " confirm " + result.get("confirmation_token").getAsString());
            } else if (stage == 22) {
                require("complete".equals(status) && hash().equals(baseline), "Unload recovery did not restore blocks");
                evidence.addProperty("chunk_unload_interrupt_and_recovery", true);
                JsonObject wrongWorld = journal.json(); wrongWorld.addProperty("world", "another-world");
                corruptId = UUID.randomUUID().toString();
                DiagnosticFiles.backup(AdminJobs.root(world).resolve("backups").resolve(corruptId).resolve("journal.json"), wrongWorld);
                stage = 23; pending = begin("orespawn undo " + corruptId);
            } else if (stage == 23) {
                require("failed".equals(status) && hash().equals(baseline), "Wrong-world journal was accepted");
                evidence.addProperty("world_bound_journal", true);
                stage = 12; pending = preview();
            } else if (stage == 12) {
                stage = 11; pending = begin("orespawn clearchunk confirm " + result.get("confirmation_token").getAsString());
            } else if (stage == 30) {
                require("preview".equals(status), "Restart recovery preview failed: " + result);
                stage = 31; pending = begin("orespawn undo " + clearId + " confirm " + result.get("confirmation_token").getAsString());
            } else if (stage == 31) {
                require("complete".equals(status) && hash().equals(baseline), "Restart recovery differs from original");
                require(chunk.getLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, pos((8 << 8) + 4)) == 15, "Restart recovery did not restore emitted light");
                inventory(); evidence.addProperty("exact_restart_recovery", true); complete("reload");
            }
        } catch (Throwable failure) {
            failure.printStackTrace(); ready = false;
            try { evidence.addProperty("failed", failure.toString()); evidence.addProperty("stage", stage); DiagnosticFiles.write(java.nio.file.Paths.get("admin-probe-failed.json").toAbsolutePath(), evidence); } catch (Exception ignored) { }
            server.initiateShutdown();
        }
    }

    private String preview() { return begin("orespawn clearchunk --center " + CX + " " + CZ); }
    private void checkLeaf() {
        IBlockState before = chunk.getBlockState(pos(LEAF));
        require(before.getValue(net.minecraft.block.BlockLeaves.CHECK_DECAY), "Expected an unchecked leaf");
        // Run Minecraft's real supported-leaf check between preview and confirmation.
        Blocks.LEAVES.updateTick(world, pos(LEAF), before, new java.util.Random(0));
        require(chunk.getBlockState(pos(LEAF)).equals(before.withProperty(net.minecraft.block.BlockLeaves.CHECK_DECAY, false)),
                "Native leaf check did not update only its bookkeeping flag");
    }
    private void command(String command) { require(server.getCommandManager().executeCommand(sender, command) > 0, "Command rejected: " + command); }
    private String begin(String command) {
        Set<String> before = new HashSet<>(); AdminJobs.jobs(null).entrySet().forEach(e -> before.add(e.getKey()));
        command(command);
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : AdminJobs.jobs(null).entrySet())
            if (!before.contains(entry.getKey())) return entry.getKey();
        throw new AssertionError("No new job for " + command);
    }
    private BlockPos pos(int index) { return new BlockPos(CX * 16 + (index & 15), index >>> 8, CZ * 16 + ((index >>> 4) & 15)); }
    private void put(int index, IBlockState state) {
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray(); int section = index >>> 12;
        if (sections[section] == null) sections[section] = new ExtendedBlockStorage(section * 16, true);
        sections[section].set(index & 15, (index >>> 8) & 15, (index >>> 4) & 15, state); chunk.setModified(true);
    }
    private String hash() throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < 65536; i++) { hash.update(DiagnosticStates.key(chunk.getBlockState(pos(i))).getBytes("UTF-8")); hash.update((byte) 0); }
        StringBuilder result = new StringBuilder(); for (byte item : hash.digest()) result.append(String.format("%02x", item & 255)); return result.toString();
    }
    private String randomState() throws Exception {
        java.lang.reflect.Field seed = java.util.Random.class.getDeclaredField("seed"); seed.setAccessible(true);
        return String.valueOf(((AtomicLong) seed.get(world.rand)).get());
    }
    private void inventory() {
        TileEntityChest chest = (TileEntityChest) world.getTileEntity(pos((8 << 8) + 5));
        require(chest != null && chest.getStackInSlot(0).getCount() == 37, "Inventory changed");
    }
    private int nativeLight(BlockPos position) throws Exception {
        for (java.lang.reflect.Method method : World.class.getDeclaredMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 2 && parameters[0] == BlockPos.class && parameters[1] == net.minecraft.world.EnumSkyBlock.class
                    && method.getReturnType() == int.class) {
                method.setAccessible(true);
                return (Integer) method.invoke(world, position, net.minecraft.world.EnumSkyBlock.BLOCK);
            }
        }
        throw new AssertionError("Native raw-light calculation was not found");
    }
    private int scheduled() {
        java.util.List<?> entries = world.getPendingBlockUpdates(chunk, false);
        return entries == null ? 0 : entries.size();
    }
    private int drops() {
        int count = 0;
        for (Entity entity : world.loadedEntityList) if (entity instanceof net.minecraft.entity.item.EntityItem) count++;
        return count;
    }
    private void complete(String phase) throws Exception {
        ready = false; evidence.addProperty("passed", true); evidence.addProperty("phase", phase);
        evidence.addProperty("max_visits", maxObservedVisits); require(maxObservedVisits <= 16384, "Visit budget exceeded");
        evidence.addProperty("profile", DiagnosticFiles.fingerprint(WorldGeologyProfileManager.activeProfile().toJson()));
        evidence.add("ambient_item_spawns", ambientDrops);
        evidence.addProperty("per_admin_tick_rng_scheduled_updates_and_drops", true);
        java.util.Collections.sort(activeTickNanos);
        java.util.Collections.sort(activeCpuNanos);
        require(!activeTickNanos.isEmpty(), "No active-job timing captured");
        evidence.addProperty("active_job_ticks", activeTickNanos.size());
        evidence.addProperty("active_job_tick_p95_ms", activeTickNanos.get((int) Math.ceil(activeTickNanos.size() * .95) - 1) / 1000000.0);
        evidence.addProperty("active_job_tick_max_ms", activeTickNanos.get(activeTickNanos.size() - 1) / 1000000.0);
        evidence.addProperty("active_job_cpu_p95_ms", activeCpuNanos.get((int) Math.ceil(activeCpuNanos.size() * .95) - 1) / 1000000.0);
        evidence.addProperty("active_job_cpu_max_ms", activeCpuNanos.get(activeCpuNanos.size() - 1) / 1000000.0);
        DiagnosticFiles.write(java.nio.file.Paths.get("admin-probe-" + phase + ".json").toAbsolutePath(), evidence);
        System.out.println("ADMIN_TOOLS_PASS " + phase + " " + evidence); server.initiateShutdown();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private final class Sender implements ICommandSender {
        final String name; final int permission;
        Sender(String name, int permission) { this.name = name; this.permission = permission; }
        public String getName() { return name; }
        public ITextComponent getDisplayName() { return new TextComponentString(name); }
        public void sendMessage(ITextComponent text) { System.out.println("ADMIN_FEEDBACK " + text.getUnformattedText()); }
        public boolean canUseCommand(int required, String command) { return permission >= required; }
        public BlockPos getPosition() { return pos(10 << 8); }
        public Vec3d getPositionVector() { return new Vec3d(CX * 16, 10, CZ * 16); }
        public World getEntityWorld() { return world; }
        public Entity getCommandSenderEntity() { return null; }
        public boolean sendCommandFeedback() { return true; }
        public void setCommandStat(CommandResultStats.Type type, int value) { }
        public MinecraftServer getServer() { return server; }
    }

    private final class ProbeOperator extends net.minecraft.entity.player.EntityPlayerMP {
        ProbeOperator() {
            super(AdminProbe.this.server, AdminProbe.this.world,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "AdminModeProbe"),
                    new net.minecraft.server.management.PlayerInteractionManager(AdminProbe.this.world));
        }
        @Override public boolean canUseCommand(int required, String command) { return true; }
        @Override public void sendMessage(ITextComponent text) { System.out.println("ADMIN_PLAYER_FEEDBACK " + text.getUnformattedText()); }
    }
}
