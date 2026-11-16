package zone.moddev.mc.orespawn.commands.admin;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

/** Keeps translated command failures intact until Brigadier displays them. */
final class AdminFailure extends RuntimeException {
    final ITextComponent message;
    AdminFailure(String key, Object... arguments) {
        super(key);
        message = new TranslationTextComponent(key, arguments);
    }
}
