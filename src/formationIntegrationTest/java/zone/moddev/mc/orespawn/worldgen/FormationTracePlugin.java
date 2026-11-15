package zone.moddev.mc.orespawn.worldgen;

import java.util.Map;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Test-only trace of the native lava/water reaction; all other writes stay untouched. */
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"zone.moddev.mc.orespawn.worldgen.FormationTracePlugin"})
public final class FormationTracePlugin implements IFMLLoadingPlugin {
    public String[] getASMTransformerClass() { return new String[]{Transformer.class.getName()}; }
    public String getModContainerClass() { return null; }
    public String getSetupClass() { return null; }
    public void injectData(Map<String, Object> values) { }
    public String getAccessTransformerClass() { return null; }

    public static final class Transformer implements IClassTransformer {
        public byte[] transform(String name, String transformedName, byte[] bytes) {
            if (!"net.minecraft.block.BlockDynamicLiquid".equals(transformedName)) return bytes;
            ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
            int changed = 0;
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof MethodInsnNode)) continue;
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (!call.owner.equals("net/minecraft/world/World")
                            || !(call.name.equals("setBlockState") || call.name.equals("func_175656_a"))
                            || !call.desc.equals("(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)Z")) continue;
                    method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC,
                            "zone/moddev/mc/orespawn/worldgen/NativeFluidTrace", "write",
                            "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)Z", false));
                    changed++;
                }
            }
            if (changed != 1) throw new IllegalStateException("Expected exactly one native fluid-reaction write, found " + changed);
            System.setProperty("formation.nativeFluidTraceSites", Integer.toString(changed));
            ClassWriter writer = new ClassWriter(0); node.accept(writer); return writer.toByteArray();
        }
    }
}
