package zone.moddev.mc.orespawn.api;

import java.util.function.IntSupplier;

/** Column-scoped base geology and lazy, non-chunk-loading natural land elevation. */
public final class RockFormationContext {
    private final long seed;
    private final GeologyColumn base;
    private final float temperature, rainfall;
    private final IntSupplier surface;
    private int height = Integer.MIN_VALUE;
    public RockFormationContext(long seed, GeologyColumn base, float temperature, float rainfall, IntSupplier surface) {
        this.seed = seed; this.base = base; this.temperature = temperature; this.rainfall = rainfall; this.surface = surface;
    }
    public long seed() { return seed; }
    public GeologyColumn base() { return base; }
    public float temperature() { return temperature; }
    public float rainfall() { return rainfall; }
    /** Highest natural solid terrain Y, or -1 for wet/unsupported terrain. */
    public int surfaceY() {
        if (height == Integer.MIN_VALUE) height = surface.getAsInt();
        return height;
    }
}
