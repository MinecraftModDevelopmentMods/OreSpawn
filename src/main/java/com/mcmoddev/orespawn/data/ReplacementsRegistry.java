package com.mcmoddev.orespawn.data;

import com.mcmoddev.orespawn.util.StateUtil;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.os3.IReplacementEntry;
import com.mcmoddev.orespawn.impl.os3.ReplacementEntry;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.state.pattern.BlockMatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryModifiable;
import net.minecraftforge.registries.RegistryBuilder;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ReplacementsRegistry {
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
			IBlockState ents[] = new IBlockState[oldList.size()];
			newRE = new ReplacementEntry(name, oldList.toArray(ents));
			registry.remove(regName);
			newRE.setRegistryName(regName);
			registry.register(newRE);
			return;
		}
		
		IReplacementEntry r = new ReplacementEntry(name, state);		
		registry.register(r);
	}
	
	public static void loadFile(Path file) {
		
	}
}
