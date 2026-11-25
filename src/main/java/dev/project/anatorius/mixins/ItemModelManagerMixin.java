package dev.project.anatorius.mixins;

import dev.project.anatorius.modules.impl.visuals.Items3DModule;
import dev.project.anatorius.utils.items3d.Items3DRenderer;
import dev.project.anatorius.utils.mixins.ICrystalItemState;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.HeldItemContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelManager.class)
public class ItemModelManagerMixin {
    @Inject(method = "clearAndUpdate", at = @At("TAIL"))
    private void clearAndUpdate$items3d(ItemRenderState state, ItemStack stack, ItemDisplayContext context, World world,
                                        HeldItemContext heldItemContext, int seed, CallbackInfo ci) {
        if (context.isFirstPerson() || !stack.isOf(Items.END_CRYSTAL)) return;
        Items3DModule m = Items3DModule.active();
        if (m == null || !m.crystalShownIn(context) || !Items3DRenderer.INSTANCE.crystalEligible(m, state)) return;
        ((ICrystalItemState) state).anatorius$setCrystal(true);
        state.markAnimated();
        state.addModelKey(Items3DRenderer.CRYSTAL_MODEL_KEY);
    }
}
