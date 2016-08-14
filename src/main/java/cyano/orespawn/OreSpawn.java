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
import java.util.*;


/**
 * This is the entry point for this mod. If you are writing your own mod that 
 * uses this mod,You only need to create the appropriate JSON files in the
 * config/orespawn folder (no .jar dependency required).
 * @author DrCyano
 *
 */
@Mod(
		modid = OreSpawn.MODID,
		name= OreSpawn.NAME,
		version = OreSpawn.VERSION
//		dependencies = "required-after:Forge"
//		acceptedMinecraftVersions = "1.9.4")
//		updateJSON = "https://raw.githubusercontent.com/cyanobacterium/OreSpawn/master/update.json")
)
public class OreSpawn
{
	
	public static OreSpawn INSTANCE = null;
	/** ID of this mod */
	public static final String MODID = "orespawn";
	/** display name of this mod */
	public static final String NAME ="Ore Spawn";
	/** Version number, in Major.Minor.Build format. The minor number is increased whenever a change 
	 * is made that has the potential to break compatibility with other mods that depend on this one. */
	public static final String VERSION = "1.1.0";

	/** All ore-spawn files discovered in the ore-spawn folder */
	public static final List<Path> oreSpawnConfigFiles = new LinkedList<>();

	/** User-specified stones for spawning ores (in case they want to spawn in gravel or something) */
	public static final List<String> additionalStoneBlocks = new ArrayList<>();

	/** Whether or not vanilla ore-gen has been disabled */
	public static boolean disableVanillaOreGen = false;
	/** Ignores other mods telling this mod not to generate ore */
	public static boolean forceOreGen = false;
	/** Ignore non-existant blocks instead of erroring */
	public static boolean ignoreNonExistant = true;
	/** location of ore-spawn files */
	public static Path oreSpawnFolder = null;
	
	@EventHandler
	public void preInit(FMLPreInitializationEvent event)
	{
		INSTANCE = this;
		// load config
		Configuration config = new Configuration(event.getSuggestedConfigurationFile());
		config.load();



		disableVanillaOreGen = config.getBoolean("disable_standard_ore_generation", "options", disableVanillaOreGen,
				"If true, then default Minecraft ore generation will be handled exclusively by orespawn .json files \n"
						+	"(vanilla ore generation will be disabled)");

		forceOreGen = config.getBoolean("force_ore_generation", "options", forceOreGen,
				"If true, then ore generation cannot be disabled by other mods.");

		ignoreNonExistant = config.getBoolean("ignore_missing_blocks", "options", ignoreNonExistant,
				"If true, then references to non-existant blocks in the .json files will be ingored without causing an error.");


		String[] blocks = config.getString("nonstandard_spawn_blocks", "options", "",
				"A semi-colon (;) delimited list of block IDs of non-stone blocks that you want to also have ores spawn \n" +
						"in them (e.g. \"minecraft:gravel;minecraft:sandstone;minecraft:stained_hardened_clay\"").split(";");
		for(String s : blocks){
			if(!s.trim().isEmpty()){
				additionalStoneBlocks.add(s);
			}
		}

		oreSpawnFolder = Paths.get(event.getSuggestedConfigurationFile().toPath().getParent().toString(),"orespawn");
		if(!Files.isDirectory(oreSpawnFolder)){
			try{
				Files.createDirectories(oreSpawnFolder);
			} catch (IOException e) {
				FMLLog.severe(MODID+": Error: Failed to make folder "+oreSpawnFolder);
			}
		}

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

		try {
			Files.walk(oreSpawnFolder) // doing it the Java8 way
					.filter((Path p)->Files.isRegularFile(p))
					.filter((Path p)->p.getFileName().toString().toLowerCase(Locale.US).endsWith(".json"))
					.forEach(oreSpawnConfigFiles::add);
		} catch (IOException ioe) {
			FMLLog.log(Level.ERROR,ioe,"Error while searching for orespawn files");
		}

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
