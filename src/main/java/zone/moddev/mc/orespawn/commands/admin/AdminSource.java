package zone.moddev.mc.orespawn.commands.admin;

import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.WorldServer;

/** The small part of a command source that a running diagnostic job needs. */
public interface AdminSource {
    String getName();
    Entity getCommandSenderEntity();
    WorldServer getEntityWorld();
    BlockPos getPosition();
    boolean canUseCommand(int level, String command);
    void sendMessage(ITextComponent message);

    static AdminSource from(CommandSource source) {
        return new AdminSource() {
            public String getName() { return source.getName(); }
            public Entity getCommandSenderEntity() { return source.getEntity(); }
            public WorldServer getEntityWorld() { return source.getWorld(); }
            public BlockPos getPosition() { return new BlockPos(source.getPos()); }
            public boolean canUseCommand(int level, String command) { return source.hasPermissionLevel(level); }
            public void sendMessage(ITextComponent message) { source.sendFeedback(message, false); }
        };
    }
}
