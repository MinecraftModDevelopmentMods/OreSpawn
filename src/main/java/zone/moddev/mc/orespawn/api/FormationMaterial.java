package zone.moddev.mc.orespawn.api;

import net.minecraft.block.BlockState;
import java.util.Optional;

/** Baked natural substrate. A null family denotes regolith or vein fill, not a layer family. */
public final class FormationMaterial {
    private final BlockState state;
    private final Optional<GeologyFamily> family;
    public FormationMaterial(BlockState state, GeologyFamily family) {
        this.state = java.util.Objects.requireNonNull(state);
        this.family = Optional.ofNullable(family);
    }
    public BlockState state() { return state; }
    public Optional<GeologyFamily> family() { return family; }
}
