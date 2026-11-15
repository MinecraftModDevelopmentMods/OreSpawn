package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.WorldGenLakes;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.tileentity.TileEntity;
import org.junit.jupiter.api.*;
import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;
import zone.moddev.mc.orespawn.api.*;
import net.minecraft.util.ResourceLocation;

class VanillaLakeCompatibilityTest {
    @BeforeAll static void bootstrap() { Forge12TestBootstrap.registerVanilla(); }
    @BeforeEach void formation() {
        JsonObject rule = NaturalFormationTest.rule("barren_pipe");
        JsonObject material = new JsonObject(); material.addProperty("block", "minecraft:diamond_block");
        JsonObject materials = new JsonObject(); materials.add("pipe", material); rule.add("materials", materials);
        JsonObject rules = new JsonObject(); rules.add("test:pipe", rule);
        JsonObject profile = new JsonObject(); profile.add("rock_formations", rules);
        NaturalFormations.install(profile);
    }
    @AfterEach void clear() { NaturalFormations.install(new JsonObject()); VanillaLakeCompatibility.clear(); }

    private StateWorld world(Map<BlockPos, IBlockState> cells) {
        try {
            // No real save/provider/chunk is constructed by this isolated state map.
            Class<?> type=Class.forName("sun.misc.Unsafe");
            java.lang.reflect.Field field=type.getDeclaredField("theUnsafe");field.setAccessible(true);
            StateWorld world=(StateWorld)type.getMethod("allocateInstance",Class.class).invoke(field.get(null),StateWorld.class);
            world.cells=cells;world.unloaded=new HashSet<>();world.read=new HashSet<>();
            return world;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static final class StateWorld extends World {
        Map<BlockPos,IBlockState> cells;Set<BlockPos> unloaded,read;BlockPos tileAt;
        private StateWorld(){super(null,null,null,null,false);}
        protected IChunkProvider createChunkProvider(){return null;}
        protected boolean isChunkLoaded(int x,int z,boolean empty){return true;}
        public boolean isBlockLoaded(BlockPos pos,boolean empty){return !unloaded.contains(pos);}
        public boolean isAirBlock(BlockPos pos){return getBlockState(pos).getBlock()==Blocks.AIR;}
        public IBlockState getBlockState(BlockPos pos){read.add(pos.toImmutable());return cells.getOrDefault(pos,Blocks.STONE.getDefaultState());}
        public TileEntity getTileEntity(BlockPos pos){read.add(pos.toImmutable());return pos.equals(tileAt)?new TileEntity(){}:null;}
        public boolean setBlockState(BlockPos pos,IBlockState state,int flags){assertEquals(2,flags);cells.put(pos.toImmutable(),state);return true;}
    }

    private boolean generate(World world,Random random,BlockPos position,WorldGenLakes lake) {
        return VanillaLakeCompatibility.generate(world,random,position,lake,sampler(Blocks.DIAMOND_BLOCK.getDefaultState()));
    }

    private GeologySampler sampler(IBlockState material) {
        return (x,z,surface) -> new GeologyColumn() {
            GeologyColumn base=NaturalFormationTest.base(x,z);
            public ResourceLocation dimension(){return base.dimension();}
            public ResourceLocation biome(){return base.biome();}
            public ResourceLocation geome(){return base.geome();}
            public int blockX(){return x;}public int blockZ(){return z;}public int surfaceY(){return surface;}
            public IBlockState rockAt(int y){return material==null?Blocks.STONE.getDefaultState():material;}
            public Optional<GeologyFamily> familyAt(int y){return Optional.empty();}
            public Optional<FormationSample> formationAt(int y){
                return material==null?Optional.empty():Optional.of(new FormationSample(new ResourceLocation("test:pipe"),1,
                        Blocks.STONE.getDefaultState(),new FormationMaterial(material,null)));
            }
        };
    }

    @Test void lakeBeforeAndAfterFormationRetainTheSameSubstrate() {
        BlockPos at = new BlockPos(-281, 44, -667), lakeAt = new BlockPos(-280, 48, -664);
        for (boolean formationFirst : new boolean[]{false,true}) {
            Map<BlockPos,IBlockState> cells = new HashMap<>(); World world = world(cells);
            if (formationFirst) cells.put(at, Blocks.DIAMOND_BLOCK.getDefaultState());
            WorldGenLakes lake = new WorldGenLakes(Blocks.LAVA) {
                public boolean generate(World w, Random r, BlockPos p) {
                    w.setBlockState(at, Blocks.STONE.getDefaultState(), 2); return true;
                }
            };
            assertTrue(generate(world,new Random(7),lakeAt,lake));
            if (!formationFirst) cells.put(at,Blocks.DIAMOND_BLOCK.getDefaultState());
            assertEquals(Blocks.DIAMOND_BLOCK.getDefaultState(), cells.get(at));
        }
    }

    @Test void cavityFluidsOresAndTileEntitiesAreNotRestored() {
        BlockPos at = new BlockPos(3,44,3), lakeAt = new BlockPos(8,48,8);
        for (IBlockState output : new IBlockState[]{Blocks.AIR.getDefaultState(),Blocks.LAVA.getDefaultState(),
                Blocks.WATER.getDefaultState(),Blocks.IRON_ORE.getDefaultState()}) {
            Map<BlockPos,IBlockState> cells = new HashMap<>();cells.put(at,Blocks.DIAMOND_BLOCK.getDefaultState());
            World world = world(cells);
            generate(world,new Random(9),lakeAt,new WorldGenLakes(Blocks.LAVA) {
                public boolean generate(World w,Random r,BlockPos p){w.setBlockState(at,output,2);return true;}
            });
            assertEquals(output,cells.get(at));
        }
        Map<BlockPos,IBlockState> cells = new HashMap<>();cells.put(at,Blocks.DIAMOND_BLOCK.getDefaultState());
        StateWorld world = world(cells);world.tileAt=at;
        generate(world,new Random(9),lakeAt,new WorldGenLakes(Blocks.LAVA) {
            public boolean generate(World w,Random r,BlockPos p){w.setBlockState(at,Blocks.STONE.getDefaultState(),2);return true;}
        });
        assertEquals(Blocks.STONE.getDefaultState(),cells.get(at));
    }

    @Test void ordinaryRocksAreNotProtectedAndUnloadedCellsAreNotRead() {
        BlockPos at = new BlockPos(3,44,3), unloaded = new BlockPos(4,44,3);
        Map<BlockPos,IBlockState> cells = new HashMap<>();cells.put(at,Blocks.GOLD_BLOCK.getDefaultState());
        StateWorld world = world(cells);world.unloaded.add(unloaded);
        generate(world,new Random(9),new BlockPos(8,48,8),new WorldGenLakes(Blocks.LAVA) {
            public boolean generate(World w,Random r,BlockPos p){w.setBlockState(at,Blocks.STONE.getDefaultState(),2);return true;}
        });
        assertEquals(Blocks.STONE.getDefaultState(),cells.get(at));
        assertFalse(world.read.contains(unloaded));
    }

    @Test void coordinateAndLakeRandomStreamsMatchVanillaExactly() {
        for (long seed=0;seed<200;seed++) {
            Random expected=new Random(seed),actual=new Random(seed);
            int x=expected.nextInt(16)+8,y=expected.nextInt(expected.nextInt(248)+8),z=expected.nextInt(16)+8;
            boolean selected=y<63||expected.nextInt(80/8)==0;
            BlockPos origin=new BlockPos(-320,0,-688);
            assertEquals(selected?origin.add(x,y,z):null,VanillaLakeCompatibility.choosePosition(actual,origin,63,80));
            assertEquals(expected.nextLong(),actual.nextLong());
        }
        Random expected=new Random(51),actual=new Random(51);
        expected.nextLong();expected.nextInt(73);
        generate(world(new HashMap<>()),actual,new BlockPos(8,48,8),new WorldGenLakes(Blocks.LAVA) {
            public boolean generate(World w,Random r,BlockPos p){r.nextLong();r.nextInt(73);return false;}
        });
        assertEquals(expected.nextLong(),actual.nextLong());
    }

    @Test void declaredMaterialOutsideItsFormationOrWithDifferentProvenanceIsNotProtected() {
        BlockPos at=new BlockPos(3,44,3);
        for (IBlockState expected : new IBlockState[]{null,Blocks.GOLD_BLOCK.getDefaultState()}) {
            Map<BlockPos,IBlockState> cells=new HashMap<>();cells.put(at,Blocks.DIAMOND_BLOCK.getDefaultState());
            VanillaLakeCompatibility.generate(world(cells),new Random(17),new BlockPos(8,48,8),new WorldGenLakes(Blocks.LAVA) {
                public boolean generate(World w,Random r,BlockPos p){w.setBlockState(at,Blocks.STONE.getDefaultState(),2);return true;}
            },sampler(expected));
            assertEquals(Blocks.STONE.getDefaultState(),cells.get(at));
        }
    }
}
