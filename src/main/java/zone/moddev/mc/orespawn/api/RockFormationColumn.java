package zone.moddev.mc.orespawn.api;

/** Prepared immutable column; return null where this formation does not apply. No per-block allocation. */
@FunctionalInterface
public interface RockFormationColumn {
    FormationMaterial at(int y);
    /** Optional alteration parent; null uses the preceding natural column at this Y. */
    default net.minecraft.block.state.IBlockState parentAt(int y) { return null; }
}
