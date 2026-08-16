package zone.moddev.mc.orespawn.testmod.mixin;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.gametest.framework.GameTestMainUtil;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserves the first GameTest universe so the second phase is a real reload. */
@Mixin(GameTestMainUtil.class)
abstract class GameTestMainUtilMixin {
	@Inject(method = "createOrResetDir", at = @At("HEAD"), cancellable = true, remap = false)
	private static void surfaceprobe$preserveReloadUniverse(String universePath,
			CallbackInfo callback) {
		if (!"reload".equals(System.getProperty("surfaceprobe.integrationPhase"))) return;
		Path universe = Path.of(universePath);
		if (!Files.isDirectory(universe)) {
			throw new IllegalStateException(
					"Surface integration reload universe is missing: " + universe);
		}
		callback.cancel();
	}
}
