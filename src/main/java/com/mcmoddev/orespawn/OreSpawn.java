	package com.mcmoddev.orespawn;

	import com.google.common.base.Joiner;
	import com.mcmoddev.orespawn.data.Constants;
	import net.minecraft.util.ResourceLocation;
	import net.minecraft.world.gen.feature.Feature;
	import net.minecraftforge.event.RegistryEvent;
	import net.minecraftforge.eventbus.api.SubscribeEvent;
	import net.minecraftforge.fml.ModList;
	import net.minecraftforge.fml.common.Mod;
	import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
	import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

	import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
	import org.apache.logging.log4j.LogManager;
	import org.apache.logging.log4j.Logger;

	import java.io.IOException;
	import java.nio.file.Files;
	import java.nio.file.Path;
	import java.util.ArrayList;
	import java.util.List;
	import java.util.Locale;
	import java.util.stream.Collectors;

	@Mod("orespawn4")
	public class OreSpawn {
		// Directly reference a log4j logger.
		public static final Logger LOGGER = LogManager.getFormatterLogger();

		public OreSpawn() {
			// Register the setup method for modloading
			FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);

		}

		private void setup(final FMLCommonSetupEvent event) {
			List<ResourceLocation> foundFiles = new ArrayList<>();
			ModList.get().getModFiles().stream()
					.map(ModFileInfo::getFile)
					.map(modFile -> {
						Path root = modFile.getLocator().findPath(modFile, Constants.FileBits.RESOURCE_PATH).toAbsolutePath();

						try {
							return Files.walk(root).
								map(path -> root.relativize(path.toAbsolutePath())).
								filter(path -> path.getNameCount() <= 64). // Make sure the depth is within bounds
									filter(path -> path.toString().endsWith(".json")).
								map(path -> Joiner.on('/').join(path)).
								//map(path -> path.toString()).
								map(path -> path.substring(0, path.length() - 5)).
								map(path -> new ResourceLocation(modFile.getModInfos().get(0).getModId(), path)).
								collect(Collectors.toList());
						} catch (IOException e) {
							LOGGER.error("Exception trying to get possible data from mod-resources: {}", e.getMessage());
							return new ArrayList<ResourceLocation>();
						}
					})
				.filter(lrl -> !lrl.isEmpty())
				.forEach(foundFiles::addAll);

			try {
				Path diskPath = Constants.JSONPATH.toAbsolutePath();
				List<ResourceLocation> temp = Files.walk(diskPath)
					.map(path -> diskPath.relativize(path.toAbsolutePath()))
					.filter(path -> path.getNameCount() <= 64)
					.filter(path -> path.toString().endsWith(".json"))
					.map(path -> Joiner.on('/').join(path))
					//.map(path -> path.toString())
					.map(path -> path.toLowerCase(Locale.US))
					.map(path -> path.substring(0, path.length() - 5))
					.map(path -> new ResourceLocation("orespawn-disk", path))
					.collect(Collectors.toList());
				foundFiles.addAll(temp);
			} catch (IOException e) {
				LOGGER.error("Exception trying to get possible data from config directory resources: {}", e.getMessage());
			}
			foundFiles.forEach(rl -> LOGGER.debug("Found possible data with handle {}", rl.toString()));
		}

		@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
		public static class RegistryEvents {
			@SubscribeEvent
			public static void featureRegistryEvent(final RegistryEvent.Register<Feature<?>> event) {
			}
		}
	}
