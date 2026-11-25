package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.miscellaneous.NameProtectModule;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.text.CustomFormatting;
import dev.project.anatorius.utils.text.FormattingUtils;
import net.minecraft.text.Style;
import net.minecraft.text.TextVisitFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
@Mixin(TextVisitFactory.class)
public class TextVisitFactoryMixin implements IMinecraft {
    @WrapOperation(method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z", at = @At(value = "INVOKE", target = "Ljava/lang/String;charAt(I)C", ordinal = 1))
    private static char visitFormatted(String instance, int index, Operation<Character> original, @Local(ordinal = 2) LocalRef<Style> style) {
        CustomFormatting customFormatting = CustomFormatting.byCode(instance.charAt(index));
        if (customFormatting != null) style.set(FormattingUtils.withExclusiveFormatting(style.get(), customFormatting));
        return original.call(instance, index);
    }

    @ModifyVariable(method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private static String replaceText(String value) {
        if (value == null) return null;
        if (mc == null || mc.getSession() == null) return value;
        if (Anatorius.MODULE_MANAGER == null) return value;

        NameProtectModule module = Anatorius.MODULE_MANAGER.getModule(NameProtectModule.class);
        if (module == null || !module.isToggled()) return value;

        String username = mc.getSession().getUsername();
        String replacement = module.name.getValue();
        if (username == null || username.isEmpty() || replacement == null || !value.contains(username)) return value;

        String sized;
        if (replacement.length() >= username.length()) {
            sized = replacement.substring(0, username.length());
        } else {
            StringBuilder sb = new StringBuilder(replacement);
            while (sb.length() < username.length()) sb.append(' ');
            sized = sb.toString();
        }
        return value.replace(username, sized);
    }
}
