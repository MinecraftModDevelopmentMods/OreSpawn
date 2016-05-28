package cyano.basemetals;

import cyano.basemetals.data.DataConstants;
import cyano.basemetals.events.OreGenDisabler;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.Level;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;


/**
 * This is the entry point for this mod. If you are writing your own mod that 
 * uses this mod,You only need to create the appropriate JSON files in the
 * config/orespawn folder (no API .jar file required).
 * @author DrCyano
 *
 */
@Mod(
		modid = OreSpawn.MODID,
		name= OreSpawn.NAME,
		version = OreSpawn.VERSION,
		dependencies = "required-after:Forge",
		acceptedMinecraftVersions = "1.9.4)")
//		updateJSON = "https://raw.githubusercontent.com/cyanobacterium/OreSpawn/master/update.json")

public class OreSpawn
{
	
	public static OreSpawn INSTANCE = null;
	/** ID of this mod */
	public static final String MODID = "orespawn";
	/** display name of this mod */
	public static final String NAME ="Ore Spawn";
	/** Version number, in Major.Minor.Build format. The minor number is increased whenever a change 
	 * is made that has the potential to break compatibility with other mods that depend on this one. */
	public static final String VERSION = "1.0.0";
	
	/** All ore-spawn files discovered in the ore-spawn folder */
	public static final List<Path> oreSpawnConfigFiles = new LinkedList<>();

	/** Whether or not vanilla ore-gen has been disabled */
	public static boolean disableVanillaOreGen = false;
	/** Whether or not other mod's ore-gen has been disabled */
	public static boolean disableOtherOreGen = false;
	/** If true, convert all removed ore-spawning to an orespawn JSON of equivalent value */
	public static boolean autoGenerateOrespawnFiles = true;
	/** Ignores other mods telling this mod not to generate ore */
	public static boolean forceOreGen = false;
	/** location of ore-spawn files */
	public static Path oreSpawnFolder = null;
	
	@EventHandler
	public void preInit(FMLPreInitializationEvent event)
	{
		INSTANCE = this;
		// load config
		Configuration config = new Configuration(event.getSuggestedConfigurationFile());
		config.load();
		
//		enablePotionRecipes = config.getBoolean("enable_potions", "options", enablePotionRecipes, 
//				"If true, then some metals can be used to brew potions.");
		


		disableVanillaOreGen = config.getBoolean("disable_standard_ore_generation", "options", disableVanillaOreGen, 
				"If true, then ore generation will be handled exclusively by oregen .json files \n"
			+	"(vanilla ore generation will be disabled)");

		forceOreGen = config.getBoolean("force_ore_generation", "options", forceOreGen, 
				"If true, then ore generation cannot be disabled by other mods.");


		
		oreSpawnFolder = Paths.get(event.getSuggestedConfigurationFile().toPath().getParent().toString(),"orespawn");

		Path oreVanillaSpawnFile = Paths.get(oreSpawnFolder.toString(),"minecraft.json");
		if(disableVanillaOreGen && Files.exists(oreVanillaSpawnFile) == false){
			try {
				Files.createDirectories(oreVanillaSpawnFile.getParent());
				Files.write(oreVanillaSpawnFile, Arrays.asList(DataConstants.defaultVanillaOreSpawnJSON.split("\n")), Charset.forName("UTF-8"));
			} catch (IOException e) {
				FMLLog.severe(MODID+": Error: Failed to write file "+oreVanillaSpawnFile);
			}
		}

		config.save();
		



		if(event.getSide() == Side.CLIENT){
			clientPreInit(event);
		}
		if(event.getSide() == Side.SERVER){
			serverPreInit(event);
		}
	}
	
	@SideOnly(Side.CLIENT)
	private void clientPreInit(FMLPreInitializationEvent event){
		// client-only code

	}
	@SideOnly(Side.SERVER)
	private void serverPreInit(FMLPreInitializationEvent event){
		// server-only code
	}
	
	@EventHandler
	public void init(FMLInitializationEvent event)
	{


		try {
			Files.walk(oreSpawnFolder) // doing it the Java8 way
					.filter((Path p)->Files.isRegularFile(p))
					.filter((Path p)->p.getFileName().toString().toLowerCase(Locale.US).endsWith(".json"))
					.forEach(oreSpawnConfigFiles::add);
		} catch (IOException ioe) {
			FMLLog.log(Level.ERROR,ioe,"Error while searching for orespawn files");
		}

		
		if(disableVanillaOreGen){
			MinecraftForge.ORE_GEN_BUS.register(OreGenDisabler.getInstance());
		}

		if(event.getSide() == Side.CLIENT){
			clientInit(event);
		}
		if(event.getSide() == Side.SERVER){
			serverInit(event);
		}
	}
	

	@SideOnly(Side.CLIENT)
	private void clientInit(FMLInitializationEvent event){
		// client-only code
	}
	@SideOnly(Side.SERVER)
	private void serverInit(FMLInitializationEvent event){
		// server-only code
	}
	
	@EventHandler
	public void postInit(FMLPostInitializationEvent event)
	{
		// parse orespawn data
		for(Path oreSpawnFile : oreSpawnConfigFiles){
			try {
				cyano.basemetals.init.WorldGen.loadConfig(oreSpawnFile);

			} catch (IOException e) {
				FMLLog.log(Level.ERROR, e,MODID+": Error parsing ore-spawn config file "+oreSpawnFile);
			}
		}
		
		cyano.basemetals.init.WorldGen.init();




		
		if(event.getSide() == Side.CLIENT){
			clientPostInit(event);
		}
		if(event.getSide() == Side.SERVER){
			serverPostInit(event);
		}

	}
	

	@SideOnly(Side.CLIENT)
	private void clientPostInit(FMLPostInitializationEvent event){
		// client-only code
	}
	@SideOnly(Side.SERVER)
	private void serverPostInit(FMLPostInitializationEvent event){
		// server-only code
	}
	

	/**
	 * Parses a String in the format (stack-size)*(modid):(item/block name)#(metadata value). The 
	 * stacksize and metadata value parameters are optional.
	 * @param str A String describing an itemstack (e.g. "4*minecraft:dye#15" or "minecraft:bow")
	 * @param allowWildcard If true, then item strings that do not specify a metadata value will use 
	 * the OreDictionary wildcard value. If false, then the default meta value is 0 instead.
	 * @return An ItemStack representing the item, or null if the item is not found
	 */
	public static ItemStack parseStringAsItemStack(String str, boolean allowWildcard){
		str = str.trim();
		int count = 1;
		int meta;
		if(allowWildcard){
			meta = OreDictionary.WILDCARD_VALUE;
		} else {
			meta = 0;
		}
		int nameStart = 0;
		int nameEnd = str.length();
		if(str.contains("*")){
			count = Integer.parseInt(str.substring(0,str.indexOf("*")).trim());
			nameStart = str.indexOf("*")+1;
		}
		if(str.contains("#")){
			meta = Integer.parseInt(str.substring(str.indexOf("#")+1,str.length()).trim());
			nameEnd = str.indexOf("#");
		}
		String id = str.substring(nameStart,nameEnd).trim();
		if(Block.getBlockFromName(id) != null){
			// is a block
			return new ItemStack(Block.getBlockFromName(id),count,meta);
		} else if(Item.getByNameOrId(id) != null){
			// is an item
			return new ItemStack(Item.getByNameOrId(id),count,meta);
		} else {
			// item not found
			FMLLog.severe("Failed to find item or block for ID '"+id+"'");
			return null;
		}
	}




}
