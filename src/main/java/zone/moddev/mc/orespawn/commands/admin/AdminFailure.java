package zone.moddev.mc.orespawn.commands.admin;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/** Keeps translated command failures intact until Brigadier displays them. */
final class AdminFailure extends RuntimeException {
    final ITextComponent message;
    AdminFailure(String key, Object... arguments) {
        super(key);
        message = new TextComponentTranslation(key, arguments);
    }
}
