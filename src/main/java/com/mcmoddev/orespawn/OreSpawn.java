package com.mcmoddev.orespawn;

import com.mcmoddev.orespawn.world.features.Features;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("orespawn")
public class OreSpawn {
	// Directly reference a log4j logger.
	public static final Logger LOGGER = LogManager.getLogger();

	public OreSpawn() {
		// Register the setup method for modloading
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
		/*
		// Register the enqueueIMC method for modloading
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueIMC);
		// Register the processIMC method for modloading
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);
		// Register the doClientStuff method for modloading
		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);
		// Register the doClientStuff method for modloading

		// Register ourselves for server and other game events we are interested in
		MinecraftForge.EVENT_BUS.addListener(this::itemRegistryEvent);
		MinecraftForge.EVENT_BUS.addListener(this::doServerStartTasks);
		 */
		MinecraftForge.EVENT_BUS.register(this);
	}

	private void setup(final FMLCommonSetupEvent event) {
		event.enqueueWork( () -> Registry.register(Registry.FEATURE, new ResourceLocation("orespawn4", "default"), Features.DEFAULT) );
	}

    /*
	private void doClientStuff(final FMLClientSetupEvent event) {
	}

	private void enqueueIMC(final InterModEnqueueEvent event) {
	}

	private void processIMC(final InterModProcessEvent event) {
	}

	private void itemRegistryEvent(final RegistryEvent.Register<Item> ev) {

	}

	private void doServerStartTasks(final FMLServerStartingEvent ev) {
	}
	*/
}
