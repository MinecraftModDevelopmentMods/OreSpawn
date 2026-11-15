package zone.moddev.mc.orespawn.api;

import net.minecraft.util.ResourceLocation;
import net.minecraft.block.state.IBlockState;

/** Read-only provenance of the natural substrate, before ores and structures. */
public final class FormationSample {
    private final ResourceLocation id;
    private final int revision;
    private final IBlockState parent;
    private final FormationMaterial material;
    public FormationSample(ResourceLocation id, int revision, IBlockState parent, FormationMaterial material) {
        this.id = id; this.revision = revision; this.parent = parent; this.material = material;
    }
    public ResourceLocation id() { return id; }
    public int revision() { return revision; }
    public IBlockState parent() { return parent; }
    public FormationMaterial material() { return material; }
}
