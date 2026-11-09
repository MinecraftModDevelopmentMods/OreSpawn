package cyano.orespawn.events;

import java.util.Random;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Deprecated OreSpawn 1.x custom ore-generation event. */
@Deprecated
public class OreGenEvent extends net.minecraftforge.event.terraingen.OreGenEvent.GenerateMinable {
	public final String modID;
	public OreGenEvent(World world, Random random, BlockPos position, String modID) {
		super(world, random, null, position, EventType.CUSTOM);
		this.modID = modID;
	}
}
