package com.mcmoddev.orespawn.impl.features;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.FeatureBase;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.os3.ISpawnEntry;
import com.mcmoddev.orespawn.data.Constants;
import com.mojang.realmsclient.util.Pair;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

public class VeinGenerator extends FeatureBase implements IFeature {

	private VeinGenerator(Random rand) {
		super(rand);
	}

	public VeinGenerator() {
		this(new Random());
	}

	@Override
	public void generate(World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider,
			ISpawnEntry spawnData, ChunkPos _pos) {
		ChunkPos pos = _pos;
		JsonObject params = spawnData.getFeature().getFeatureParameters();

		// First, load cached blocks for neighboring chunk ore spawns
		int chunkX = pos.x;
		int chunkZ = pos.z;

		runCache(chunkX, chunkZ, world, spawnData);
		mergeDefaults(params, getDefaultParameters());

		// now to ore spawn

		int blockX = chunkX * 16 + 8;
		int blockZ = chunkZ * 16 + 8;

		int minY = params.get(Constants.FormatBits.MIN_HEIGHT).getAsInt();
		int maxY = params.get(Constants.FormatBits.MAX_HEIGHT).getAsInt();
		int vari = params.get(Constants.FormatBits.VARIATION).getAsInt();
		int freq = params.get(Constants.FormatBits.FREQUENCY).getAsInt();
		int length = params.get(Constants.FormatBits.LENGTH).getAsInt();
		int startingFace = getFaceFromString(params.get(Constants.FormatBits.STARTINGFACE).getAsString());
		int nodeSize = params.get(Constants.FormatBits.NODE_SIZE).getAsInt();
		int triesMin   = params.get(Constants.FormatBits.ATTEMPTS_MIN).getAsInt();
		int triesMax   = params.get(Constants.FormatBits.ATTEMPTS_MAX).getAsInt();

		OreSpawn.LOGGER.fatal("parameters: %s", params);
		int tries;

		if (triesMax == triesMin) {
			tries = triesMax;
		} else {
			tries = random.nextInt(triesMax - triesMin) + triesMin;
		}

		// we have an offset into the chunk but actually need something more
		while (tries > 0) {
			if (this.random.nextInt(100) <= freq) {
				int x = blockX + random.nextInt(16);
				int y = random.nextInt(maxY - minY) + minY;
				int z = blockZ + random.nextInt(16);

				final int r;

				if (vari > 0) {
					r = random.nextInt(2 * vari) - vari;
				} else {
					r = 0;
				}

				spawnVein(length + r, nodeSize, startingFace, new BlockPos(x, y, z), spawnData, world);
			}

			tries--;
		}
	}
	
	private int getFaceFromString(String direction) {
		String work = direction.toLowerCase();
		switch(work) {
		case "north":
			return 0;
		case "south":
			return 5;
		case "east":
			return 2;
		case "west":
			return 3;
		case "down":
			return 1;
		case "up":
			return 4;
		case "random":
			return this.random.nextInt(5);
		case "vertical":
			return this.random.nextBoolean()?4:1;
		case "horizontal":
			return this.random.nextBoolean()?(this.random.nextBoolean()?0:5):(this.random.nextBoolean()?2:3);
		default:
			OreSpawn.LOGGER.error("Invalid value %s found in parameters for vein spawn, returning \"north\"", direction);
			return 0;
		}
	}

