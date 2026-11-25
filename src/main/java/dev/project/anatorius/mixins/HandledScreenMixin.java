package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.miscellaneous.ShulkerInfoModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import dev.project.anatorius.utils.items3d.InventoryAnimator;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.screen.slot.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin implements IMinecraft {
    @Shadow @Nullable protected Slot focusedSlot;
    @Shadow @Final protected ScreenHandler handler;
    @Shadow protected int x;
    @Shadow protected int y;

    @Inject(method = "renderMain", at = @At("HEAD"))
    private void renderMain$items3d(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        InventoryAnimator.INSTANCE.frame(handler, focusedSlot, x, y);
    }

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void drawSlot$items3dHead(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        InventoryAnimator.INSTANCE.beginSlot(context, slot);
    }

    @Inject(method = "drawSlot", at = @At("RETURN"))
    private void drawSlot$items3dReturn(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        InventoryAnimator.INSTANCE.endSlot(context);
    }

    @Inject(method = "renderCursorStack", at = @At("HEAD"))
    private void renderCursorStack$items3dHead(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        InventoryAnimator.INSTANCE.beginCursor(context, mouseX, mouseY);
    }

    @Inject(method = "renderCursorStack", at = @At("RETURN"))
    private void renderCursorStack$items3dReturn(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        InventoryAnimator.INSTANCE.endCursor(context);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ShulkerInfoModule shulkerInfoModule = Anatorius.MODULE_MANAGER.getModule(ShulkerInfoModule.class);

        if(!shulkerInfoModule.isToggled()) return;

        if(focusedSlot != null && !focusedSlot.getStack().isEmpty() && mc.player.playerScreenHandler.getCursorStack().isEmpty() && shulkerInfoModule.hasItems(focusedSlot.getStack())) {
            shulkerInfoModule.renderInfo(context, mouseX, mouseY, focusedSlot.getStack());
        }
    }

    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
    private void drawMouseoverTooltip(DrawContext drawContext, int x, int y, CallbackInfo ci) {
        ShulkerInfoModule shulkerInfoModule = Anatorius.MODULE_MANAGER.getModule(ShulkerInfoModule.class);

        if(!shulkerInfoModule.isToggled()) return;

        if(focusedSlot != null && !focusedSlot.getStack().isEmpty() && mc.player.playerScreenHandler.getCursorStack().isEmpty() && shulkerInfoModule.hasItems(focusedSlot.getStack())) {
            ci.cancel();
        }
    }
}
