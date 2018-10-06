package com.mcmoddev.orespawn.impl.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.FeatureBase;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.os3.ISpawnEntry;
import com.mcmoddev.orespawn.data.Constants;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.WeightedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

public class VeinGenerator extends FeatureBase implements IFeature {

	private VeinGenerator(final Random rand) {
		super(rand);
	}

	public VeinGenerator() {
		this(new Random());
	}

	@Override
	public void generate(final World world, final IChunkGenerator chunkGenerator,
			final IChunkProvider chunkProvider, final ISpawnEntry spawnData, final ChunkPos _pos) {
		final ChunkPos pos = _pos;
		final JsonObject params = spawnData.getFeature().getFeatureParameters();

		// First, load cached blocks for neighboring chunk ore spawns
		final int chunkX = pos.x;
		final int chunkZ = pos.z;

		runCache(chunkX, chunkZ, world, spawnData);
		mergeDefaults(params, getDefaultParameters());

		// now to ore spawn

		final int blockX = chunkX * 16 + 8;
		final int blockZ = chunkZ * 16 + 8;

		final int minY = params.get(Constants.FormatBits.MIN_HEIGHT).getAsInt();
		final int maxY = params.get(Constants.FormatBits.MAX_HEIGHT).getAsInt();
		final int vari = params.get(Constants.FormatBits.VARIATION).getAsInt();
		final int freq = params.get(Constants.FormatBits.FREQUENCY).getAsInt();
		final int length = params.get(Constants.FormatBits.LENGTH).getAsInt();
		final EnumFacing startingFace = getFaceFromString(
				params.get(Constants.FormatBits.STARTINGFACE).getAsString());
		final int nodeSize = params.get(Constants.FormatBits.NODE_SIZE).getAsInt();
		final int triesMin = params.get(Constants.FormatBits.ATTEMPTS_MIN).getAsInt();
		final int triesMax = params.get(Constants.FormatBits.ATTEMPTS_MAX).getAsInt();

		int tries;

		if (triesMax == triesMin) {
			tries = triesMax;
		} else {
			tries = random.nextInt(triesMax - triesMin) + triesMin;
		}

		// we have an offset into the chunk but actually need something more
		while (tries > 0) {
			if (this.random.nextInt(100) <= freq) {
				final int x = blockX + random.nextInt(16);
				final int y = random.nextInt(maxY - minY) + minY;
				final int z = blockZ + random.nextInt(16);

				final int r;

				if (vari > 0) {
					r = random.nextInt(2 * vari) - vari;
				} else {
					r = 0;
				}

				spawnVein(length + r, nodeSize, startingFace, new BlockPos(x, y, z), spawnData,
						world);
			}

			tries--;
		}
	}

	private EnumFacing getFaceFromString(final String direction) {
		final String work = direction.toLowerCase();
		switch (work) {
			case "north":
				return EnumFacing.NORTH;
			case "south":
				return EnumFacing.SOUTH;
			case "east":
				return EnumFacing.EAST;
			case "west":
				return EnumFacing.WEST;
			case "down":
				return EnumFacing.DOWN;
			case "up":
				return EnumFacing.UP;
			case "random":
				return EnumFacing.VALUES[this.random.nextInt(EnumFacing.VALUES.length)];
			case "vertical":
				return this.random.nextBoolean() ? EnumFacing.UP : EnumFacing.DOWN;
			case "horizontal":
				return EnumFacing.HORIZONTALS[this.random.nextInt(EnumFacing.HORIZONTALS.length)];
			default:
				OreSpawn.LOGGER.error(
						"Invalid value %s found in parameters for vein spawn, returning \"north\"",
						direction);
				return EnumFacing.NORTH;
		}
	}

	private enum EnumSquare {
		TOP_EDGE(0), LEFT_EDGE(1), BOTTOM_EDGE(2), RIGHT_EDGE(3), LEFT_TOP(4), LEFT_BOTTOM(5),
		RIGHT_TOP(6), RIGHT_BOTTOM(7), FACE(8);

		private int index;

		private EnumSquare(int index) {
			this.index = index;
		}

		final public int getIndex() {
			return this.index;
		}
	}

