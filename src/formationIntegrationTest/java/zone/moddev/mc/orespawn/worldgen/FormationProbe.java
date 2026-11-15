package zone.moddev.mc.orespawn.worldgen;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import zone.moddev.mc.orespawn.api.*;

/** Checks real chunk terrain against an independent, simple formation and the public sampler. */
@Mod(modid="formationprobe", name="Formation qualification", version="1", dependencies="required-after:orespawn")
public final class FormationProbe {
    @Mod.EventHandler public void register(FMLPreInitializationEvent event) {
        RockFormationRegistry.register(new ResourceLocation("formationprobe:band"),
                definition -> context -> new RockFormationColumn() {
                    public FormationMaterial at(int y) {
                        return y >= 15 && y <= 40 ? new FormationMaterial(Blocks.STONE.getStateFromMeta(2),
                                GeologyFamily.IGNEOUS_INTRUSIVE) : null;
                    }
                });
    }

    @Mod.EventHandler public void configure(FMLServerAboutToStartEvent event) {
        JsonObject root = WorldGeologyProfileManager.activeProfile().rootCopy();
        root.addProperty("geology_mode", "geome");
        root.addProperty("manage_vanilla_ores", false);
        JsonObject terrain = new JsonObject();
        terrain.add("minecraft:overworld", WorldgenProvider.TerrainDimensionDefinition.builder(new ResourceLocation("minecraft:overworld"))
                .hostBlock(new ResourceLocation("minecraft:stone")).build().toJson());
        root.add("terrain_dimensions", terrain);
        JsonObject formations = new JsonObject(), rule = new JsonObject(), materials = new JsonObject(), material = new JsonObject();
        rule.addProperty("type", "formationprobe:band"); rule.addProperty("algorithm_revision", 1);
        material.addProperty("block", "minecraft:stone"); material.addProperty("metadata", 2);
        material.addProperty("family", "igneous_intrusive");
        materials.add("body", material); rule.add("materials", materials); formations.add("formationprobe:band", rule);
        JsonObject quartz = new com.google.gson.JsonParser().parse("{\"type\":\"orespawn:quartz_vein\",\"algorithm_revision\":1,\"chance\":1,\"spacing_chunks\":8,\"radius_min\":26,\"radius_max\":28,\"min_y\":24,\"max_y\":24,\"height\":30,\"priority\":1,\"materials\":{\"vein_quartz\":{\"block\":\"minecraft:quartz_block\"}}}").getAsJsonObject();
        formations.add("formationprobe:quartz", quartz); root.add("rock_formations", formations);
        WorldGeologyProfileManager.applyBenchmarkProfile(WorldGeologyProfileManager.activeProfile().withRoot(root));
    }