	private void spawnVein(int veinLength, int nodeSize, int startingFace, BlockPos blockPos, ISpawnEntry spawnData, World world) {
		int face = startingFace;
		int square = this.random.nextInt(8);
		BlockPos workingPos = new BlockPos(blockPos);
		
		if(!spawnData.getMatcher().test(world.getBlockState(blockPos))) {
			return;
		}
		
		// build vein
		List<Pair<Integer,Integer>> points = Lists.newLinkedList();
		points.add(Pair.of(face, square));
		for( int j = 0; j < veinLength; j++ ) {
			Map<Integer,Integer> pos = Maps.newHashMap();
			int[] w = getWeighted(face, square);
			for( int k = 0; k < w.length; k++ ) {
				pos.put(w[k], k);
			}
			
			int[] t = Arrays.stream(w).distinct().sorted().toArray();
			int[] fpos = Arrays.stream(Arrays.copyOfRange(t, t.length-5, t.length)).map( it -> pos.get(it)).toArray();
			int temp = this.random.nextInt(5);
			face = fpos[temp]%6;
			square = fpos[temp]%12;
			points.add(Pair.of(face,square));
		}

		spawnOre(world, spawnData, blockPos, nodeSize);
		for(Pair<Integer,Integer> point : points) {
			spawnOre(world, spawnData, workingPos, nodeSize);
			int[] nextMunge = getDirectionValues(point.first().intValue(), point.second().intValue());
			workingPos = workingPos.add(nextMunge[0], nextMunge[1], nextMunge[2]);
		}
	}

	private void spawnOre(World world, ISpawnEntry spawnData, BlockPos pos, int quantity) {
		int count = quantity;
		int lutType = (quantity < 8) ? offsetIndexRef_small.length : offsetIndexRef.length;
		int[] lut = (quantity < 8) ? offsetIndexRef_small : offsetIndexRef;
		Vec3i[] offs = new Vec3i[lutType];

		System.arraycopy((quantity < 8) ? offsets_small : offsets, 0, offs, 0, lutType);

		if (quantity < 27) {
			int[] scrambledLUT = new int[lutType];
			System.arraycopy(lut, 0, scrambledLUT, 0, scrambledLUT.length);
			scramble(scrambledLUT, this.random);

			while (count > 0) {
				IBlockState oreBlock = spawnData.getBlocks().getRandomBlock(random);
				BlockPos target = pos.add(offs[scrambledLUT[--count]]);
				spawn(oreBlock, world, target,
				    world.provider.getDimension(), true, spawnData);
			}

			return;
		}

		doSpawnFill(this.random.nextBoolean(), count, world, spawnData, pos);
	}

	private void doSpawnFill(boolean nextBoolean, int quantity, World world, ISpawnEntry spawnData, BlockPos pos) {
		int count = quantity;
		double radius = Math.pow(quantity, 1.0/3.0) * (3.0 / 4.0 / Math.PI) + 2;
		int rSqr = (int)(radius * radius);
		if( nextBoolean ) {
			spawnMungeNE( world, pos, rSqr, radius, spawnData, count );
		} else {
			spawnMungeSW( world, pos, rSqr, radius, spawnData, count );
		}
	}

	private int[] getDirectionValues(int face, int square) {
		int x = 0;
		int y = 0;
		int z = 0;
		switch(face) {
		case 0:
			z = -1;
			break;
		case 2:
			x = 1;
			break;
		case 3:
			x = -1;
			break;
		case 5:
			z = 1;
			break;
		}
		if (face == 0 || face == 5) {
			y = square < 3?1:(square>5?-1:0);
			x = (square==0||square%3==0)?-1:(((square+1)%3==0)?1:0);
		} else if (face == 2 || face == 3) {
			y = square < 3?1:square>5?-1:0;
			z = (square==0||square%3==0)?-1:(((square+1)%3==0)?1:0);
		} else if (face == 1 || face == 4) {
			z = square < 3?-1:(square>5?1:0);
			x = (square==0||square%3==0)?-1:(((square+1)%3==0)?1:0);
		}
		
		if (square < 3 || face == 4) y = 1;
		else if (square <= 5) y = 0;
		else if (square <= 8 || face == 1) y = -1;
		
		return new int[] { x, y, z };
	}
	/*
	 * To keep things heading the correct direction, we weight the faces and the "points" of the face
	 * so that we can choose a semi-intelligent direction to continue in.
	 */
	private int[] faceWeights = new int[] { /* self */ 2, /* other */ 1 };
	private int[] pointWeights = Arrays.stream(faceWeights).map(a -> a*2 ).toArray();


