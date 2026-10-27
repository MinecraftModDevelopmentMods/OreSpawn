package zone.moddev.mc.orespawn.qualification;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.orespawn.api.OreGenerationContext;
import zone.moddev.mc.orespawn.api.OrePatternType;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.orespawn.api.StandardPatternSettings;

/** Exercises saved profiles through a real, disposable Forge 1.12 server. */
@Mod(modid = "oresourcequalification", name = "Ore Sources qualification", version = "1",
        acceptedMinecraftVersions = "[1.12.2]", dependencies = "required-after:orespawn")
public final class OreSourceQualificationMod {
    public OreSourceQualificationMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void registerPatterns(RegistryEvent.Register<OrePatternType> event) {
        event.getRegistry().register(OrePatternType.create(StandardPatternSettings.CODEC, settings -> context -> {
            OreGenerationContext generation = (OreGenerationContext) context;
            boolean changed = false;
            // This small deposit has a fixed shape, independent of background random calls.
            for (int x = 4; x < 8; x++) {
                for (int z = 4; z < 8; z++) {
                    changed |= generation.tryPlace((generation.chunkX() << 4) + x, 200,
                            (generation.chunkZ() << 4) + z, 1001L);
                }
            }
            return changed;
        }).setRegistryName(new ResourceLocation("oresourcequalification", "deposit")));
    }

    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) throws Exception {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        String phase = System.getProperty("oresourceprobe.phase", "fresh");
        JsonObject report = new JsonObject();
        report.addProperty("phase", phase);
        try {
            JsonObject profile = OreSpawnApi.getActiveProfile(server).get().toJson();
            report.add("profile", profile);
            if (!Boolean.getBoolean("oresourceprobe.bootstrap")) {
                boolean managed = Boolean.parseBoolean(System.getProperty("oresourceprobe.manage", "true"));
                require(profile.get("manage_vanilla_ores").getAsBoolean() == managed, "Management switch changed");
                WorldServer world = DimensionManager.getWorld(0);
                int base = Integer.getInteger("oresourceprobe.base", 64);
                int oldBase = Integer.getInteger("oresourceprobe.oldBase", base);
                boolean reload = Boolean.getBoolean("oresourceprobe.reload");
                load(world, oldBase, false);
                if (!reload) load(world, base, true);
                report.add("terrain", scan(world, base));
                if (oldBase != base) report.add("old_terrain", scan(world, oldBase));
                JsonObject terrain = report.getAsJsonObject("terrain");
                require(terrain.get("deposit").getAsInt() == 256, "Independent deposit changed shape or disappeared");
                String expectation = System.getProperty("oresourceprobe.expect", "positive");
                if ("zero".equals(expectation)) {
                    require(terrain.get("ordinary").getAsInt() == 0, "Replacement left ordinary iron behind");
                } else {
                    require(terrain.get("ordinary").getAsInt() > 0, "Ordinary iron was unexpectedly suppressed");
                }
            }
            server.saveAllWorlds(false);
            report.addProperty("passed", true);
            System.out.println("ORE_SOURCE_QUALIFICATION_PASS " + phase);
        } catch (Throwable failure) {
            report.addProperty("passed", false);
            report.addProperty("failure", failure.toString());
            failure.printStackTrace();
        } finally {
            Files.write(Paths.get(phase + "-result.json"), (new GsonBuilder().setPrettyPrinting().create()
                    .toJson(report) + "\n").getBytes(StandardCharsets.UTF_8));
            server.initiateShutdown();
        }
    }

    private static void load(WorldServer world, int base, boolean populate) {
        int from = populate ? base - 1 : base;
        int until = populate ? base + 6 : base + 4;
        for (int x = from; x < until; x++) {
            for (int z = from; z < until; z++) world.getChunkProvider().provideChunk(x, z);
        }
    }

    private static JsonObject scan(WorldServer world, int base) throws Exception {
        JsonObject result = new JsonObject();
        MessageDigest blocks = MessageDigest.getInstance("SHA-256");
        MessageDigest ordinaryPositions = MessageDigest.getInstance("SHA-256");
        MessageDigest depositPositions = MessageDigest.getInstance("SHA-256");
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int ordinary = 0;
        int deposit = 0;
        for (int cx = base; cx < base + 4; cx++) {
            for (int cz = base; cz < base + 4; cz++) {
                Chunk chunk = world.getChunkProvider().provideChunk(cx, cz);
                require(chunk.isTerrainPopulated(), "Audit chunk has not populated");
                blocks.update(chunk.getBiomeArray());
                for (int y = 0; y < 256; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            int wx = (cx << 4) + x;
                            int wz = (cz << 4) + z;
                            position.setPos(wx, y, wz);
                            net.minecraft.block.state.IBlockState state = chunk.getBlockState(position);
                            number(blocks, Block.getStateId(state));
                            if (state.getBlock() != Blocks.IRON_ORE) continue;
                            MessageDigest coordinates;
                            if (y >= 192) { deposit++; coordinates = depositPositions; }
                            else { ordinary++; coordinates = ordinaryPositions; }
                            number(coordinates, wx);
                            number(coordinates, y);
                            number(coordinates, wz);
                        }
                    }
                }
            }
        }
        result.addProperty("ordinary", ordinary);
        result.addProperty("deposit", deposit);
        result.addProperty("blocks_sha256", hex(blocks.digest()));
        result.addProperty("ordinary_sha256", hex(ordinaryPositions.digest()));
        result.addProperty("deposit_sha256", hex(depositPositions.digest()));
        return result;
    }

    private static void number(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte value : bytes) text.append(String.format("%02x", value & 255));
        return text.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