	private void spawnVein(final int veinLength, final int nodeSize, final EnumFacing startingFace,
			final BlockPos blockPos, final ISpawnEntry spawnData, final World world) {
		EnumFacing face = startingFace;
		EnumSquare square = EnumSquare.values()[this.random.nextInt(EnumSquare.values().length)];
		BlockPos workingPos = new BlockPos(blockPos);

		if (!spawnData.getMatcher().test(world.getBlockState(blockPos))) {
			return;
		}

		// build vein
		final List<BlockPos> points = Lists.newLinkedList();
		for (; points.size() < veinLength;) {
			points.add(workingPos);
			List<EnumFacing> nextFaces = getNextFaceSet(square, face);

			if (!nextFaces.isEmpty()) {
				BlockPos temp = workingPos;
				for (EnumFacing f : nextFaces) {
					temp = temp.offset(f, 1);
				}
				nextFaces.clear();
				points.add(temp);
				workingPos = temp;
			}

			face = getNextStartingFace(square, face);
			square = getNextSquare();
		}

		spawnOre(world, spawnData, blockPos, nodeSize);
		for (final BlockPos pos : points) {
			spawnOre(world, spawnData, pos, nodeSize);
		}
	}

	private class SquareWeight extends WeightedRandom.Item {

		public EnumSquare item;

		public SquareWeight(EnumSquare item, int weight) {
			super(weight);
			this.item = item;
		}
	}

	private EnumSquare getNextSquare() {
		float[] weights = new float[] {
				0.12f, 0.12f, 0.12f, 0.12f, 0.0475f, 0.0475f, 0.0475f, 0.0475f, 0.33f
		};
		List<SquareWeight> items = new ArrayList<>();

		for (EnumSquare sq : EnumSquare.values()) {
			items.add(new SquareWeight(sq, (int) (weights[sq.getIndex()] * 10000)));
		}

		return ((SquareWeight) WeightedRandom.getRandomItem(this.random, items)).item;
	}

