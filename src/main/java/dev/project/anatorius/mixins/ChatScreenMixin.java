package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.ChatInputEvent;
import dev.project.anatorius.events.impl.CommandInputEvent;
import dev.project.anatorius.modules.impl.core.HUDModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin extends Screen {
    @Shadow
    protected TextFieldWidget chatField;

    @Unique
    private float anatorius$prefixOutlineProgress = 0.0f;

    @Unique
    private long anatorius$prefixOutlineLastMs = Util.getMeasuringTimeMs();

    protected ChatScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "sendMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendChatMessage(Ljava/lang/String;)V"), cancellable = true)
    private void sendMessage(String chatText, boolean addToHistory, CallbackInfo info) {
        ChatInputEvent event = new ChatInputEvent(chatText);
        Anatorius.EVENT_HANDLER.post(event);
        if (event.isCancelled()) info.cancel();
    }

    @Inject(method = "sendMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendChatCommand(Ljava/lang/String;)V"), cancellable = true)
    private void sendCommand(String chatText, boolean addToHistory, CallbackInfo info) {
        CommandInputEvent event = new CommandInputEvent(chatText);
        Anatorius.EVENT_HANDLER.post(event);
        if (event.isCancelled()) info.cancel();
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"))
    private boolean render(DrawContext instance, int x1, int y1, int x2, int y2, int color) {
        return !Anatorius.MODULE_MANAGER.getModule(HUDModule.class).isToggled();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderPrefixOutline(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (chatField == null) return;

        String prefix = Anatorius.COMMAND_MANAGER.getPrefix();
        String text = chatField.getText();
        boolean active = text != null && !prefix.isEmpty() && text.startsWith(prefix);

        long now = Util.getMeasuringTimeMs();
        float dt = Math.max(0.0f, (now - anatorius$prefixOutlineLastMs) / 1000.0f);
        anatorius$prefixOutlineLastMs = now;

        float speed = 5.0f;
        float target = active ? 1.0f : 0.0f;
        anatorius$prefixOutlineProgress += (target - anatorius$prefixOutlineProgress) * Math.min(1.0f, dt * speed);

        float p = anatorius$prefixOutlineProgress;
        if (p < 0.01f) return;

        float eased = p * p * (3.0f - 2.0f * p);

        int x = chatField.getX();
        int y = chatField.getY();
        int w = chatField.getWidth();
        int h = chatField.getHeight();

        float time = (now % 6000L) / 6000.0f;
        int rgb = java.awt.Color.HSBtoRGB(time, 1.0f, 1.0f) & 0x00FFFFFF;

        int alpha = Math.min(255, Math.max(0, (int) (200.0f * eased)));
        int color = (alpha << 24) | rgb;

        int thickness = 1;
        int pad = 3;

        int x1 = x - pad;
        int y1 = y - pad;
        int x2 = x + w + pad;
        int y2 = y + h + pad;

        context.fill(x1, y1, x2, y1 + thickness, color);
        context.fill(x1, y2 - thickness, x2, y2, color);
        context.fill(x1, y1, x1 + thickness, y2, color);
        context.fill(x2 - thickness, y1, x2, y2, color);
    }
}
