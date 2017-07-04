package com.mcmoddev.orespawn.json.os3.readers;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.os3.BiomeBuilder;
import com.mcmoddev.orespawn.api.os3.BuilderLogic;
import com.mcmoddev.orespawn.api.os3.DimensionBuilder;
import com.mcmoddev.orespawn.api.os3.FeatureBuilder;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.impl.os3.DimensionBuilderImpl;
import com.mcmoddev.orespawn.impl.os3.SpawnBuilderImpl;
import com.mcmoddev.orespawn.json.os3.IOS3Reader;
import com.mcmoddev.orespawn.json.os3.OS3TypeAdapter;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class OS3V11Reader implements IOS3Reader {

	@Override
	public void parseJson(JsonObject entries, String fileName) {
		JsonArray elements = entries.get("dimensions").getAsJsonArray();
		
		BuilderLogic logic = OreSpawn.API.getLogic(FilenameUtils.getBaseName(fileName));
		List<DimensionBuilder> builders = new ArrayList<>();

		for (JsonElement element : elements ) {
			JsonObject object = element.getAsJsonObject();

			int dimension = object.has("dimension") ? object.get("dimension").getAsInt() : OreSpawn.API.dimensionWildcard();
			DimensionBuilder builder = logic.DimensionBuilder(dimension);
			List<SpawnBuilder> spawns = new ArrayList<>();
			
			JsonArray ores = object.get("ores").getAsJsonArray();

			for (JsonElement oresEntry : ores) {
				SpawnBuilder spawn = builder.SpawnBuilder(null);
				
				JsonObject ore = oresEntry.getAsJsonObject();

				OreBuilder oreB = spawn.OreBuilder();
				
				String oreName = ore.get("block").getAsString();
				
				if (ore.has("state")) {
					String stateString = ore.get("state").getAsString();
					oreB.setOre(oreName, stateString);
				} else {
					oreB.setOre(oreName);
				}

				FeatureBuilder feature = spawn.FeatureBuilder(ore.get("feature").getAsString());
				feature.setParameters(ore.get("parameters").getAsJsonObject());

				String replaceBase = ore.get("replace_block").getAsString();
				IBlockState blockRep;

				if( "default".equals(replaceBase) ) {
					blockRep = ReplacementsRegistry.getDimensionDefault(dimension);
				} else if( replaceBase == null ) {
					blockRep = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft:stone")).getDefaultState();
				} else {
					blockRep = ReplacementsRegistry.getBlock(replaceBase);
				}

				BiomeBuilder biomes = spawn.BiomeBuilder();

				if (ore.has("biomes")) {
					Gson gson = new GsonBuilder().registerTypeAdapter(BiomeLocation.class, OS3TypeAdapter.class).create();
					BiomeLocation b = gson.fromJson(ore.get("biomes"), BiomeLocation.class);
					biomes.setFromBiomeLocation(b);
				}
				
				List<IBlockState> repBlock = new ArrayList<>();
				repBlock.add(blockRep);
				spawn.create(biomes, feature, repBlock, oreB);
				spawns.add(spawn);
			}
			builder.create(spawns.toArray(new SpawnBuilderImpl[spawns.size()]));
			builders.add(builder);
		}
		
		logic.create(builders.toArray(new DimensionBuilderImpl[builders.size()]));

		OreSpawn.API.registerLogic(logic);
	}
}

