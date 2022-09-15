	package com.mcmoddev.orespawn;

	import com.mcmoddev.orespawn.data.Config;
	import com.mcmoddev.orespawn.utils.Loaders;
	import net.minecraftforge.fml.common.Mod;
	import net.minecraftforge.fml.config.ModConfig;
	import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
	import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

	import org.apache.logging.log4j.LogManager;
	import org.apache.logging.log4j.Logger;

	@Mod("orespawn")
	public class OreSpawn {
		// Directly reference a log4j logger.
		public static final Logger LOGGER = LogManager.getFormatterLogger();

		public OreSpawn() {
			// Register the setup method for modloading
			// find and load the configs - for this we just use a custom class...
//			Loaders.loadConfigs();
			FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
//			FMLJavaModLoadingContext.get().getModEventBus().addListener(this::modConfig);
			// register features so they can be used elsewhere
			Features.loadAndRegister();
		}

/*
		public void modConfig(ModConfig.ModConfigEvent event)
		{
			ModConfig config = event.getConfig();
			Config.refresh();
		}
*/

		private void setup(final FMLCommonSetupEvent event) {
		}

		@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
		public static class RegistryEvents {
		}
	}
