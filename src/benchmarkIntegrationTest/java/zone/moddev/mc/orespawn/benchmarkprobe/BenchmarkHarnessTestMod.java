package zone.moddev.mc.orespawn.benchmarkprobe;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;

/** Build-only test that gives Forge 61's benchmark GameTest server a harness-owned result. */
@Mod(BenchmarkHarnessTestMod.MODID)
@GameTestHolder(BenchmarkHarnessTestMod.MODID)
public final class BenchmarkHarnessTestMod {
	static final String MODID = "benchmarkprobe";

	@GameTest(template = "minecraft:igloo/top")
	public static void benchmarkLifecycle(GameTestHelper helper) {
		helper.succeed();
	}
}
