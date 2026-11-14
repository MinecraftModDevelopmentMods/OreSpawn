package zone.moddev.mc.orespawn.worldgen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.WorldGenRegion;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.LakesConfig;
import zone.moddev.mc.orespawn.api.FormationSample;
import zone.moddev.mc.orespawn.api.GeologyColumn;
import zone.moddev.mc.orespawn.api.GeologySampler;
import zone.moddev.mc.orespawn.api.OreSpawnApi;

/** Restores only a native lava lake's plain-stone rim where it covered a planned formation. */
final class VanillaLakeCompatibility extends ContextFeature<LakesConfig> {
    static final VanillaLakeCompatibility FEATURE = new VanillaLakeCompatibility();

    @Override boolean place(FeaturePlaceContext<LakesConfig> context) {
        IWorld world = context.level();
        if (context.config().field_202438_a != Blocks.LAVA
                || !(world.getWorld() instanceof WorldServer)
                || !NaturalFormations.enabledForPlacement(world.getWorld()))
            return delegate(context);
        Optional<GeologySampler> sampler = OreSpawnApi.createSampler((WorldServer) world.getWorld());
        if (!sampler.isPresent()) return delegate(context);
        BlockPos origin = context.origin();
        while (origin.getY() > 5 && world.isAirBlock(origin)) origin = origin.down();
        if (origin.getY() <= 4) return delegate(context);
        // Unlike 1.10's WorldGenLakes, the 1.13 feature does not subtract eight from X/Z.
        origin = origin.down(4);
        Map<BlockPos, IBlockState> original = new LinkedHashMap<>();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            GeologyColumn column = null;
            for (int y = 0; y < 8; y++) {
                BlockPos at = origin.add(x, y, z);
                if (!readable(world, at)) continue;
                IBlockState state = world.getBlockState(at);
                if (NaturalFormations.material(state) == null || world.getTileEntity(at) != null) continue;
                if (column == null) column = sampler.get().sampleColumn(at.getX(), at.getZ(), 64);
                Optional<FormationSample> sample = column.formationAt(at.getY());
                if (sample.isPresent() && sample.get().material().state().equals(state)) original.put(at, state);
            }
        }
        boolean generated = delegate(context);
        for (Map.Entry<BlockPos, IBlockState> entry : original.entrySet()) {
            BlockPos at = entry.getKey();
            if (readable(world, at) && world.getBlockState(at).equals(Blocks.STONE.getDefaultState())
                    && world.getTileEntity(at) == null) world.setBlockState(at, entry.getValue(), 2);
        }
        return generated;
    }

    private static boolean delegate(FeaturePlaceContext<LakesConfig> context) {
        return Feature.LAKES.func_212245_a(AquiferMaterialSubstitution.nativeDecorationView(context.level()), context.chunkGenerator(),
                context.random(), context.origin(), context.config());
    }

    private static boolean readable(IWorld world, BlockPos at) {
        return at.getY() >= 0 && at.getY() < 256 && (world instanceof WorldGenRegion
                ? ((WorldGenRegion) world).isChunkInBounds(at.getX() >> 4, at.getZ() >> 4)
                : ((WorldServer) world.getWorld()).getChunkProvider().chunkExists(at.getX() >> 4, at.getZ() >> 4));
    }
}
