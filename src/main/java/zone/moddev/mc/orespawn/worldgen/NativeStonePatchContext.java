package zone.moddev.mc.orespawn.worldgen;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/** Lets rock blocks distinguish native stone decoration from metal ore placement. */
public final class NativeStonePatchContext {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private NativeStonePatchContext() { }
    public static boolean active() { return ACTIVE.get(); }
    static boolean enter(boolean enabled, Block output) {
        boolean previous = ACTIVE.get();
        ACTIVE.set(enabled && (output == Blocks.GRANITE || output == Blocks.DIORITE || output == Blocks.ANDESITE));
        return previous;
    }
    static void restore(boolean previous) { ACTIVE.set(previous); }
    static void clear() { ACTIVE.remove(); }
}
