package com.mcmoddev.orespawn.data;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.IReplacementEntry;
import com.mcmoddev.orespawn.impl.os3.ReplacementEntry;
import com.mcmoddev.orespawn.util.StateUtil;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.IForgeRegistryModifiable;
import net.minecraftforge.registries.RegistryBuilder;

public class ReplacementsRegistry {

	private static final String ORE_SPAWN_VERSION = "OreSpawn Version";
	private static final IForgeRegistryModifiable<IReplacementEntry> registry = (IForgeRegistryModifiable<IReplacementEntry>) new RegistryBuilder<IReplacementEntry>()
			.setName(new ResourceLocation("orespawn", "replacements_registry")).allowModification()
			.setType(IReplacementEntry.class).setMaxID(65535) // 16 bits should be enough...
			.create();

	public ReplacementsRegistry() {
		//
	}

	public Map<ResourceLocation, IReplacementEntry> getReplacements() {
		return ImmutableMap.copyOf(registry.getEntries());
	}

	/**
	 *
	 * @param dimension
	 * @return
	 * @deprecated
	 */
	@Deprecated
	public List<IBlockState> getDimensionDefault(final int dimension) {
		String[] names = { "minecraft:netherrack", "minecraft:stone", "minecraft:end_stone" };
		List<IBlockState> mineralogyOres = OreDictionary.getOres("cobblestone").stream()
				.filter(iS -> iS.getItem().getRegistryName().getNamespace().equals("mineralogy"))
				.map(iS -> Block.getBlockFromItem(iS.getItem()).getStateFromMeta(iS.getMetadata()))
				.collect(Collectors.toList());
		List<IBlockState> baseRv = new ArrayList<>();
		baseRv.addAll(mineralogyOres);

		if (dimension < -1 || dimension > 1 || dimension == 0) {
			for (ItemStack iS : OreDictionary.getOres("stone")) {
				baseRv.add(Block.getBlockFromItem(iS.getItem()).getStateFromMeta(iS.getMetadata()));
			}

			return baseRv;
		}

		baseRv.addAll(Arrays.asList(ForgeRegistries.BLOCKS
				.getValue(new ResourceLocation(names[dimension + 1])).getDefaultState()));
		return baseRv;
	}

	public IReplacementEntry getReplacement(final String name) {
		ResourceLocation act = new ResourceLocation(
				name.contains(":") ? name : String.format("orespawn:%s", name));
		if (registry.containsKey(act)) {
			return registry.getValue(act);
		} else {
			return registry.getValue(new ResourceLocation("orespawn:default"));
		}
	}

	public void addBlock(final String name, final String blockName, final String blockState) {
		IBlockState b = StateUtil.deserializeState(
				ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockName)), blockState);
		addBlock(name, b);
	}

	public Map<String, List<IBlockState>> getBlocks() {
		Map<String, List<IBlockState>> tempMap = new TreeMap<>();
		registry.getEntries().stream()
				.forEach(e -> tempMap.put(e.getKey().toString(), e.getValue().getEntries()));

		return Collections.unmodifiableMap(tempMap);
	}

	public void addReplacement(final IReplacementEntry replacement) {
		registry.register(replacement);
	}

	public void addBlock(final String name, final IBlockState state) {
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

	@SuppressWarnings("deprecation")
	public void loadFile(final Path file) {
		JsonParser parser = new JsonParser();
		JsonObject elements;
		String rawJson;

		try {
			rawJson = FileUtils.readFileToString(file.toFile(), Charset.defaultCharset());
		} catch (IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e,
					"Failed reading config " + file.getFileName());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
			return;
		}

		elements = parser.parse(rawJson).getAsJsonObject();
		elements.entrySet().stream().forEach(elem -> {
			String entName = elem.getKey();
			JsonArray entries = elem.getValue().getAsJsonArray();
			List<IBlockState> blocks = new LinkedList<>();
			for (JsonElement e : entries) {
				JsonObject asObj = e.getAsJsonObject();
				String blockName = asObj.get(Constants.ConfigNames.NAME).getAsString()
						.toLowerCase();

				// is this an OreDictionary entry ?
				if (blockName.startsWith("ore:")) {
					// yes, it is
					String oreDictName = blockName.split(":")[1];
					OreDictionary.getOres(oreDictName).forEach(iS -> {
						if (iS.getMetadata() != 0) {
							blocks.add(Block.getBlockFromItem(iS.getItem())
									.getStateFromMeta(iS.getMetadata()));
						} else {
							blocks.add(Block.getBlockFromItem(iS.getItem()).getDefaultState());
						}
					});
				} else {
					String state = null;
					ResourceLocation blockRL = new ResourceLocation(blockName);
					Block theBlock = ForgeRegistries.BLOCKS.getValue(blockRL);
					if (asObj.has(Constants.ConfigNames.METADATA)) {
						// has metadata
						int meta = asObj.get(Constants.ConfigNames.METADATA).getAsInt();
						blocks.add(theBlock.getStateFromMeta(meta));
					} else if (asObj.has(Constants.ConfigNames.STATE)) {
						// has a state
						state = asObj.get(Constants.ConfigNames.STATE).getAsString();
						blocks.add(StateUtil.deserializeState(theBlock, state));
					} else {
						// use the default state
						blocks.add(theBlock.getDefaultState());
					}
				}
			}

			IReplacementEntry replacer = new ReplacementEntry("orespawn:" + entName, blocks);
			registry.register(replacer);
		});
	}

	public void saveFile(final String modName) {
		JsonObject outs = new JsonObject();

		registry.getEntries().stream().filter(ent -> ent.getKey().getNamespace().equals(modName))
				.forEach(ent -> {
					JsonArray entry = new JsonArray();
					IReplacementEntry workVal = ent.getValue();
					workVal.getEntries().stream().forEach(bs -> {
						JsonObject block = new JsonObject();
						block.addProperty(Constants.ConfigNames.BLOCK,
								bs.getBlock().getRegistryName().toString());
						if (!bs.toString().matches("\\[normal\\]")) {
							block.addProperty(Constants.ConfigNames.STATE,
									bs.toString().replaceAll("[\\[\\]]", ""));
						}
						entry.add(block);
					});
					outs.add(ent.getKey().toString(), entry);
				});
		Path p = Paths.get("config", "orespawn3", "sysconfig",
				String.format("replacements-%s.json", modName));
		try (BufferedWriter w = Files.newBufferedWriter(p)) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			String ov = gson.toJson(outs);
			w.write(ov);
		} catch (IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e, String
					.format("Failed writing replacements file  %s", p.toAbsolutePath().toString()));
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
		}
	}

	public boolean has(final ResourceLocation resourceLocation) {
		return registry.containsKey(resourceLocation);
	}
}
