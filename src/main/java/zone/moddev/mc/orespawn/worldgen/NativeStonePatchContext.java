package zone.moddev.mc.orespawn.worldgen;

import net.minecraftforge.event.terraingen.OreGenEvent.GenerateMinable.EventType;
import java.util.ArrayDeque;
import java.util.Deque;

/** Server-thread context for vanilla's non-metal stone-patch passes only. */
public final class NativeStonePatchContext {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<Deque<Boolean>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private NativeStonePatchContext() { }

    static void select(boolean formationsEnabled, EventType type) {
        if (formationsEnabled && (type == EventType.GRANITE || type == EventType.DIORITE || type == EventType.ANDESITE))
            ACTIVE.set(Boolean.TRUE);
        else reset();
    }
    public static boolean active() { return Boolean.TRUE.equals(ACTIVE.get()); }
    static void reset() { ACTIVE.set(Boolean.FALSE); }
    static void begin() { FRAMES.get().push(active()); reset(); }
    static void end() {
        Deque<Boolean> frames = FRAMES.get();
        ACTIVE.set(frames.isEmpty() ? Boolean.FALSE : frames.pop());
    }
    static void clear() { ACTIVE.remove(); FRAMES.remove(); }
}
