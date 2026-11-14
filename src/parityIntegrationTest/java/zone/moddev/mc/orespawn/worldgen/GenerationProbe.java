package zone.moddev.mc.orespawn.worldgen;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.BitSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFlowingFluid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Fluids;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.ChunkGeneratorOverworld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.terraingen.ChunkGeneratorEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import zone.moddev.mc.orespawn.api.*;

/** Checks native terrain, the sampler and fluid provenance in real generated chunks. */
final class GenerationProbe {
    private final String mode, fluid = System.getProperty("parity.fluid", "native");
    private final Map<Long, BitSet> masks = new HashMap<>();
    private Block water, luminous;
    private WorldServer savedWorld;
    private int savedCenterX, savedCenterZ;
    private JsonObject savedResult;
    GenerationProbe(String mode) {
        this.mode = mode;
        MinecraftForge.EVENT_BUS.register(this);
        if (mode.equals("fluids")) {
            TestLiquid[] waterFluids = testFluids("water"), luminousFluids = testFluids("luminous_water");
            FMLJavaModLoadingContext.get().getModEventBus().addGenericListener(Block.class,
                (RegistryEvent.Register<Block> event) -> {
                    water = new TestWater(0, waterFluids[0]).setRegistryName("parityprobe", "water");
                    luminous = new TestWater(8, luminousFluids[0]).setRegistryName("parityprobe", "luminous_water");
                    for (TestLiquid liquid : waterFluids) liquid.block = water;
                    for (TestLiquid liquid : luminousFluids) liquid.block = luminous;
                    event.getRegistry().registerAll(water, luminous);
                });
        }
        if (mode.equals("formations")) RockFormationRegistry.register(new ResourceLocation("parityprobe:band"),
                json -> context -> new RockFormationColumn() {
                    public FormationMaterial at(int y) {
                        return y >= 15 && y <= 40 ? new FormationMaterial(Blocks.POLISHED_GRANITE.getDefaultState(),
                                GeologyFamily.IGNEOUS_INTRUSIVE) : null;
                    }
                });
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void configure(FMLServerAboutToStartEvent event) {
        if (mode.equals("fluids")) ParityProbe.require(water.getDefaultState().getFluidState().getFluid() != Fluids.WATER
                && !water.getDefaultState().getFluidState().isTagged(net.minecraft.tags.FluidTags.WATER),
                "The acceptance fluid must have its own identity and must not inherit the water tag");
        JsonObject root = WorldGeologyProfileManager.activeProfile().rootCopy();
        root.addProperty("manage_vanilla_ores", false);
        if (mode.equals("formations")) {
            JsonObject terrain = new JsonObject();
            terrain.add("minecraft:overworld", WorldgenProvider.TerrainDimensionDefinition.builder(new ResourceLocation("minecraft:overworld"))
                    .hostBlock(new ResourceLocation("minecraft:stone")).hostBlock(new ResourceLocation("minecraft:granite"))
                    .hostBlock(new ResourceLocation("minecraft:diorite")).hostBlock(new ResourceLocation("minecraft:andesite")).build().toJson());
            root.add("terrain_dimensions", terrain);
            JsonObject formations = new JsonObject(), rule = new JsonObject(), materials = new JsonObject(), material = new JsonObject();
            rule.addProperty("type", "parityprobe:band"); rule.addProperty("algorithm_revision", 1);
            material.addProperty("block", "minecraft:polished_granite"); material.addProperty("family", "igneous_intrusive");
            materials.add("body", material); rule.add("materials", materials); formations.add("parityprobe:band", rule);
            JsonObject quartz = new com.google.gson.JsonParser().parse("{\"type\":\"orespawn:quartz_vein\",\"algorithm_revision\":1,\"chance\":1,\"spacing_chunks\":8,\"radius_min\":26,\"radius_max\":28,\"min_y\":24,\"max_y\":24,\"height\":30,\"priority\":1,\"materials\":{\"vein_quartz\":{\"block\":\"minecraft:quartz_block\"}}}").getAsJsonObject();
            formations.add("parityprobe:quartz", quartz); root.add("rock_formations", formations);
        } else {
            JsonObject materials = new JsonObject(), rule = new JsonObject();
            rule.addProperty("dimension", "minecraft:overworld"); rule.addProperty("enabled", true);
            rule.addProperty("default_fluid", fluid.equals("native") ? "minecraft:water" : "parityprobe:" + fluid);
            materials.add("parityprobe:materials", rule); root.add("dimension_materials", materials);
        }
        WorldGeologyProfileManager.applyBenchmarkProfile(WorldGeologyProfileManager.activeProfile().withRoot(root));
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void capture(ChunkGeneratorEvent.ReplaceBiomeBlocks event) {
        if (System.getProperty("parity.mode", "").equals("fluidbench")) return;
        if (mode.equals("fluids") && event.getGenerator().getClass() == ChunkGeneratorOverworld.class)
            masks.put(event.getChunk().getPos().asLong(), AquiferMaterialSubstitution.captureMask(
                    event.getChunk(), Blocks.WATER.getDefaultState(), event.getWorld().getSeaLevel()));
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void worldLoaded(net.minecraftforge.event.world.WorldEvent.Load event) {
        if (event.getWorld() instanceof WorldServer) ProbeTags.stabilizeCorals();
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void run(FMLServerStartedEvent event) {
        if (System.getProperty("parity.mode", "").equals("fluidbench")) return;
        try {
            WorldServer world = event.getServer().getWorld(DimensionType.OVERWORLD);
            ParityProbe.require(world.getSaveHandler().getWorldDirectory().getName().equals("generation-parity-world"), "Not a disposable world");
            ProbeTags.stabilizeCorals();
            ChunkGeneratorOverworld generator = (ChunkGeneratorOverworld) world.getChunkProvider().getChunkGenerator();
            if (mode.equals("fluids")) {
                ParityProbe.require(fluid.equals("luminous_water") ? generator.defaultFluid.getBlock() == luminous
                        : generator.defaultFluid.getBlock() == Blocks.WATER, "Wrong aquifer generator path");
            }
            int centerX = 4, centerZ = -16;
            if (mode.equals("formations")) {
                BlockPos dry = generator.getBiomeProvider().findBiomePosition(0, 0, 2048,
                        java.util.Collections.singletonList(net.minecraft.init.Biomes.PLAINS), new java.util.Random(471));
                ParityProbe.require(dry != null, "No plains test site");
                centerX = dry.getX() >> 4; centerZ = dry.getZ() >> 4;
            }
            for (int z = centerZ - 5; z <= centerZ + 5; z++) for (int x = centerX - 5; x <= centerX + 5; x++) world.getChunk(x, z);
            int loaded = world.getChunkProvider().getLoadedChunkCount();
            java.util.Random rng = world.rand;
            java.lang.reflect.Field seed = java.util.Random.class.getDeclaredField("seed"); seed.setAccessible(true);
            long beforeRng = ((java.util.concurrent.atomic.AtomicLong) seed.get(rng)).get();
            GeologySampler sampler = mode.equals("formations") ? OreSpawnApi.createSampler(world).get() : null;
            long formations = 0, quartz = 0, custom = 0, waterCells = 0, scheduled = 0;
            MessageDigest digest = MessageDigest.getInstance("SHA-256"), stable = MessageDigest.getInstance("SHA-256");
            Map<IBlockState, byte[]> keys = new IdentityHashMap<>();
            Map<String, Long> counts = new java.util.TreeMap<>();
            BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
            for (int z = centerZ - 4; z <= centerZ + 4; z++) for (int x = centerX - 4; x <= centerX + 4; x++) {
                Chunk chunk = world.getChunk(x, z);
                java.util.List<?> updates = world.getPendingFluidTicks().getPending(chunk, false);
                scheduled += updates == null ? 0 : updates.size();
                for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                    GeologyColumn column = sampler == null ? null : sampler.sampleColumn(x * 16 + lx, z * 16 + lz, 64);
                    for (int y = 0; y < 256; y++) {
                        IBlockState state = chunk.getBlockState(position.setPos(x * 16 + lx, y, z * 16 + lz));
                        counts.merge(state.getBlock().getRegistryName().toString(), 1L, Long::sum);
                        if (mode.equals("formations")) {
                            if (state.getBlock() == Blocks.POLISHED_GRANITE || state.getBlock() == Blocks.QUARTZ_BLOCK) {
                                ParityProbe.require(column.formationAt(y).isPresent() && column.rockAt(y).equals(state), "Sampler differs from generated formation at " + position);
                                formations++; if (state.getBlock() == Blocks.QUARTZ_BLOCK) quartz++;
                            }
                            if (y >= 15 && y <= 40) ParityProbe.require(!NaturalFormations.nativeStoneVariant(state), "Unreplaced native stone in test formation at " + position);
                        } else {
                            if (state.getBlock() == water || state.getBlock() == luminous) {
                                custom++;
                                if (state.getBlock() == water && !System.getProperty("parity.phase", "Fresh").equals("Reload")) {
                                    BitSet mask = masks.get(chunk.getPos().asLong());
                                    ParityProbe.require(y < world.getSeaLevel() && mask != null && mask.get(AquiferMaterialSubstitution.index(lx, y, lz)), "Replaced a later lake/spring or unrecorded cell");
                                }
                                state = Blocks.WATER.getDefaultState();
                            } else if (state.getBlock() == Blocks.WATER) waterCells++;
                        }
                        final IBlockState normalized = state;
                        digest.update(keys.computeIfAbsent(state, ignored -> net.minecraft.command.arguments.BlockStateParser
                                .toString(normalized, null).getBytes(java.nio.charset.StandardCharsets.UTF_8))); digest.update((byte) 0);
                        IBlockState permanent = state.getBlock() == Blocks.BROWN_MUSHROOM || state.getBlock() == Blocks.RED_MUSHROOM
                                ? Blocks.AIR.getDefaultState() : state;
                        stable.update(keys.computeIfAbsent(permanent, value -> net.minecraft.command.arguments.BlockStateParser
                                .toString(value, null).getBytes(java.nio.charset.StandardCharsets.UTF_8))); stable.update((byte) 0);
                    }
                }
                if (mode.equals("fluids")) ParityProbe.require(!AquiferMaterialSubstitution.apply(world, chunk), "Consumed an aquifer mask twice");
            }
            ParityProbe.require(loaded == world.getChunkProvider().getLoadedChunkCount(), "Sampler or substitution loaded chunks");
            ParityProbe.require(beforeRng == ((java.util.concurrent.atomic.AtomicLong) seed.get(rng)).get(), "Sampler consumed generation randomness");
            ParityProbe.write(Paths.get("counts-" + System.getProperty("parity.phase", "Fresh") + ".json"), new com.google.gson.Gson().toJsonTree(counts).getAsJsonObject());
            if (mode.equals("formations")) ParityProbe.require(formations > 0 && quartz > 0, "No generated test formations or built-in quartz veins: formations=" + formations + " quartz=" + quartz);
            else if (!fluid.equals("native")) ParityProbe.require(custom > 0 && waterCells > 0, "No substituted aquifer or preserved native lake/spring water");
            JsonObject result = new JsonObject(); result.addProperty("passed", true); result.addProperty("formations", formations);
            result.addProperty("quartz", quartz); result.addProperty("substituted", custom); result.addProperty("native_water", waterCells);
            result.addProperty("pending_fluid_ticks", scheduled); result.addProperty("normalized_blocks_sha256", hex(digest.digest()));
            result.addProperty("stable_terrain_sha256", hex(stable.digest()));
            result.addProperty("reload_normalizes_only_fragile_mushrooms", true);
            result.addProperty("sampler_no_rng_or_chunk_load", true);
            result.addProperty("center_x", centerX); result.addProperty("center_z", centerZ);
            String phase = System.getProperty("parity.phase", "Fresh");
            if (mode.equals("formations") && phase.equals("Fresh"))
                result.addProperty("lake_rim_restored", checkLakeRim(world, generator, centerX + 64, centerZ));
            if (phase.equals("Reload")) {
                JsonObject before = new com.google.gson.JsonParser().parse(new String(Files.readAllBytes(Paths.get("generation-Fresh.json")), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                ParityProbe.require(before.get("stable_terrain_sha256").equals(result.get("stable_terrain_sha256")), "Reload changed existing terrain beyond ordinary mushroom support updates");
            }
            if (mode.equals("fluids")) {
                AquiferMaterialSubstitution.clear();
                ParityProbe.require(generator.defaultFluid.getBlock() == Blocks.WATER && AquiferMaterialSubstitution.pendingCount() == 0, "Shutdown failed to restore aquifer state");
            }
            ParityProbe.write(Paths.get("generation-" + phase + ".json"), result);
            savedWorld = world; savedCenterX = centerX; savedCenterZ = centerZ; savedResult = result;
            System.out.println("GENERATION_PARITY_PASS " + result);
        } catch (Throwable failure) { failure.printStackTrace(); }
        event.getServer().initiateShutdown();
    }

    private static int checkLakeRim(WorldServer world, ChunkGeneratorOverworld generator,
            int chunkX, int chunkZ) {
        // This sealed, artificial site is outside the generation-hash sample.
        for (int z = chunkZ - 1; z <= chunkZ + 1; z++)
            for (int x = chunkX - 1; x <= chunkX + 1; x++) world.getChunk(x, z);
        BlockPos origin = new BlockPos(chunkX * 16, 30, chunkZ * 16);
        Map<BlockPos, IBlockState> original = new java.util.LinkedHashMap<>();
        GeologySampler sampler = OreSpawnApi.createSampler(world).get();
        for (int z = -1; z <= 16; z++) for (int x = -1; x <= 16; x++) {
            GeologyColumn column = sampler.sampleColumn(origin.getX() + x, origin.getZ() + z, 64);
            for (int y = 20; y <= 40; y++) {
                BlockPos at = new BlockPos(origin.getX() + x, y, origin.getZ() + z);
                IBlockState state = column.formationAt(y).get().material().state();
                original.put(at, state); world.setBlockState(at, state, 2);
            }
        }
        net.minecraft.world.gen.feature.LakesConfig config = new net.minecraft.world.gen.feature.LakesConfig(Blocks.LAVA);
        ParityProbe.require(net.minecraft.world.gen.feature.Feature.LAKES.func_212245_a(
                world, generator, new java.util.Random(610), origin, config), "Native test lake was rejected");
        Map<BlockPos, IBlockState> nativeResult = new java.util.LinkedHashMap<>();
        for (BlockPos at : original.keySet()) nativeResult.put(at, world.getBlockState(at));
        for (Map.Entry<BlockPos, IBlockState> entry : original.entrySet()) world.setBlockState(entry.getKey(), entry.getValue(), 2);
        ParityProbe.require(VanillaLakeCompatibility.FEATURE.func_212245_a(
                world, generator, new java.util.Random(610), origin, config), "Wrapped test lake was rejected");
        int restored = 0, lava = 0, air = 0;
        for (Map.Entry<BlockPos, IBlockState> entry : nativeResult.entrySet()) {
            IBlockState expected = entry.getValue();
            if (expected.equals(Blocks.STONE.getDefaultState())) { expected = original.get(entry.getKey()); restored++; }
            if (expected.getBlock() == Blocks.LAVA) lava++;
            if (expected.isAir()) air++;
            ParityProbe.require(expected.equals(world.getBlockState(entry.getKey())),
                    "Lake changed its cavity, lava or formation rim at " + entry.getKey());
        }
        ParityProbe.require(restored > 0 && lava > 0 && air > 0, "Lake test did not exercise its rim and cavity");
        return restored;
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void stopping(FMLServerStoppingEvent event) throws Exception {
        if (savedResult == null) return;
        // The server may finish one tick after shutdown is requested. Snapshot after it,
        // immediately before the normal save, rather than treating falling plants as retrogen.
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Map<IBlockState, byte[]> keys = new IdentityHashMap<>();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int z = savedCenterZ - 4; z <= savedCenterZ + 4; z++) for (int x = savedCenterX - 4; x <= savedCenterX + 4; x++) {
            Chunk chunk = savedWorld.getChunk(x, z);
            for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) for (int y = 0; y < 256; y++) {
                IBlockState current = chunk.getBlockState(position.setPos(x * 16 + lx, y, z * 16 + lz));
                if (mode.equals("fluids") && (current.getBlock() == water || current.getBlock() == luminous)) current = Blocks.WATER.getDefaultState();
                final IBlockState state = current;
                digest.update(keys.computeIfAbsent(state, ignored -> net.minecraft.command.arguments.BlockStateParser
                        .toString(state, null).getBytes(java.nio.charset.StandardCharsets.UTF_8))); digest.update((byte) 0);
            }
        }
        savedResult.addProperty("saved_blocks_sha256", hex(digest.digest()));
        savedResult.addProperty("snapshot_stage", "server_stopping_before_save");
        Files.write(Paths.get("generation-" + System.getProperty("parity.phase", "Fresh") + ".json"),
                savedResult.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(); for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); return result.toString();
    }
    private static final class TestWater extends BlockFlowingFluid {
        TestWater(int light, net.minecraft.fluid.FlowingFluid fluid) { super(fluid, Block.Properties.from(Blocks.WATER).lightValue(light)); }
    }

    private static TestLiquid[] testFluids(String name) {
        TestLiquid still = new TestLiquid(true), flowing = new TestLiquid(false);
        for (TestLiquid liquid : new TestLiquid[]{still, flowing}) { liquid.still = still; liquid.flowing = flowing; }
        // Forge 25 exposes fluids through the native registry, before block registration.
        net.minecraft.util.registry.IRegistry.field_212619_h.put(new ResourceLocation("parityprobe", name), still);
        net.minecraft.util.registry.IRegistry.field_212619_h.put(new ResourceLocation("parityprobe", "flowing_" + name), flowing);
        for (TestLiquid liquid : new TestLiquid[]{still, flowing})
            for (net.minecraft.fluid.IFluidState state : liquid.getStateContainer().getValidStates())
                net.minecraft.fluid.Fluid.STATE_REGISTRY.add(state);
        return new TestLiquid[]{still, flowing};
    }

    /** Water-like flow, but neither a native water identity nor a water-tag member. */
    private static final class TestLiquid extends net.minecraft.fluid.WaterFluid {
        private final boolean source;
        private TestLiquid still, flowing;
        private Block block;
        TestLiquid(boolean source) { this.source = source; }
        @Override public net.minecraft.fluid.Fluid getStillFluid() { return still; }
        @Override public net.minecraft.fluid.Fluid getFlowingFluid() { return flowing; }
        @Override protected void fillStateContainer(net.minecraft.state.StateContainer.Builder<net.minecraft.fluid.Fluid, net.minecraft.fluid.IFluidState> builder) {
            super.fillStateContainer(builder); builder.add(LEVEL_1_TO_8);
        }
        @Override public int getLevel(net.minecraft.fluid.IFluidState state) { return source ? 8 : state.get(LEVEL_1_TO_8); }
        @Override public boolean isSource(net.minecraft.fluid.IFluidState state) { return source; }
        @Override public boolean isEquivalentTo(net.minecraft.fluid.Fluid fluid) { return fluid == still || fluid == flowing; }
        @Override public IBlockState getBlockState(net.minecraft.fluid.IFluidState state) {
            return block.getDefaultState().with(BlockFlowingFluid.LEVEL, getLevelFromState(state));
        }
    }
}
