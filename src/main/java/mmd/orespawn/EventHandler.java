package mmd.orespawn;

import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public enum EventHandler {
    INSTANCE;

    @SubscribeEvent
    public void onGenerateMinable(OreGenEvent.GenerateMinable event) {
        event.setResult(Event.Result.DENY);
    }
}
