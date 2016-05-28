package cyano.basemetals.events;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import cyano.basemetals.OreSpawn;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.Event.Result;

public class OreGenDisabler {

	


	@SubscribeEvent(priority=EventPriority.HIGHEST) 	
	public void handleOreGenEvent(net.minecraftforge.event.terraingen.OreGenEvent event){
		if(event instanceof OreGenEvent){
			if(!((OreGenEvent)event).modID.equals(OreSpawn.MODID)){
				// other mod or vanilla
				event.setResult(Result.DENY);
			}
		} else {
			// other mod or vanilla
			event.setResult(Result.DENY);
		}
	}
	
	
	
	private OreGenDisabler(){
		// do nothing
	}
	
	private static OreGenDisabler instance = null;
	
	private static final Lock initLock = new ReentrantLock();

	public static OreGenDisabler getInstance(){
		if(instance == null){
			initLock.lock();
			try{
				if(instance == null){instance = new OreGenDisabler();}
			} finally{
				initLock.unlock();
			}
		}
		return instance;
	}
}
