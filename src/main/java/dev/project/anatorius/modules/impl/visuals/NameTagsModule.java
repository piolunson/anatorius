package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.settings.impl.*;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.RenderOverlayEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.miscellaneous.FakePlayerModule;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.Renderer2D;
import dev.project.anatorius.utils.minecraft.EntityUtils;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.List;

@RegisterModule(name = "NameTags", description = "Replaces the default Minecraft NameTag with a more visible and customizable one.", category = Module.Category.VISUALS)
public class NameTagsModule extends Module {
    private static final Identifier ANATORIUS_LOGO = Identifier.of(Anatorius.MOD_ID, "textures/logo.png");

    public CategorySetting info  = new CategorySetting("info", "The category for settings İnfos");
    public BooleanSetting gameMode = new BooleanSetting("GameMode", "Renders the player's gamemode.", new CategorySetting.Visibility(info), false);
    public BooleanSetting ping = new BooleanSetting("Ping", "Renders the player's latency to the server.", new CategorySetting.Visibility(info), true);
    public BooleanSetting entityId = new BooleanSetting("EntityID", "Renders the player's entity ID.", new CategorySetting.Visibility(info), false);
    public BooleanSetting health = new BooleanSetting("Health", "Renders the player's health and absorption.", new CategorySetting.Visibility(info), true);
    public BooleanSetting totemPops = new BooleanSetting("TotemPops", "Renders the amount of totems that the player has popped.", new CategorySetting.Visibility(info), true);
    public BooleanSetting antiBot = new BooleanSetting("AntiBot", "Prevents bots from having nametags rendered for them.", new CategorySetting.Visibility(info), false);
    public BooleanSetting items = new BooleanSetting("Items", "Renders the items that the player is wearing or holding.", new CategorySetting.Visibility(info), true);
    public BooleanSetting enchantments = new BooleanSetting("Enchantments", "Renders the enchantments of the player's items.", new CategorySetting.Visibility(info), false);
    public BooleanSetting durability = new BooleanSetting("Durability", "Renders the durability of the player's items.", new CategorySetting.Visibility(info), true);
    public BooleanSetting itemName = new BooleanSetting("ItemName", "Renders the name of the item that the player is currently holding.", new CategorySetting.Visibility(info), true);

    public CategorySetting renderCategory = new CategorySetting("Render", "Contains all of the settings relating to position rendering.");
    public NumberSetting scale = new NumberSetting("Scale", "The scaling that will be applied to the nametag rendering.", new CategorySetting.Visibility(renderCategory), 30, 10, 100);
    public ModeSetting border = new ModeSetting("Border", "The border that will surround the text.", new CategorySetting.Visibility(renderCategory), "None", new String[]{"None", "Fill", "Outline", "Both"});
    public ColorSetting fillColor = new ColorSetting("FillColor", "The color that will be used for the fill rendering.", new ModeSetting.Visibility(border, "Fill", "Both"), ColorUtils.palette(ColorUtils.THEME_INK, 120));
    public ColorSetting outlineColor = new ColorSetting("OutlineColor", "The color that will be used for the outline rendering.", new ModeSetting.Visibility(border, "Outline", "Both"), ColorUtils.palette(ColorUtils.THEME_PRIMARY, 110));

