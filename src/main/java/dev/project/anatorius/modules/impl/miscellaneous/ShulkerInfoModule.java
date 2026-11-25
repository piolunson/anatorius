package dev.project.anatorius.modules.impl.miscellaneous;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.utils.graphics.Renderer2D;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.*;
import org.lwjgl.opengl.GL11;

import java.awt.*;

@RegisterModule(name = "ShulkerInfo", description = "Renders a preview of the contents of the shulker you are hovering.", category = Module.Category.MISCELLANEOUS)
public class ShulkerInfoModule extends Module {
    public void renderInfo(DrawContext context, int x, int y, ItemStack itemStack) {
        try {
            ContainerComponent component = itemStack.get(DataComponentTypes.CONTAINER);
            Item item = itemStack.getItem();
            Color color = Color.WHITE;

            if(item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock shulker) {
                try {
                    color = new Color(shulker.getColor().getEntityColor());
                } catch (Exception ignored) { }
            }
            x += 4;
            y -= 62 + Anatorius.FONT_MANAGER.getHeight();

            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

            Renderer2D.renderQuad(context, x, y, x + 164, y + 60 + Anatorius.FONT_MANAGER.getHeight(), new Color(0, 0, 0, 200));
            Renderer2D.renderOutline(context, x, y, x + 164, y + 60 + Anatorius.FONT_MANAGER.getHeight(), color);

            Anatorius.FONT_MANAGER.drawTextWithShadow(context, itemStack.getName().getString(), x + 3, y + 3, Color.WHITE);

            int row = 0, i = 0;
            for(ItemStack stack : component.stream().toList()) {
                int offsetX = x + 1 + i * 18, offsetY = y + Anatorius.FONT_MANAGER.getHeight() + 5 + row * 18;
                context.drawItem(stack, offsetX, offsetY);
                context.drawStackOverlay(mc.textRenderer, stack, offsetX, offsetY);
                i++;
                if(i>=9) {
                    i = 0;
                    row++;
                }
            }
        } catch (Exception ignored) { }
    }

    public boolean hasItems(ItemStack itemStack) {
        ContainerComponent component = itemStack.get(DataComponentTypes.CONTAINER);
        return component != null && !component.stream().toList().isEmpty();
    }
}
