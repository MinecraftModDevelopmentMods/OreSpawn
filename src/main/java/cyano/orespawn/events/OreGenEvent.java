package cyano.orespawn.events;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.terraingen.OreGenEvent.GenerateMinable;

import java.util.Random;

public class OreGenEvent extends GenerateMinable{

	public final String modID;
	
	public OreGenEvent(World world, Random rand, BlockPos pos, String modID) {
		super(world, rand, null, pos, EventType.CUSTOM);
		this.modID = modID;
	}

}