	private static final EnumFacing[][][] congruentSquares = new EnumFacing[][][] {
			// Index 0 - DOWN
			new EnumFacing[][] {
					new EnumFacing[] {
							EnumFacing.SOUTH, null
					},     // TOP_EDGE
					new EnumFacing[] {
							EnumFacing.WEST, null
					},     // LEFT_EDGE
					new EnumFacing[] {
							EnumFacing.NORTH, null
					},     // BOTTOM_EDGE
					new EnumFacing[] {
							EnumFacing.EAST, null
					},     // RIGHT_EDGE
					new EnumFacing[] {
							EnumFacing.SOUTH, EnumFacing.WEST
					},     // LEFT_TOP
					new EnumFacing[] {
							EnumFacing.NORTH, EnumFacing.WEST
					},     // LEFT_BOTTOM
					new EnumFacing[] {
							EnumFacing.SOUTH, EnumFacing.EAST
					},     // RIGHT_BOTTOM
					new EnumFacing[] {
							EnumFacing.SOUTH, EnumFacing.EAST
					}      // RIGHT_TOP
			}, new EnumFacing[][] {
					// Index 1 - UP
					new EnumFacing[] {
							EnumFacing.SOUTH, null
					},     // TOP_EDGE
					new EnumFacing[] {
							EnumFacing.WEST, null
					},     // LEFT_EDGE
					new EnumFacing[] {
							EnumFacing.NORTH, null
					},     // BOTTOM_EDGE
					new EnumFacing[] {
							EnumFacing.EAST, null
					},     // RIGHT_EDGE
					new EnumFacing[] {
							EnumFacing.SOUTH, EnumFacing.WEST
					},     // LEFT_TOP
					new EnumFacing[] {
							EnumFacing.NORTH, EnumFacing.WEST
					},     // LEFT_BOTTOM
					new EnumFacing[] {
							EnumFacing.SOUTH, EnumFacing.EAST
					},     // RIGHT_BOTTOM
					new EnumFacing[] {
							EnumFacing.SOUTH, EnumFacing.EAST
					}      // RIGHT_TOP
			}, new EnumFacing[][] {
					// Index 2 - NORTH
					new EnumFacing[] {
							EnumFacing.UP, null
					},     // TOP_EDGE
					new EnumFacing[] {
							EnumFacing.WEST, null
					},     // LEFT_EDGE
					new EnumFacing[] {
							EnumFacing.DOWN, null
					},     // BOTTOM_EDGE
					new EnumFacing[] {
							EnumFacing.EAST, null
					},     // RIGHT_EDGE
					new EnumFacing[] {
							EnumFacing.UP, EnumFacing.WEST
					},     // LEFT_TOP
					new EnumFacing[] {
							EnumFacing.DOWN, EnumFacing.WEST
					},     // LEFT_BOTTOM
					new EnumFacing[] {
							EnumFacing.DOWN, EnumFacing.EAST
					},     // RIGHT_BOTTOM
					new EnumFacing[] {
							EnumFacing.UP, EnumFacing.EAST
					}      // RIGHT_TOP
			}, new EnumFacing[][] {
					// Index 3 - SOUTH
					new EnumFacing[] {
							EnumFacing.UP, null
					},     // TOP_EDGE
					new EnumFacing[] {
							EnumFacing.EAST, null
					},     // LEFT_EDGE
					new EnumFacing[] {
							EnumFacing.DOWN, null
					},     // BOTTOM_EDGE
					new EnumFacing[] {
							EnumFacing.WEST, null
					},     // RIGHT_EDGE
					new EnumFacing[] {
							EnumFacing.UP, EnumFacing.EAST
					},     // LEFT_TOP
					new EnumFacing[] {
							EnumFacing.DOWN, EnumFacing.EAST
					},     // LEFT_BOTTOM
					new EnumFacing[] {
							EnumFacing.DOWN, EnumFacing.WEST
					},     // RIGHT_BOTTOM
					new EnumFacing[] {
							EnumFacing.UP, EnumFacing.WEST
					}      // RIGHT_TOP
			}, new EnumFacing[][] {
					// Index 4 - WEST
					new EnumFacing[] {
							EnumFacing.UP, null
					},     // TOP_EDGE
					new EnumFacing[] {
							EnumFacing.SOUTH, null
					},     // LEFT_EDGE
					new EnumFacing[] {
							EnumFacing.DOWN, null
					},     // BOTTOM_EDGE
					new EnumFacing[] {
							EnumFacing.NORTH, null
					},     // RIGHT_EDGE
					new EnumFacing[] {
							EnumFacing.UP, EnumFacing.SOUTH
					},     // LEFT_TOP
					new EnumFacing[] {
							EnumFacing.DOWN, EnumFacing.SOUTH
					},     // LEFT_BOTTOM
					new EnumFacing[] {
							EnumFacing.DOWN, EnumFacing.NORTH
					},     // RIGHT_BOTTOM
					new EnumFacing[] {
							EnumFacing.UP, EnumFacing.NORTH
					}      // RIGHT_TOP
			}, new EnumFacing[][] {
					// Index 5 - EAST
					new EnumFacing[] {
							EnumFacing.UP, null
					},     // TOP_EDGE
					new EnumFacing[] {
							EnumFacing.NORTH, null
					},     // LEFT_EDGE
					new EnumFacing[] {
							EnumFacing.DOWN, null
					},     // BOTTOM_EDGE
					new EnumFacing[] {
							EnumFacing.SOUTH, null
					},     // RIGHT_EDGE
					new EnumFacing[] {
							EnumFacing.UP, EnumFacing.NORTH
					},     // LEFT_TOP
					new EnumFacing[] {
							EnumFacing.DOWN, EnumFacing.NORTH
					},     // LEFT_BOTTOM
					new EnumFacing[] {
							EnumFacing.DOWN, EnumFacing.SOUTH
					},     // RIGHT_BOTTOM
					new EnumFacing[] {
							EnumFacing.UP, EnumFacing.SOUTH
					}      // RIGHT_TOP
			}
	};

	private EnumFacing getNextStartingFace(EnumSquare square, EnumFacing face) {

		if ((this.random.nextBoolean()) || (square == EnumSquare.FACE)) {
			return face;
		}

		EnumFacing[] possibles = Arrays.asList(congruentSquares[face.getIndex()][square.getIndex()])
				.stream().filter(it -> it != null).toArray(EnumFacing[]::new);

		if (possibles.length > 1) {
			return possibles[this.random.nextInt(possibles.length)];
		}
		return possibles[0];
	}

