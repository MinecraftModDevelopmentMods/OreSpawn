package zone.moddev.mc.orespawn.commands.admin;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge31TestBootstrap;

class LeafSnapshotTest {
    @BeforeAll static void bootstrap() throws Exception { Forge31TestBootstrap.registerVanilla(); }

    @Test void protectedVanillaLeafDistanceDoesNotInvalidateAPreview() {
        for (net.minecraft.block.Block block : new net.minecraft.block.Block[] {
                Blocks.OAK_LEAVES, Blocks.BIRCH_LEAVES, Blocks.SPRUCE_LEAVES,
                Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES}) {
            BlockState leaf = block.getDefaultState();
            assertEquals(DiagnosticStates.snapshotState(leaf.with(LeavesBlock.DISTANCE, 1), false),
                    DiagnosticStates.snapshotState(leaf.with(LeavesBlock.DISTANCE, 7), false));
        }
    }

    @Test void persistenceAndBlockChangesStillInvalidateTheSnapshot() {
        BlockState leaf = Blocks.OAK_LEAVES.getDefaultState();
        assertNotEquals(DiagnosticStates.snapshotState(leaf.with(LeavesBlock.PERSISTENT, false), false),
                DiagnosticStates.snapshotState(leaf.with(LeavesBlock.PERSISTENT, true), false));
        assertNotEquals(DiagnosticStates.snapshotState(leaf, false),
                DiagnosticStates.snapshotState(Blocks.AIR.getDefaultState(), false));
    }

    @Test void editableStatesAndReportsKeepTheirExactProperties() {
        BlockState leaf = Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.DISTANCE, 2);
        assertSame(leaf, DiagnosticStates.snapshotState(leaf, true));
        assertEquals(leaf, DiagnosticStates.resolve(DiagnosticStates.key(leaf)));
        assertSame(Blocks.STONE.getDefaultState(), DiagnosticStates.snapshotState(Blocks.STONE.getDefaultState(), false));
    }
}
