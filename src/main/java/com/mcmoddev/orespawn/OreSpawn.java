package com.mcmoddev.orespawn;

import com.mcmoddev.orespawn.world.features.Features;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.gen.feature.Feature;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("orespawn4")
public class OreSpawn {
	// Directly reference a log4j logger.
	public static final Logger LOGGER = LogManager.getLogger();

	public OreSpawn() {
		// Register the setup method for modloading
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
	}


	private void setup(final FMLCommonSetupEvent event) {
//		event.enqueueWork( () -> Registry.register(Registry.FEATURE, new ResourceLocation("orespawn4", "default"), Features.DEFAULT) );
	}

	@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
	public static class RegistryEvents {
		@SubscribeEvent
		/**
		 *
		 */
		public static void featureRegistryEvent(final RegistryEvent.Register<Feature<?>> event) {
			event.getRegistry().register(Features.DEFAULT.setRegistryName("default"));
			LOGGER.info("Registered %s", Features.DEFAULT.getRegistryName());
		}
	}
}