	private List<EnumFacing> getNextFaceSet(EnumSquare square, EnumFacing face) {
		List<EnumFacing> rv = new ArrayList<>();

		rv.add(face);

		switch (square) {
			case BOTTOM_EDGE:
				switch (face) {
					case EAST:
					case WEST:
					case NORTH:
					case SOUTH:
						rv.add(EnumFacing.DOWN);
						break;
					case UP:
					case DOWN:
						rv.add(EnumFacing.NORTH);
						break;
					default:
						break;
				}
				break;
			case TOP_EDGE:
				switch (face) {
					case EAST:
					case WEST:
					case NORTH:
					case SOUTH:
						rv.add(EnumFacing.UP);
						break;
					case UP:
					case DOWN:
						rv.add(EnumFacing.SOUTH);
						break;
					default:
						break;
				}
				break;
			case LEFT_EDGE:
				switch (face) {
					case EAST:
					case WEST:
					case NORTH:
					case SOUTH:
						rv.add(face.rotateYCCW());
						break;
					case UP:
					case DOWN:
						rv.add(EnumFacing.EAST);
						break;
					default:
						break;
				}
				break;
			case RIGHT_EDGE:
				switch (face) {
					case EAST:
					case WEST:
					case NORTH:
					case SOUTH:
						rv.add(face.rotateY());
						break;
					case UP:
					case DOWN:
						rv.add(EnumFacing.WEST);
						break;
					default:
						break;
				}
				break;
			case FACE:
				break;
			case LEFT_TOP:
				switch (face) {
					case EAST:
					case WEST:
					case NORTH:
					case SOUTH:
						rv.add(face.rotateYCCW());
						rv.add(EnumFacing.UP);
						break;
					case UP:
					case DOWN:
						rv.add(EnumFacing.EAST);
						rv.add(EnumFacing.SOUTH);
						break;
					default:
						break;
				}
				break;
			case LEFT_BOTTOM:
				switch (face) {
					case EAST:
					case WEST:
					case NORTH:
					case SOUTH:
						rv.add(face.rotateYCCW());
						rv.add(EnumFacing.DOWN);
						break;
					case UP:
					case DOWN:
						rv.add(EnumFacing.EAST);
						rv.add(EnumFacing.NORTH);
						break;
					default:
						break;
				}
				break;
			case RIGHT_TOP:
				switch (face) {
					case EAST:
					case WEST:
					case NORTH:
					case SOUTH:
						rv.add(face.rotateY());
						rv.add(EnumFacing.UP);
						break;
					case UP:
					case DOWN:
						rv.add(EnumFacing.EAST);
						rv.add(EnumFacing.SOUTH);
						break;
					default:
						break;
				}
				break;
			case RIGHT_BOTTOM:
				switch (face) {
					case EAST:
					case WEST:
					case NORTH:
					case SOUTH:
						rv.add(face.rotateY());
						rv.add(EnumFacing.DOWN);
						break;
					case UP:
					case DOWN:
						rv.add(EnumFacing.EAST);
						rv.add(EnumFacing.NORTH);
						break;
					default:
						break;
				}
				break;
			default:
				break;
		}

		return rv;
	}

	private void spawnOre(final World world, final ISpawnEntry spawnData, final BlockPos pos,
			final int quantity) {
		int count = quantity;
		final int lutType = (quantity < 8) ? offsetIndexRef_small.length : offsetIndexRef.length;
		final int[] lut = (quantity < 8) ? offsetIndexRef_small : offsetIndexRef;
		final Vec3i[] offs = new Vec3i[lutType];

		System.arraycopy((quantity < 8) ? offsets_small : offsets, 0, offs, 0, lutType);

		if (quantity < 27) {
			final int[] scrambledLUT = new int[lutType];
			System.arraycopy(lut, 0, scrambledLUT, 0, scrambledLUT.length);
			scramble(scrambledLUT, this.random);

			while (count > 0) {
				final IBlockState oreBlock = spawnData.getBlocks().getRandomBlock(random);
				if (oreBlock.getBlock().equals(net.minecraft.init.Blocks.AIR)) return;
				final BlockPos target = pos.add(offs[scrambledLUT[--count]]);
				spawn(oreBlock, world, target, world.provider.getDimension(), true, spawnData);
			}

			return;
		}

		doSpawnFill(this.random.nextBoolean(), count, world, spawnData, pos);
	}

	private void doSpawnFill(final boolean nextBoolean, final int quantity, final World world,
			final ISpawnEntry spawnData, final BlockPos pos) {
		final int count = quantity;
		final double radius = Math.pow(quantity, 1.0 / 3.0) * (3.0 / 4.0 / Math.PI) + 2;
		final int rSqr = (int) (radius * radius);
		if (nextBoolean) {
			spawnMungeNE(world, pos, rSqr, radius, spawnData, count);
		} else {
			spawnMungeSW(world, pos, rSqr, radius, spawnData, count);
		}
	}

	@Override
	public JsonObject getDefaultParameters() {
		final JsonObject defParams = new JsonObject();
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
	public void setRandom(final Random rand) {
		this.random = rand;
	}
}
