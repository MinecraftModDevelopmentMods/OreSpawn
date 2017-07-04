package com.mcmoddev.orespawn.json.os3;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import com.google.common.collect.ImmutableSet;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.impl.location.BiomeLocationComposition;
import com.mcmoddev.orespawn.impl.location.BiomeLocationDictionary;
import com.mcmoddev.orespawn.impl.location.BiomeLocationList;
import com.mcmoddev.orespawn.impl.location.BiomeLocationSingle;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class OS3TypeAdapter extends TypeAdapter<BiomeLocation> {

	@Override
	public void write(JsonWriter out, BiomeLocation value) throws IOException {
		if( (value instanceof BiomeLocationSingle) || (value instanceof BiomeLocationDictionary) ) {
			outString(out, value);
		} else if( value instanceof BiomeLocationList ) {
			serializeList(out, value);
		} else if( value instanceof BiomeLocationComposition ) {
			serializeComposition(out, value);
		} else {
			// error ?
		}
	}

	private void serializeComposition(JsonWriter out, BiomeLocation value) throws IOException {
		ImmutableSet<BiomeLocation> inclusions = ((BiomeLocationComposition) value).getInclusions();
		ImmutableSet<BiomeLocation> exclusions = ((BiomeLocationComposition) value).getExclusions();		
		out.beginObject();
		out.name("inclusions");
		serializeList(out, new BiomeLocationList(inclusions));
		out.name("exclusions");
		serializeList(out, new BiomeLocationList(exclusions));
		out.endObject();
	}

	private void serializeList(JsonWriter out, BiomeLocation value) throws IOException {
		out.beginArray();
		ImmutableSet<BiomeLocation> values = ((BiomeLocationList)value).getLocations();
		
		values.forEach( bl -> {
			try {
				if( (bl instanceof BiomeLocationSingle) || (bl instanceof BiomeLocationDictionary) ) {
					outString(out, bl);
				} else if( value instanceof BiomeLocationComposition ) {
					serializeComposition(out,bl);
				} else {
					// error ?
				}			
			} catch(IOException e) {
				OreSpawn.LOGGER.error("Error serializing a BiomeLocationList", e);
			}
		});
		out.endArray();
	}

	private void outString(JsonWriter out, BiomeLocation value) throws IOException {
		String outV = null;
		if( value instanceof BiomeLocationDictionary ) {
			outV = ((BiomeLocationDictionary)value).getType().toString();
		} else if( value instanceof BiomeLocationSingle ) {
			outV = ForgeRegistries.BIOMES.getKey(((BiomeLocationSingle)value).getBiome()).toString();
		} else {
			// error ?
			outV = "";
		}
		out.value(outV);
	}

	@Override
	public BiomeLocation read(JsonReader in) throws IOException {
		JsonToken next = in.peek();

		// error checking
		if( next.equals(JsonToken.NULL) ) {
			in.nextNull();
			return null;
		}
		
		if( next.equals(JsonToken.BEGIN_OBJECT) ) {
			// we're looking at a BiomeLocationCombination
			return deserializeBiomeLocationComposition(in);
		} else if( next.equals(JsonToken.BEGIN_ARRAY) ) {
			// we're looking at a BiomeLocationList
			return deserializeBiomeLocationList(in);
		} else if( in.peek().equals(JsonToken.STRING) ) {
			// we should, technically, always see a BiomeLocationList
			// or a BiomeLocationCombination at this level, but to
			// stay away from certain issues, we'll allow for this
			// to be a BiomeLocationSingle or BiomeLocationDictionary
			return deserializeSingleEntry(in);
		} else {
			// should we throw an exception here ?
			return null;
		}
	}

	private BiomeLocation deserializeSingleEntry(JsonReader in) throws IOException {
		String tokenValue = in.nextString();
		if( tokenValue.contains(":") ) {
			// its a single
			return new BiomeLocationSingle(ForgeRegistries.BIOMES.getValue(new ResourceLocation(tokenValue)));
		} else {
			// assume its a dictionary...
			return new BiomeLocationDictionary( BiomeDictionary.Type.getType(tokenValue) );
		}
	}
	
	private BiomeLocation deserializeBiomeLocationList(JsonReader in) throws IOException {
		Set<BiomeLocation> myData = new TreeSet<>();
		
		in.beginArray();
		while( !in.peek().equals(JsonToken.END_ARRAY) ) {
			if( in.peek().equals(JsonToken.BEGIN_OBJECT) ) {
				myData.add(deserializeBiomeLocationComposition(in));
			} else if( in.peek().equals(JsonToken.STRING) ) {
				myData.add(deserializeSingleEntry(in));
			} else {
				// error time ?
				in.skipValue();
			}
		}
		in.endArray();
		return new BiomeLocationList(ImmutableSet.<BiomeLocation>copyOf(myData));
	}

	private BiomeLocation deserializeBiomeLocationComposition(JsonReader in) throws IOException {
		Set<BiomeLocation> includes = new TreeSet<>();
		Set<BiomeLocation> excludes = new TreeSet<>();
		
		in.beginObject();
		while( !in.peek().equals(JsonToken.END_OBJECT) ) {
			String name = in.nextName();
			if( "inclusions".equals(name) ) {
				if( in.peek().equals(JsonToken.BEGIN_ARRAY) ) {
					includes.add(deserializeBiomeLocationList(in));
				} else if( in.peek().equals(JsonToken.STRING) ) {
					includes.add(deserializeSingleEntry(in));
				} else {
					// error ?
					in.skipValue();
				}
			} else if( "exclusions".equals(name) ) {
				if( in.peek().equals(JsonToken.BEGIN_ARRAY) ) {
					excludes.add(deserializeBiomeLocationList(in));
				} else if( in.peek().equals(JsonToken.STRING) ) {
					excludes.add(deserializeSingleEntry(in));
				} else {
					// error ?
					in.skipValue();
				}				
			}
		}
		in.endObject();
		return new BiomeLocationComposition(ImmutableSet.<BiomeLocation>copyOf(includes),
				ImmutableSet.<BiomeLocation>copyOf(excludes));
	}
}

