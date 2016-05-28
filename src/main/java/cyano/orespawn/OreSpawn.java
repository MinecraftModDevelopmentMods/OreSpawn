package cyano.orespawn;

import cyano.orespawn.data.DataConstants;
import cyano.orespawn.events.OreGenDisabler;
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
				"If true, then default Minecraft ore generation will be handled exclusively by orespawn .json files \n"
						+	"(vanilla ore generation will be disabled)");

		disableOtherOreGen = config.getBoolean("disable_other_ore_generation", "options", disableOtherOreGen,
				"If true, then all mods will have their ore generation replaced by orespawn .json files. \n"
						+	"(set generate_orespawn_templates to true to automatically create the equivalent orespawn files)");

		autoGenerateOrespawnFiles = config.getBoolean("generate_orespawn_templates", "options", autoGenerateOrespawnFiles,
				"If true, then all mods will have their ore generation replaced by orespawn .json files. \n"
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
		// remove orespawning
		// TODO: remove other mod orespawns and generate JSON files

		// parse orespawn data
		for(Path oreSpawnFile : oreSpawnConfigFiles){
			try {
				cyano.orespawn.init.WorldGen.loadConfig(oreSpawnFile);

			} catch (IOException e) {
				FMLLog.log(Level.ERROR, e,MODID+": Error parsing ore-spawn config file "+oreSpawnFile);
			}
		}
		
		cyano.orespawn.init.WorldGen.init();




		
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
	





}
