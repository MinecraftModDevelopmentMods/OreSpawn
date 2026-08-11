package cyano.orespawn.events;

/** Deprecated OS1 hook; OS4 applies vanilla suppression in its coordinator. */
@Deprecated
public class OreGenDisabler {
	private static final OreGenDisabler INSTANCE = new OreGenDisabler();
	public void handleOreGenEvent(net.minecraftforge.event.terraingen.OreGenEvent event) { }
	public static OreGenDisabler getInstance() { return INSTANCE; }
}
