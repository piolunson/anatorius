package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.core.CommandsModule;
import dev.project.anatorius.modules.impl.core.HUDModule;
import dev.project.anatorius.modules.impl.miscellaneous.BetterChatModule;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.animations.Easing;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.mixins.IChatHudLineVisible;
import dev.project.anatorius.utils.graphics.Renderer2D;
import dev.project.anatorius.utils.text.CustomFormatting;
import net.minecraft.util.Identifier;
import dev.project.anatorius.utils.text.FormattingUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.Profilers;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import dev.project.anatorius.utils.graphics.BetterChatBackend;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Mixin(value = ChatHud.class, priority = 999)
public abstract class ChatHudMixin<E> implements IMinecraft {
    @Shadow protected abstract boolean isChatHidden();

    @Shadow public abstract int getVisibleLineCount();

    @Shadow @Final public List<ChatHudLine.Visible> visibleMessages;

    @Shadow public abstract double getChatScale();

    @Shadow public abstract int getWidth();

    @Shadow @Final private MinecraftClient client;

    @Shadow protected abstract int getLineHeight();

    @Shadow private int scrolledLines;

    @Shadow private boolean hasUnreadNewMessages;

    @ModifyVariable(method = "render(Lnet/minecraft/client/gui/hud/ChatHud$Backend;IIZ)V", at = @At("HEAD"), argsOnly = true)
    private ChatHud.Backend wrapBackend(ChatHud.Backend backend) {
        BetterChatModule module = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(BetterChatModule.class);
        return module != null && module.isToggled() ? new BetterChatBackend(backend, module) : backend;
    }

    @Unique
    private OrderedText processOrderedText(OrderedText orderedText) {
        MutableText builder = Text.empty();

        int[] i = {0};
        orderedText.accept((index, style, codePoint) -> {
            MutableText text = Text.empty();

            if (style.getColor() != null && (style.getColor().toString().toLowerCase().equals(CustomFormatting.CLIENT.getName()) || style.getColor().toString().toLowerCase().equals(CustomFormatting.RAINBOW.getName()))) {
                if (style.getColor().toString().toLowerCase().equals(CustomFormatting.CLIENT.getName())) {
                    text.append(Text.literal(String.valueOf(Character.toChars(codePoint))).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(ColorUtils.getGlobalColor().getRGB()))));
                }

                if (style.getColor().toString().toLowerCase().equals(CustomFormatting.RAINBOW.getName())) {
                    long index1 = (long) i[0] * (Anatorius.MODULE_MANAGER.getModule(HUDModule.class).rainbowOffset.getValue().longValue() * 5L);
                    Color color = ColorUtils.getOffsetRainbow(index1);
                    text.append(Text.literal(String.valueOf(Character.toChars(codePoint))).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color.getRGB()))));
                }
            } else {
                text.append(Text.literal(String.valueOf(Character.toChars(codePoint))).setStyle(style));
            }

            builder.append(text);
            i[0]++;

            return true;
        });

        return builder.asOrderedText();
    }

    @ModifyArgs(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/ChatHudLine;<init>(ILnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V"))
    private void addMessage(Args args, Text message, MessageSignatureData signature, MessageIndicator indicator) {
        BetterChatModule module = Anatorius.MODULE_MANAGER.getModule(BetterChatModule.class);
        if (module.isToggled() && module.timestamps.getValue()) {
            args.set(1, Text.literal("").append(Text.literal(FormattingUtils.getFormatting(Anatorius.MODULE_MANAGER.getModule(CommandsModule.class).secondaryWatermarkColor.getValue()) + module.opening.getValue() + FormattingUtils.getFormatting(Anatorius.MODULE_MANAGER.getModule(CommandsModule.class).primaryWatermarkColor.getValue()) + new SimpleDateFormat("HH:mm").format(new Date()) + FormattingUtils.getFormatting(Anatorius.MODULE_MANAGER.getModule(CommandsModule.class).secondaryWatermarkColor.getValue()) + module.closing.getValue() + Formatting.RESET + " ")).append(message));
        }
    }
}
