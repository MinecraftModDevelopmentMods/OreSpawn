package zone.moddev.mc.orespawn.basemetals111;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Minimal block registry around the exact historical Base Metals provider. */
@Mod(modid = BaseMetals111Fixture.MODID, name = "Base Metals 1.11 Provider Fixture",
		version = "2.5.0-beta", acceptableRemoteVersions = "*")
@Mod.EventBusSubscriber(modid = BaseMetals111Fixture.MODID)
public final class BaseMetals111Fixture {
	public static final String MODID = "basemetals";
	private static final String[] ORES = {
			"coldiron_ore", "adamantine_ore", "starsteel_ore", "copper_ore",
			"silver_ore", "tin_ore", "lead_ore", "zinc_ore", "mercury_ore",
			"nickel_ore", "platinum_ore"
	};

	public BaseMetals111Fixture() { }

	@SubscribeEvent
	public static void registerBlocks(RegistryEvent.Register<Block> event) {
		for (String name : ORES) {
			event.getRegistry().register(new Block(Material.ROCK)
					.setRegistryName(MODID, name).setUnlocalizedName(MODID + "." + name));
		}
	}
}
