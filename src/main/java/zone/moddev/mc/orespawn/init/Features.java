package zone.moddev.mc.orespawn.init;

import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.worldgen.OreSpawnOreGeneration;
import zone.moddev.mc.orespawn.worldgen.FlatBedrockFeature;
import zone.moddev.mc.orespawn.worldgen.FluidDepositFeature;
import zone.moddev.mc.orespawn.worldgen.StoneReplacer;
import zone.moddev.mc.orespawn.worldgen.BiomeSurfaceFeature;
import zone.moddev.mc.orespawn.worldgen.VanillaOreFeatureGate;
import zone.moddev.mc.orespawn.worldgen.VanillaSpringCompatibility;

import net.minecraft.world.gen.feature.Feature;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OreSpawn.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Features {
	@SubscribeEvent
	public static void registerFeatures(RegistryEvent.Register<Feature<?>> event) {
		event.getRegistry().register(StoneReplacer.FEATURE);
		event.getRegistry().register(OreSpawnOreGeneration.FEATURE);
		event.getRegistry().register(FluidDepositFeature.FEATURE);
		event.getRegistry().register(FlatBedrockFeature.FEATURE);
		event.getRegistry().register(BiomeSurfaceFeature.FEATURE);
		event.getRegistry().register(VanillaSpringCompatibility.FEATURE);
		VanillaOreFeatureGate.registerFeatures(event.getRegistry());
	}

	private Features() {
		throw new IllegalAccessError("Not an instantiable class");
	}
}
