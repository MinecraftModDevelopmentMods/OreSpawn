package com.mcmoddev.orespawn.data;

import com.mcmoddev.orespawn.util.StateUtil;
import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.os3.IReplacementEntry;
import com.mcmoddev.orespawn.impl.os3.ReplacementEntry;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.pattern.BlockMatcher;
import net.minecraft.crash.CrashReport;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryModifiable;
import net.minecraftforge.registries.RegistryBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

public class ReplacementsRegistry {
	private static final String ORE_SPAWN_VERSION = "OreSpawn Version";
	private static final IForgeRegistryModifiable<IReplacementEntry> registry = (IForgeRegistryModifiable<IReplacementEntry>) new RegistryBuilder<IReplacementEntry>()
			.setName(new ResourceLocation("orespawn", "replacements_registry"))
			.allowModification()
			.setType(IReplacementEntry.class)
			.setMaxID(65535) // 16 bits should be enough...
			.create();

	private ReplacementsRegistry() {
	}

	@Deprecated
	public static List<IBlockState> getDimensionDefault(int dimension) {
		String[] names = { "minecraft:netherrack", "minecraft:stone", "minecraft:end_stone" };
		List<IBlockState> mineralogyOres = 	OreDictionary.getOres("cobblestone").stream()
				.filter( iS -> iS.getItem().getRegistryName().getResourceDomain().equals("mineralogy"))
				.map( iS -> Block.getBlockFromItem(iS.getItem()).getStateFromMeta(iS.getMetadata()))
				.collect(Collectors.toList());
		List<IBlockState> baseRv = new ArrayList<>();
		baseRv.addAll(mineralogyOres);

		
		if (dimension < -1 || dimension > 1 || dimension == 0) {
			for (ItemStack iS : OreDictionary.getOres("stone")) {
				baseRv.add(Block.getBlockFromItem(iS.getItem()).getStateFromMeta(iS.getMetadata()));
			}
			
			return baseRv;
		} 
		
		baseRv.addAll(Arrays.asList(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(names[dimension + 1])).getDefaultState()));
		return baseRv;
	}

	public static IReplacementEntry getReplacement(String name) {
		if (registry.containsKey(new ResourceLocation(name))) {
			return registry.getValue(new ResourceLocation(name));
		} else {
			return registry.getValue(new ResourceLocation("default"));
		}
	}

	public static void addBlock(String name, String blockName, String blockState) {
		IBlockState b = StateUtil.deserializeState(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName)), blockState);
		addBlock(name, b);
	}

	public static Map<String, List<IBlockState>> getBlocks() {
		Map<String,List<IBlockState>> tempMap = new TreeMap<>();
		registry.getEntries().stream()
		.forEach(e -> tempMap.put(e.getKey().toString(), e.getValue().getEntries()));
		
		return Collections.unmodifiableMap(tempMap);
	}

	public static void addBlock(String name, IBlockState state) {
		ResourceLocation regName = new ResourceLocation(name);
		if (registry.containsKey(regName)) {
			IReplacementEntry old = registry.getValue(regName);
			IReplacementEntry newRE;
			List<IBlockState> oldList = old.getEntries();
			oldList.add(state);
			newRE = new ReplacementEntry(name, oldList);
			registry.remove(regName);
			newRE.setRegistryName(regName);
			registry.register(newRE);
			return;
		}
		
		IReplacementEntry r = new ReplacementEntry(name, state);		
		registry.register(r);
	}
	
	public static void loadFile(Path file) {
		JsonParser parser = new JsonParser();
		JsonObject elements;
		String rawJson;
		String modName = getModName(FilenameUtils.getBaseName(file.toString()));
		
		try {
			rawJson = FileUtils.readFileToString(file.toFile(), Charset.defaultCharset());
		} catch (IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e, "Failed reading config " + file.getFileName());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
			return;
		}
		
		elements = parser.parse(rawJson).getAsJsonObject();
		elements.entrySet().stream()
		.forEach( elem -> {
			String entName = elem.getKey();
			JsonArray entries = elem.getValue().getAsJsonArray();
			List<IBlockState> blocks = new LinkedList<>();
			for (JsonElement e : entries) {
				JsonObject asObj = e.getAsJsonObject();
				String blockName = asObj.get(Constants.ConfigNames.BLOCK_V2).getAsString();
				String state = null;
				if (asObj.has(Constants.ConfigNames.METADATA)) {
					state = String.format("variant=%s", asObj.get(Constants.ConfigNames.METADATA).getAsInt());
				} else if(asObj.has(Constants.ConfigNames.STATE)) {
					state = asObj.get(Constants.ConfigNames.STATE).getAsString();
				}
				
				Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName));
				if (state != null) {
					blocks.add(StateUtil.deserializeState(block, state));
				} else {
					blocks.add(block.getDefaultState());
				}
			}
			IReplacementEntry replacer = new ReplacementEntry(entName, blocks);
			replacer.setRegistryName(new ResourceLocation(modName, entName));
			registry.register(replacer);
		});
	}

	public void saveFile(String modName) {
		// TODO: write this
	}
	
	private static String getModName(String baseName) {
		if(baseName.indexOf("-") > 0) {
			String[] bits = baseName.split("-");
			return bits[1];
		}
		
		return baseName;
	}
}