    @Mod.EventHandler public void run(FMLServerStartedEvent event) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        try {
            WorldServer world = server.getWorld(0);
            require(world.getSaveHandler().getWorldDirectory().getName().equals("formation-test"), "Not a disposable save");
            world.getGameRules().setOrCreateGameRule("randomTickSpeed", "0");
            int cx = 4, cz = -16;
            for (int z = cz - 5; z <= cz + 5; z++) for (int x = cx - 5; x <= cx + 5; x++) world.getChunkProvider().provideChunk(x, z);
            int loaded = world.getChunkProvider().getLoadedChunkCount();
            java.lang.reflect.Field seed = java.util.Random.class.getDeclaredField("seed"); seed.setAccessible(true);
            long randomBefore = ((java.util.concurrent.atomic.AtomicLong) seed.get(world.rand)).get();
            GeologySampler sampler = OreSpawnApi.createSampler(world).get();
            String phase = System.getProperty("formation.phase", "fresh");
            java.util.Set<Long> priorReactions = new java.util.HashSet<>();
            if (phase.equals("reload")) {
                JsonObject prior = new com.google.gson.JsonParser().parse(new String(Files.readAllBytes(Paths.get("formation-fresh.json")), StandardCharsets.UTF_8)).getAsJsonObject();
                for (com.google.gson.JsonElement value : prior.getAsJsonArray("native_reaction_positions")) priorReactions.add(value.getAsLong());
            }
            com.google.gson.JsonArray reactionPositions = new com.google.gson.JsonArray();
            long formations = 0, quartz = 0, gaps = 0, nativeReactions = 0;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Map<IBlockState, byte[]> keys = new IdentityHashMap<>();
            BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
            for (int z = cz - 4; z <= cz + 4; z++) for (int x = cx - 4; x <= cx + 4; x++) {
                Chunk chunk = world.getChunkProvider().getLoadedChunk(x, z);
                for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                    GeologyColumn column = sampler.sampleColumn(x * 16 + lx, z * 16 + lz, 64);
                    for (int y = 0; y < 256; y++) {
                        IBlockState state = chunk.getBlockState(position.setPos(x * 16 + lx, y, z * 16 + lz));
                        if (state.equals(Blocks.STONE.getStateFromMeta(2)) || state.getBlock() == Blocks.QUARTZ_BLOCK) {
                            require(column.formationAt(y).isPresent() && column.rockAt(y).equals(state), "Sampler differs from generated formation at " + position);
                            formations++; if (state.getBlock() == Blocks.QUARTZ_BLOCK) quartz++;
                        }
                        if (y >= 15 && y <= 40 && NaturalFormations.nativeStoneVariant(state)) {
                            if (NativeFluidTrace.contains(world, position) || priorReactions.contains(position.toLong())) {
                                nativeReactions++;
                                reactionPositions.add(new com.google.gson.JsonPrimitive(position.toLong()));
                            }
                            else gaps++;
                        }
                        // Native mushroom support updates are not geology changes.
                        IBlockState permanent = state.getBlock() == Blocks.BROWN_MUSHROOM || state.getBlock() == Blocks.RED_MUSHROOM ? Blocks.AIR.getDefaultState() : state;
                        digest.update(keys.computeIfAbsent(permanent, value -> value.toString().getBytes(StandardCharsets.UTF_8)));
                        digest.update((byte) 0);
                    }
                }
            }
            require(formations > 0 && quartz > 0 && gaps == 0, "Missing formation substrate or built-in quartz: " + formations + "/" + quartz + "/" + gaps);
            require("1".equals(System.getProperty("formation.nativeFluidTraceSites")), "The native fluid writer was not traced");
            require(loaded == world.getChunkProvider().getLoadedChunkCount(), "Sampler loaded a chunk");
            require(randomBefore == ((java.util.concurrent.atomic.AtomicLong) seed.get(world.rand)).get(), "Sampler consumed world randomness");
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format("%02x", value & 255));
            JsonObject result = new JsonObject(); result.addProperty("passed", true);
            result.addProperty("formations", formations); result.addProperty("quartz", quartz);
            result.addProperty("unreplaced_stone", gaps); result.addProperty("terrain_hash", hash.toString());
            result.addProperty("native_fluid_reaction_stone", nativeReactions);
            result.add("native_reaction_positions", reactionPositions);
            result.addProperty("native_structure_sentinel", structureSentinel(world, cx + 5, cz, phase));
            if (phase.equals("reload")) {
                JsonObject before = new com.google.gson.JsonParser().parse(new String(Files.readAllBytes(Paths.get("formation-fresh.json")), StandardCharsets.UTF_8)).getAsJsonObject();
                require(before.equals(result), "Exact reload changed terrain or counts");
            }
            Files.write(Paths.get("formation-" + phase + ".json"), result.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("FORMATION_PASS " + result);
        } catch (Throwable failure) { failure.printStackTrace(); }
        server.initiateShutdown();
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static boolean structureSentinel(WorldServer world, int cx, int cz, String phase) {
        Chunk chunk = world.getChunkProvider().getLoadedChunk(cx, cz);
        BlockPos protectedCell = new BlockPos(cx * 16 + 8, 25, cz * 16 + 8);
        BlockPos ordinaryCell = new BlockPos(cx * 16 + 3, 25, cz * 16 + 3);
        net.minecraft.world.storage.MapStorage store = world.getPerWorldStorage();
        net.minecraft.world.gen.structure.MapGenStructureData starts = (net.minecraft.world.gen.structure.MapGenStructureData)
                store.getOrLoadData(net.minecraft.world.gen.structure.MapGenStructureData.class, "Mansion");
        GeologySampler sampler = OreSpawnApi.createSampler(world).get();
        IBlockState expected = sampler.sampleColumn(ordinaryCell.getX(), ordinaryCell.getZ(), 64).rockAt(25);
        require(!expected.equals(Blocks.STONE.getDefaultState()), "Missing independent formation control");
        if (phase.equals("fresh")) {
            if (starts == null) {
                starts = new net.minecraft.world.gen.structure.MapGenStructureData("Mansion");
                store.setData("Mansion", starts);
            }
            net.minecraft.nbt.NBTTagCompound start = new net.minecraft.nbt.NBTTagCompound();
            start.setIntArray("BB", new int[]{cx * 16 + 7, 20, cz * 16 + 7, cx * 16 + 10, 30, cz * 16 + 10});
            starts.getTagCompound().setTag("formation_probe", start); starts.markDirty();
            chunk.setBlockState(protectedCell, Blocks.STONE.getDefaultState());
            chunk.setBlockState(ordinaryCell, Blocks.STONE.getDefaultState());
        } else {
            require(starts != null && starts.getTagCompound().hasKey("formation_probe"), "Native structure start was not retained");
            require(chunk.getBlockState(protectedCell).equals(Blocks.STONE.getDefaultState()), "Protected structure changed across restart");
            require(chunk.getBlockState(ordinaryCell).equals(expected), "Ordinary formation changed across restart");
        }
        int loaded = world.getChunkProvider().getLoadedChunkCount();
        NaturalFormations.apply(world, chunk, GeomeConfig.terrainDimension(WorldIds.dimension(world)));
        require(chunk.getBlockState(protectedCell).equals(Blocks.STONE.getDefaultState()), "Formation rewrote native structure terrain");
        require(chunk.getBlockState(ordinaryCell).equals(expected), "Unprotected formation control did not run");
        require(loaded == world.getChunkProvider().getLoadedChunkCount(), "Structure protection loaded a neighbouring chunk");
        chunk.setModified(true);
        return true;
    }
}