    @SubscribeEvent
    public void onRenderOverlay(RenderOverlayEvent event) {
        if (mc.player == null || mc.world == null) return;

        DrawContext context = event.getContext();
        float tickDelta = event.getTickDelta();

        for (PlayerEntity player : mc.world.getPlayers().stream().sorted(Comparator.comparing(p -> -mc.player.distanceTo(p))).toList()) {
            if (player == mc.player) continue;
            if (antiBot.getValue() && EntityUtils.isBot(player)) continue;

            double wx = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX());
            double wy = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()) + (player.isSneaking() ? 1.9f : 2.1f);
            double wz = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ());

            float[] screen = Renderer2D.worldToScreen(wx, wy, wz);
            if (screen == null) continue;

            float distance = mc.player.distanceTo(player);
            float s = Math.max(0.45f, scale.getValue().floatValue() / 30.0f * (12.0f / (distance + 12.0f)) * 1.4f);

            String text = player.getName().getString();
            if (gameMode.getValue()) text += " [" + EntityUtils.getGameModeName(EntityUtils.getGameMode(player)) + "]";
            if (ping.getValue()) text += " " + EntityUtils.getLatency(player) + "ms";
            if (entityId.getValue()) text += " " + player.getId();
            if (health.getValue()) text += " " + ColorUtils.getHealthColor(player.getHealth() + player.getAbsorptionAmount()) + new DecimalFormat("0.0").format(player.getHealth() + player.getAbsorptionAmount()) + Formatting.RESET;

            int pops = Anatorius.WORLD_MANAGER.getPoppedTotems().getOrDefault(player.getUuid(), 0);
            if (totemPops.getValue() && pops > 0) text += " " + ColorUtils.getTotemColor(pops) + "-" + pops;

            int width = Anatorius.FONT_MANAGER.getWidth(text);
            int textH = Anatorius.FONT_MANAGER.getHeight();

            Color nameColor = Anatorius.MODULE_MANAGER.getModule(FakePlayerModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(FakePlayerModule.class).getPlayer() == player ? new Color(225, 0, 70)
                    : player.isSneaking() ? new Color(255, 170, 0)
                    : Anatorius.FRIEND_MANAGER.contains(player.getName().getString()) ? Anatorius.FRIEND_MANAGER.getDefaultFriendColor()
                    : Color.WHITE;

            context.getMatrices().pushMatrix();
            context.getMatrices().translate(screen[0], screen[1]);
            context.getMatrices().scale(s, s);

            boolean anatoriusUser = false;
            int iconSize = textH + 1;
            int rowWidth = width + (anatoriusUser ? iconSize + 2 : 0);
            int left = -rowWidth / 2;

            int textTop = -textH - 1;
            if (border.getValue().equalsIgnoreCase("Fill") || border.getValue().equalsIgnoreCase("Both"))
                Renderer2D.renderQuad(context, left - 1, textTop - 1, left + rowWidth + 2, textTop + textH, fillColor.getColor());
            if (border.getValue().equalsIgnoreCase("Outline") || border.getValue().equalsIgnoreCase("Both"))
                Renderer2D.renderOutline(context, left - 1, textTop - 1, left + rowWidth + 2, textTop + textH, outlineColor.getColor());

            Anatorius.FONT_MANAGER.drawTextWithShadow(context, text, left, textTop, nameColor);
            if (anatoriusUser) {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, ANATORIUS_LOGO, left + width + 2, textTop - 1,
                        0.0f, 0.0f, iconSize, iconSize, 64, 64, 64, 64, -1);
            }

            boolean renderedItems = false;
            int itemY = textTop - 2 - 16;
            for (int i = 0; i < 6; i++) {
                ItemStack stack = getItem(player, i);
                if (stack.isEmpty()) continue;

                int stackX = -(6 * 16) / 2 + (i * 16);

                if (items.getValue()) {
                    renderedItems = true;
                    context.drawItem(player, stack, stackX, itemY, player.getId());

                    if (stack.getItem().equals(Items.ENCHANTED_GOLDEN_APPLE)) {
                        drawSmall(context, "God", stackX, itemY - 1, new Color(255, 125, 255));
                    }
                    if (stack.getCount() != 1) {
                        String count = String.valueOf(stack.getCount());
                        drawSmall(context, count, stackX + 16 - Anatorius.FONT_MANAGER.getWidth(count) / 2, itemY + 11, Color.WHITE);
                    }
                }

                if (durability.getValue() && stack.isDamageable()) {
                    float green = (stack.getMaxDamage() - stack.getDamage()) / (float) stack.getMaxDamage();
                    int pct = Math.round(((stack.getMaxDamage() - stack.getDamage()) * 100.0f) / stack.getMaxDamage());
                    drawSmall(context, pct + "%", stackX, itemY - 5, new Color(1.0f - green, green, 0));
                }

                if (items.getValue() && enchantments.getValue() && stack.hasEnchantments()) {
                    ItemEnchantmentsComponent component = EnchantmentHelper.getEnchantments(stack);
                    Object2IntMap<RegistryEntry<Enchantment>> ench = new Object2IntOpenHashMap<>();
                    for (RegistryEntry<Enchantment> enchantment : component.getEnchantments())
                        ench.put(enchantment, component.getLevel(enchantment));

                    int h = 0;
                    for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : Object2IntMaps.fastIterable(ench)) {
                        drawSmall(context, getEnchantmentName(entry.getKey().getIdAsString(), entry.getIntValue()), stackX, itemY - 6 - h, Color.WHITE);
                        h += textH / 2;
                    }
                }
            }

            if (itemName.getValue() && !player.getMainHandStack().isEmpty()) {
                String itemText = player.getMainHandStack().getName().getString();
                int top = (renderedItems ? itemY : textTop) - 2 - textH / 2;
                drawSmall(context, itemText, -Anatorius.FONT_MANAGER.getWidth(itemText) / 4, top, Color.WHITE);
            }

            context.getMatrices().popMatrix();
        }
    }

    private void drawSmall(DrawContext context, String text, float x, float y, Color color) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(0.5f, 0.5f);
        Anatorius.FONT_MANAGER.drawTextWithShadow(context, text, 0, 0, color);
        context.getMatrices().popMatrix();
    }

    private ItemStack getItem(PlayerEntity player, int index) {
        return switch (index) {
            case 0 -> player.getMainHandStack();
            case 1 -> dev.project.anatorius.utils.minecraft.ItemUtils.getArmorItems(player).get(3);
            case 2 -> dev.project.anatorius.utils.minecraft.ItemUtils.getArmorItems(player).get(2);
            case 3 -> dev.project.anatorius.utils.minecraft.ItemUtils.getArmorItems(player).get(1);
            case 4 -> dev.project.anatorius.utils.minecraft.ItemUtils.getArmorItems(player).get(0);
            case 5 -> player.getOffHandStack();
            default -> ItemStack.EMPTY;
        };
    }

    private String getEnchantmentName(String id, int level) {
        id = id.replace("minecraft:", "");
        id = level > 1 ? id.substring(0, 2) : id.substring(0, 3);
        return id.substring(0, 1).toUpperCase() + id.substring(1) + " " + (level > 1 ? level : "");
    }

    private record ItemElement(ItemStack stack, List<String> enchantments) { }
}
