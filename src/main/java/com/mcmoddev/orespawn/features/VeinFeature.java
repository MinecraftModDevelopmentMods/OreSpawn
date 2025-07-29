package com.mcmoddev.orespawn.features;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.features.configs.VeinConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class VeinFeature extends Feature<VeinConfiguration> {
    private static final String eMess = "Value %d out of range (1-6)";

    public VeinFeature() {
        super(VeinConfiguration.CODEC);
        OreSpawn.LOGGER.info("VeinFeature created");
    }

    @Override
    public boolean place(FeaturePlaceContext<VeinConfiguration> pContext) {
        RandomSource randomsource = pContext.random();
        BlockPos blockpos = pContext.origin();
        WorldGenLevel worldgenlevel = pContext.level();
        VeinConfiguration veinconfiguration = pContext.config();

        double startX = blockpos.getX();
        double startY = blockpos.getY();
        double startZ = blockpos.getZ();
        int length = randomsource.nextInt(veinconfiguration.minLength, veinconfiguration.maxLength);
        return doPlacement(worldgenlevel, randomsource, veinconfiguration, startX, startY, startZ, length);
    }

    private boolean doPlacement(WorldGenLevel pLevel, RandomSource pRandom, VeinConfiguration pConfig, double startX, double startY, double startZ, int length) {
        OreSpawn.LOGGER.info("VeinFeature called to spawn a vein at {}, {}, {}", startX, startY, startZ);
        int cn = 0;
        int ls = -1;
        int ns = -1;
        BlockPos curpos = new BlockPos((int) startX, (int) startY, (int) startZ);
        List<Pair<BlockPos, Integer>> seen = new ArrayList<>();
        while (cn <= length) {
            ns = pRandom.nextInt(1, 6);
            if (ns == ls)
                while (ns == ls)
                    ns = pRandom.nextInt(1, 6);

            if (ls == -1)
                seen.add(Pair.of(curpos, ns));
            curpos = switch (ns) {
                case 1 -> curpos.above();
                case 2 -> curpos.below();
                case 3 -> curpos.east();
                case 4 -> curpos.west();
                case 5 -> curpos.north();
                case 6 -> curpos.south();
                default -> throw new RuntimeException(String.format(eMess, ns));
            };

            ls = ns;
            if (seen.contains(Pair.of(curpos, ls))) break; // early exit, we've crossed our previous track
            cn += 1;
        }
        seen.add(Pair.of(curpos, ns));
        seen.forEach(bp -> makeNodeAt(pLevel, pConfig, bp, pRandom));
        return true;
    }

    private void makeNodeAt(WorldGenLevel pLevel, VeinConfiguration pConfig, Pair<BlockPos, Integer> pLoc, RandomSource pRandom) {
        // the right side of each loc determines if the node generates vertical or horizontal
        switch (pLoc.getRight()) {
            case 1:
            case 2:
                makeHorizontal(pLevel, pLoc.getLeft(), pConfig, pRandom);
                break;
            case 3:
            case 4:
                makeVertical(pLevel, pLoc.getLeft(), pConfig, pRandom, true);
                break;
            case 5:
            case 6:
                makeVertical(pLevel, pLoc.getLeft(), pConfig, pRandom, false);
                break;
            default:
                throw new RuntimeException(String.format(eMess, pLoc.getRight()));
        }
    }

    private static double getRadiusOfArea(int area) {
        double base = (double)area/Math.PI;
        double res = Math.sqrt(base);
        return res;
    }

    private static Pair<Double, Double> paraCirc(double r, double curT) {
        return Pair.of(r * Math.sin(curT), r * Math.cos(curT));
    }

    private static Pair<Integer, Integer> paraCircCoords(int cx, int cy, double r, double curT) {
        Pair<Double, Double> base = paraCirc(r, curT);
        return Pair.of((int) (cx + base.getLeft()), (int) (cy + base.getRight()));
    }

    private void makeHorizontal(WorldGenLevel pLevel, BlockPos pos, VeinConfiguration pConfig, RandomSource pRandom) {
    	OreSpawn.LOGGER.debug("[Vein] makeHorizontal start at {}  size={}  maxR={}", pos, pConfig.size, getRadiusOfArea(pConfig.size));
        try (BulkSectionAccess bulksectionaccess = new BulkSectionAccess(pLevel)) {
            List<Pair<Integer, Integer>> placed = new LinkedList<>();

            BlockPos.MutableBlockPos accessPos = pos.mutable();
            //OreSpawn.LOGGER.info("VeinFeature starting at {}", accessPos);

            if (pLevel.ensureCanWrite(accessPos)) {
                LevelChunkSection section = bulksectionaccess.getSection(accessPos);
                if (section != null) {
                    //OreSpawn.LOGGER.info("section != null");
                    int relX = SectionPos.sectionRelative(accessPos.getX());
                    int relY = SectionPos.sectionRelative(accessPos.getY());
                    int relZ = SectionPos.sectionRelative(accessPos.getZ());
                    BlockState blockstate = section.getBlockState(relX, relY, relZ);
                    if (!placed.contains(Pair.of(accessPos.getX(), accessPos.getZ()))) {
                        //OreSpawn.LOGGER.info("!placed.contains(Pair.of(...))  targets: {}", pConfig.targetStates.size());
                        for (VeinConfiguration.TargetBlockState tgt : pConfig.targetStates) {
                            //OreSpawn.LOGGER.info("Target state: {} ({})-- test: {}", tgt, blockstate, tgt.target.test(blockstate, pRandom));
                            if (tgt.target.test(blockstate, pRandom)) {
                                placed.add(Pair.of(accessPos.getX(), accessPos.getZ()));
                                //OreSpawn.LOGGER.info("calling setBlock (pLevel.setBlock(...))");
                                //pLevel.setBlock(accessPos, tgt.state, 2);
                                
                                int rx = SectionPos.sectionRelative(accessPos.getX());
                                int ry = SectionPos.sectionRelative(accessPos.getY());
                                int rz = SectionPos.sectionRelative(accessPos.getZ());
                                OreSpawn.LOGGER.info("section.setBlockState({}, {}, {}, {}, false)", rx, ry, rz, tgt.state);
                                section.setBlockState(rx, ry, rz, tgt.state, false);
                            }
                        }
                    }
                }
            }
            //double maxRadius = Math.min(getRadiusOfArea(pConfig.size), 8); // TODO: make this configurable - added for now for testing
            //for (double r = 1; r <= maxRadius; r++) { //  maxRadius=getRadiusOfArea(pConfig.size)
            
            
            for (double r = 1; r <= getRadiusOfArea(pConfig.size); r++) {
                double div = Math.pow(r*2+1, 2);
                double step = Math.TAU / div;
                for (double c = 0; c <= Math.TAU; c += step) {
                    int left = pos.getX();
                    int right = pos.getZ();
                    Pair<Integer, Integer> tl = paraCircCoords(left, right, r, c);
                    accessPos.set(tl.getLeft(), pos.getY(), tl.getRight());
                    if (pLevel.ensureCanWrite(accessPos)) {
                        LevelChunkSection section = bulksectionaccess.getSection(accessPos);
                        if (section != null) {
                            //OreSpawn.LOGGER.info("section != null");
                            int relX = SectionPos.sectionRelative(accessPos.getX());
                            int relY = SectionPos.sectionRelative(accessPos.getY());
                            int relZ = SectionPos.sectionRelative(accessPos.getZ());
                            BlockState blockstate = section.getBlockState(relX, relY, relZ);
                            if (!placed.contains(tl)) {
                                //OreSpawn.LOGGER.info("!placed.contains(tl)");
                                for (VeinConfiguration.TargetBlockState tgt : pConfig.targetStates) {
                                    //OreSpawn.LOGGER.info("Target state: {} ({})-- test: {}", tgt, blockstate, tgt.target.test(blockstate, pRandom));
                                    if (tgt.target.test(blockstate, pRandom)) {
                                        placed.add(tl);
                                        //OreSpawn.LOGGER.info("calling setBlock (pLevel.setBlock(...))");
                                        //pLevel.setBlock(accessPos, tgt.state, 2);
                                        
                                        int rx = SectionPos.sectionRelative(accessPos.getX());
                                        int ry = SectionPos.sectionRelative(accessPos.getY());
                                        int rz = SectionPos.sectionRelative(accessPos.getZ());
                                        OreSpawn.LOGGER.info("section.setBlockState({}, {}, {}, {}, false)", rx, ry, rz, tgt.state);
                                        section.setBlockState(rx, ry, rz, tgt.state, false);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        OreSpawn.LOGGER.debug("[Vein] makeHorizontal done at {}", pos);
    }

    private void makeVertical(WorldGenLevel pLevel, BlockPos pos, VeinConfiguration pConfig, RandomSource pRandom, boolean northSouth) {
    	OreSpawn.LOGGER.debug("[Vein] makeVertical start at {}  size={}  maxR={}", pos, pConfig.size, getRadiusOfArea(pConfig.size));
        // northsouth == true, manipulate XY else manipulate ZY
        try (BulkSectionAccess bulksectionaccess = new BulkSectionAccess(pLevel)) {
            List<Pair<Integer, Integer>> placed = new LinkedList<>();
            BlockPos.MutableBlockPos accessPos = pos.mutable();
            OreSpawn.LOGGER.info("VeinFeature starting at {} **{}", accessPos, northSouth);

            if (pLevel.ensureCanWrite(accessPos)) {
                LevelChunkSection section = bulksectionaccess.getSection(accessPos);
                if (section != null) {
                    //OreSpawn.LOGGER.info("section != null");
                    int relX = SectionPos.sectionRelative(accessPos.getX());
                    int relY = SectionPos.sectionRelative(accessPos.getY());
                    int relZ = SectionPos.sectionRelative(accessPos.getZ());
                    BlockState blockstate = section.getBlockState(relX, relY, relZ);
                    if (!placed.contains(Pair.of(accessPos.getX(), northSouth?accessPos.getY():accessPos.getZ()))) {
                        //OreSpawn.LOGGER.info("!placed.contains(Pair.of(...))");
                        for (VeinConfiguration.TargetBlockState tgt : pConfig.targetStates) {
                            //OreSpawn.LOGGER.info("Target state: {} ({})-- test: {}", tgt, blockstate, tgt.target.test(blockstate, pRandom));
                            if (tgt.target.test(blockstate, pRandom)) {
                                placed.add(Pair.of(accessPos.getX(), northSouth?accessPos.getY():accessPos.getZ()));
                                //OreSpawn.LOGGER.info("calling setBlock (pLevel.setBlock(...))");
                                //pLevel.setBlock(accessPos, tgt.state, 2);
                                
                                int rx = SectionPos.sectionRelative(accessPos.getX());
                                int ry = SectionPos.sectionRelative(accessPos.getY());
                                int rz = SectionPos.sectionRelative(accessPos.getZ());
                                OreSpawn.LOGGER.info("section.setBlockState({}, {}, {}, {}, false)", rx, ry, rz, tgt.state);
                                section.setBlockState(rx, ry, rz, tgt.state, false);
                            }
                        }
                    }
                }
            }

            for (double r = 1; r <= getRadiusOfArea(pConfig.size); r++) {
                double div = Math.pow(r*2+1, 2);
                double step = Math.TAU / div;
                for (double c = 0; c <= Math.TAU; c += step) {
                    int left = pos.getX();
                    int right = northSouth?pos.getY():pos.getZ();
                    Pair<Integer, Integer> tl = paraCircCoords(left, right, r, c);
                    if (northSouth) {
                        accessPos.set(tl.getLeft(), tl.getRight(), pos.getZ());
                    } else {
                        accessPos.set(pos.getX(), tl.getRight(), tl.getLeft());
                    }

                    if (pLevel.ensureCanWrite(accessPos)) {
                        LevelChunkSection section = bulksectionaccess.getSection(accessPos);
                        if (section != null) {
                            //OreSpawn.LOGGER.info("section != null");
                            int relX = SectionPos.sectionRelative(accessPos.getX());
                            int relY = SectionPos.sectionRelative(accessPos.getY());
                            int relZ = SectionPos.sectionRelative(accessPos.getZ());
                            BlockState blockstate = section.getBlockState(relX, relY, relZ);
                            if (!placed.contains(tl)) {
                                //OreSpawn.LOGGER.info("!placed.contains(tl)");
                                for (VeinConfiguration.TargetBlockState tgt : pConfig.targetStates) {
                                    //OreSpawn.LOGGER.info("Target state: {} ({})-- test: {}", tgt, blockstate, tgt.target.test(blockstate, pRandom));
                                    if (tgt.target.test(blockstate, pRandom)) {
                                        placed.add(tl);
                                        //OreSpawn.LOGGER.info("calling setBlock (pLevel.setBlock(...))");
                                        //pLevel.setBlock(accessPos, tgt.state, 2);
                                        
                                        int rx = SectionPos.sectionRelative(accessPos.getX());
                                        int ry = SectionPos.sectionRelative(accessPos.getY());
                                        int rz = SectionPos.sectionRelative(accessPos.getZ());
                                        OreSpawn.LOGGER.info("section.setBlockState({}, {}, {}, {}, false)", rx, ry, rz, tgt.state);
                                        section.setBlockState(rx, ry, rz, tgt.state, false);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        OreSpawn.LOGGER.debug("[Vein] makeVertical done at {}", pos);
    }
}
