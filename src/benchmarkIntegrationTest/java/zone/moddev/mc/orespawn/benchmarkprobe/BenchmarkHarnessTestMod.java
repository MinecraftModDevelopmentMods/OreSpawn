package zone.moddev.mc.orespawn.benchmarkprobe;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Build-only test that gives NeoForge 20.6's benchmark GameTest server a harness-owned result. */
@Mod(BenchmarkHarnessTestMod.MODID)
@GameTestHolder(BenchmarkHarnessTestMod.MODID)
public final class BenchmarkHarnessTestMod {
	static final String MODID = "benchmarkprobe";

	@PrefixGameTestTemplate(false)
	@GameTest(templateNamespace = "minecraft", template = "igloo/top")
	public static void benchmarkLifecycle(GameTestHelper helper) {
		helper.succeed();
	}
}