	private int[] getWeighted(int face, int square) {
		int[] rv = new int[54];

		for( int f = 0; f < 6; f++ ) {
			for( int s = 0; s < 12; s++ ) {
				if(isNeighborFace(face, f)) {
					rv[f+s] += faceWeights[1];
				} else if(face == f) {
					rv[f+s] += faceWeights[0];
				} else {
					rv[f+s] = 0;
				}

				rv[f+s] += pointWeights[s == square?0:1];
				if( s != 4 && s != 7) {
					int matchFace = 0;
					int matchSquare = 0;
					if( s%3 == 0 ) {
						// left side
						matchFace = getMatchingFace(f, 0);
						matchSquare = getMatchingSquare(s, f, 0);
					} else if( s == 2 || s == 5 || s == 8 || s == 11 ) {
						// right side
						matchFace = getMatchingFace(f, 1);						
						matchSquare = getMatchingSquare(s, f, 1);
					}
					int addr = matchFace + matchSquare;
					rv[addr] += faceWeights[1] + pointWeights[1];

					if( s < 3 || s > 8 ) {
						if( s < 3 ) {
							// top row
							matchFace = getMatchingFace(f, 2);
							matchSquare = getMatchingSquare(s, f, 2);
						} else if( s > 8 ) {
							//bottom row
							matchFace = getMatchingFace(f, 3);
							matchSquare = getMatchingSquare(s, f, 3);
						}
						rv[matchFace+matchSquare] += faceWeights[1] + pointWeights[1];
					}
				}
			}
		}

		return rv;
	}

	/*
	 * 0  1  2
	 * 3  4  5
	 * 6  7  8
	 * 9 10 11
	 * --------
	 * 0  1  2
	 * 3  4  5
	 * 6  7  8
	 * 9 10 11
	 * --------
	 * 0  1  2
	 * 3  4  5
	 * 6  7  8
	 * 9 10 11
	 */
	private int getMatchingSquare(int s, int face, int direction) {
		switch(direction) {
		case 0: /* LEFT */
			return (s%3 == 0)? s +2 : s - 1;
		case 1: /* RIGHT */
			return ( s == 2 || s == 5 || s == 8 || s == 11 )? s - 2: s + 1;
		case 2: /* UP */
			return (s<3) ? s + 9 : s - 3;
		case 3: /* DOWN */
			return (s>8) ? s - 9 : s + 3;
		}
		return s;
	}

	private int[][] faceMap = new int[][] { { 3, 2, 1, 4 }, { 3, 2, 5, 0 }, { 1, 4, 5, 0 },
		                                    { 4, 1, 5, 0 }, { 2, 3, 5, 0 }, { 3, 2, 4, 1 } };

	private int getMatchingFace(int f, int i) {		                             
		return faceMap[f][i];
	}

	private boolean isNeighborFace(int face, int f) {
		return Arrays.stream(faceMap[face]).anyMatch(a -> a == f);
	}

	@Override
	public JsonObject getDefaultParameters() {
		JsonObject defParams = new JsonObject();
		defParams.addProperty(Constants.FormatBits.MIN_HEIGHT, 0);
		defParams.addProperty(Constants.FormatBits.MAX_HEIGHT, 256);
		defParams.addProperty(Constants.FormatBits.VARIATION, 16);
		defParams.addProperty(Constants.FormatBits.FREQUENCY, 50);
		defParams.addProperty(Constants.FormatBits.ATTEMPTS_MAX, 8);
		defParams.addProperty(Constants.FormatBits.ATTEMPTS_MIN, 4);
		defParams.addProperty(Constants.FormatBits.LENGTH, 16);
		defParams.addProperty(Constants.FormatBits.STARTINGFACE, "north");
		defParams.addProperty(Constants.FormatBits.NODE_SIZE, 3);
		return defParams;
	}


	@Override
	public void setRandom(Random rand) {
		this.random = rand;
	}
}
