package dev.project.anatorius.gui;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import dev.project.anatorius.settings.impl.WhitelistSetting;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.ClickGuiShader;
import dev.project.anatorius.utils.graphics.Renderer2D;
import net.minecraft.block.Block;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class WhitelistScreen extends Screen {
    private static final int WIDTH = 112, HEADER = 14, ROW = 14, MAX_ROWS = 26, MAX_QUERY = 32;

    private final WhitelistSetting setting;
    private final Screen parent;
    private final List<Entry> entries;
    private List<Entry> visible = List.of();

    private String query = "";
    private String builtQuery = null;

    private int fx, fy, bodyTop, bodyHeight;
    private boolean dragging;
    private int dragX, dragY;

    private float scroll, targetScroll;
    private Boolean paintState;

    public WhitelistScreen(WhitelistSetting setting, Screen parent) {
        super(Text.literal(Anatorius.MOD_ID + "-whitelist"));
        this.setting = setting;
        this.parent = parent;
        this.entries = buildEntries(setting.getType());
        this.fx = (net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledWidth() - WIDTH) / 2;
        this.fy = 12;
    }

    private record Entry(Object value, String id, String name, String search, ItemStack icon) {
    }

    private static List<Entry> buildEntries(WhitelistSetting.Type type) {
        List<Entry> list = new ArrayList<>();
        switch (type) {
            case BLOCKS -> {
                for (Block block : Registries.BLOCK) {
                    if (block.getDefaultState().isAir()) continue;
                    Item item = block.asItem();
                    String id = Registries.BLOCK.getId(block).toString();
                    boolean variant = item instanceof net.minecraft.item.BlockItem bi && bi.getBlock() != block;
                    add(list, block, id, variant ? pretty(id) : block.getName().getString(),
                            item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
                }
            }
            case MODELS -> {
                dev.project.anatorius.utils.playermodels.PlayerModels.scanLocal(true);
                for (var model : dev.project.anatorius.utils.playermodels.PlayerModels.all()) {
                    ItemStack icon = new ItemStack(model.local() ? Items.ARMOR_STAND : model.id().equals("terrorist") ? Items.TNT
                            : model.id().equals("counter_terrorist") ? Items.SHIELD : model.back() != null ? Items.CLOCK : Items.PLAYER_HEAD);
                    list.add(new Entry(model.id(), model.id(), model.name(), (model.name() + " " + model.id().replace('_', ' ')).toLowerCase(Locale.ROOT), icon));
                }
                return list;
            }
            case SOUNDS -> {
                for (String name : dev.project.anatorius.utils.minecraft.ClientSounds.list()) {
                    String pretty = dev.project.anatorius.utils.minecraft.ClientSounds.pretty(name);
                    list.add(new Entry(name, name, pretty, (pretty + " " + name).toLowerCase(Locale.ROOT), new ItemStack(Items.NOTE_BLOCK)));
                }
                return list;
            }
            case EFFECTS -> {
                for (net.minecraft.entity.effect.StatusEffect effect : Registries.STATUS_EFFECT) {
                    add(list, effect, Registries.STATUS_EFFECT.getId(effect).toString(), effect.getName().getString(), new ItemStack(Items.POTION));
                }
            }
            case POTIONS -> {
                for (Potion potion : Registries.POTION) {
                    ItemStack stack = PotionContentsComponent.createStack(Items.POTION, Registries.POTION.getEntry(potion));
                    add(list, potion, Registries.POTION.getId(potion).toString(), stack.getName().getString(), stack);
                }
            }
            default -> {
                for (Item item : Registries.ITEM) {
                    if (item == Items.AIR) continue;
                    add(list, item, Registries.ITEM.getId(item).toString(), item.getName().getString(), new ItemStack(item));
                }
            }
        }
        list.sort(Comparator.comparing((Entry e) -> e.name.toLowerCase(Locale.ROOT)).thenComparing(Entry::id));
        return list;
    }

    private static void add(List<Entry> list, Object value, String id, String name, ItemStack icon) {
        String path = id.startsWith("minecraft:") ? id.substring(10) : id;
        list.add(new Entry(value, id, name, (name + " " + path.replace('_', ' ') + " " + path).toLowerCase(Locale.ROOT), icon));
    }

    private static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String word : id.substring(id.indexOf(':') + 1).split("_")) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private boolean selected(Entry e) {
        return setting.isWhitelistContains(e.value);
    }

    private void set(Entry e, boolean on) {
        if (on == selected(e)) return;
        if (on && sounds()) setting.clear();
        if (on) setting.add(e.value);
        else setting.remove(e.value);
    }

    private boolean sounds() {
        return setting.getType() == WhitelistSetting.Type.SOUNDS;
    }

    private void refilter() {
        if (query.equals(builtQuery)) return;
        builtQuery = query;
        String[] words = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
        String first = words.length == 0 ? "" : words[0];
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            boolean match = true;
            for (String w : words) if (!w.isEmpty() && !e.search.contains(w)) { match = false; break; }
            if (match) out.add(e);
        }
        if (first.isEmpty()) out.sort(Comparator.comparingInt((Entry e) -> selected(e) ? 0 : 1));
        else out.sort(Comparator.comparingInt((Entry e) -> e.name.toLowerCase(Locale.ROOT).startsWith(first) ? 0 : 1));
        visible = out;
        scroll = targetScroll = 0;
    }

    private void layout() {
        bodyTop = fy + HEADER;
        int screenH = this.height;
        int maxBody = Math.max(ROW, Math.min(MAX_ROWS * ROW, screenH - 6 - bodyTop));
        bodyHeight = Math.min(Math.max(1, visible.size()) * ROW + 1, maxBody);
    }

    private float maxScroll() {
        return Math.max(0, visible.size() * ROW + 1 - bodyHeight);
    }

    private boolean overHeader(double mx, double my) {
        return mx >= fx && mx < fx + WIDTH && my >= fy && my < bodyTop;
    }

    private boolean overFrame(double mx, double my) {
        return mx >= fx && mx < fx + WIDTH && my >= fy && my < bodyTop + bodyHeight;
    }

    private Entry rowAt(double mx, double my) {
        if (mx < fx || mx >= fx + WIDTH || my < bodyTop || my >= bodyTop + bodyHeight) return null;
        int index = (int) ((my - bodyTop - 1 + scroll) / ROW);
        return index >= 0 && index < visible.size() ? visible.get(index) : null;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);
        double mx = mouseX, my = mouseY;
        if (dragging) {
            fx = (int) mx - dragX;
            fy = (int) my - dragY;
        }
        refilter();
        layout();
        targetScroll = Math.max(0, Math.min(maxScroll(), targetScroll));
        scroll += (targetScroll - scroll) * 0.35f;
        if (Math.abs(targetScroll - scroll) < 0.3f) scroll = targetScroll;

        var m = context.getMatrices();

        Color accent = clickGui.isRainbow() ? ColorUtils.getOffsetRainbow(fy * 10L) : clickGui.color.getColor();

        String headerMode = clickGui.headerShader.getValue();
        if (headerMode.equals("Fluid") || headerMode.equals("Colorful Plasma")) {
            ClickGuiShader.getInstance().render(context, headerMode, fx, fy, WIDTH, HEADER, accent, delta);
        } else if (clickGui.gradient.getValue()) {
            Renderer2D.renderSidewaysGradient(context, fx, fy, fx + WIDTH, bodyTop, ClickGuiScreen.darken(accent, 0.85f), accent);
        } else {
            Renderer2D.renderQuad(context, fx, fy, fx + WIDTH, bodyTop, accent);
        }

        Color text = clickGui.textColor.getColor();
        String count = String.valueOf(setting.getWhitelist().size());
        int countX = fx + WIDTH - 3 - Anatorius.FONT_MANAGER.getWidth(count);
        Anatorius.FONT_MANAGER.drawTextWithShadow(context, count, countX, fy + 2, new Color(text.getRed(), text.getGreen(), text.getBlue(), 170));
        int textX = fx + 3;
        if (query.isEmpty()) {
            Anatorius.FONT_MANAGER.drawTextWithShadow(context, "Search...", textX, fy + 2, new Color(text.getRed(), text.getGreen(), text.getBlue(), 150));
        } else {
            String shown = query;
            while (Anatorius.FONT_MANAGER.getWidth(shown) > countX - 6 - textX && shown.length() > 1) shown = shown.substring(1);
            Anatorius.FONT_MANAGER.drawTextWithShadow(context, shown, textX, fy + 2, text);
            textX += Anatorius.FONT_MANAGER.getWidth(shown);
        }
        if ((System.currentTimeMillis() / 400) % 2 == 0)
            Renderer2D.renderQuad(context, textX + (query.isEmpty() ? -1 : 1), fy + 3, textX + (query.isEmpty() ? 0 : 2), bodyTop - 3, text);

        Color bg = clickGui.background.getColor();
        String bodyMode = clickGui.backgroundShader.getValue();
        if (bodyMode.equals("Fluid") || bodyMode.equals("Colorful Plasma")) {
            ClickGuiShader.getInstance().render(context, bodyMode, fx, bodyTop, WIDTH, bodyHeight, bg, delta);
        } else if (clickGui.gradient.getValue()) {
            Renderer2D.renderGradient(context, fx, bodyTop, fx + WIDTH, bodyTop + bodyHeight, bg, ClickGuiScreen.darken(bg, 0.75f));
        } else {
            Renderer2D.renderQuad(context, fx, bodyTop, fx + WIDTH, bodyTop + bodyHeight, bg);
        }

        context.enableScissor(fx, bodyTop, fx + WIDTH, bodyTop + bodyHeight);
        Entry hovered = dragging ? null : rowAt(mx, my);
        int first = Math.max(0, (int) (scroll / ROW));
        int last = Math.min(visible.size() - 1, (int) ((scroll + bodyHeight) / ROW));
        for (int i = first; i <= last; i++) {
            Entry e = visible.get(i);
            int y = (int) (bodyTop + 1 + i * ROW - scroll);
            boolean on = selected(e);
            Color base = ClickGuiScreen.getModuleButtonColor(y, on);
            if (clickGui.gradient.getValue())
                Renderer2D.renderSidewaysGradient(context, fx, y, fx + WIDTH, y + ROW, ClickGuiScreen.darken(base, 0.85f), base);
            else
                Renderer2D.renderQuad(context, fx, y, fx + WIDTH, y + ROW, base);
            if (e == hovered) Renderer2D.renderQuad(context, fx, y, fx + WIDTH, y + ROW, new Color(255, 255, 255, 18));

            if (!e.icon.isEmpty()) {
                m.pushMatrix();
                m.translate(fx + 3, y + 1);
                m.scale(0.75f, 0.75f);
                context.drawItemWithoutEntity(e.icon, 0, 0);
                m.popMatrix();
            }
            int nameX = fx + 18;
            Anatorius.FONT_MANAGER.drawTextWithShadow(context, ellipsize(e.name, fx + WIDTH - 3 - nameX), nameX, y + 2,
                    on ? clickGui.enabledTextColor.getColor() : text);
        }
        if (visible.isEmpty())
            Anatorius.FONT_MANAGER.drawTextWithShadow(context, sounds() && query.isEmpty() ? "No sounds" : "No results", fx + 3, bodyTop + 3, new Color(text.getRed(), text.getGreen(), text.getBlue(), 150));
        context.disableScissor();

        Renderer2D.renderOutline(context, fx, fy, fx + WIDTH, bodyTop + bodyHeight, accent);
        Renderer2D.renderQuad(context, fx, bodyTop - 1, fx + WIDTH, bodyTop, accent);
    }

    private static String ellipsize(String s, int max) {
        if (Anatorius.FONT_MANAGER.getWidth(s) <= max) return s;
        int dots = Anatorius.FONT_MANAGER.getWidth("…");
        int end = s.length();
        while (end > 0 && Anatorius.FONT_MANAGER.getWidth(s.substring(0, end)) + dots > max) end--;
        return s.substring(0, end).stripTrailing() + "…";
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (dev.project.anatorius.modules.impl.visuals.BlurModule.active()) applyBlur(context);
        Renderer2D.renderQuad(context, 0, 0, this.width, this.height, new Color(0, 0, 0, 120));
    }

    private void clickSound() {
        ClickGuiModule.playSound();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (!overFrame(mx, my) || click.button() != 0) return true;
        if (overHeader(mx, my)) {
            dragging = true;
            dragX = (int) mx - fx;
            dragY = (int) my - fy;
            return true;
        }
        Entry e = rowAt(mx, my);
        if (e != null) {
            boolean on = !selected(e);
            set(e, on);
            if (sounds()) {
                if (on) dev.project.anatorius.utils.minecraft.ClientSounds.play((String) e.value, 1.0f);
                else clickSound();
            } else {
                paintState = on;
                clickSound();
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (paintState != null) {
            Entry e = rowAt(click.x(), click.y());
            if (e != null) set(e, paintState);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        paintState = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int speed = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class).scrollSpeed.getValue().intValue();
        targetScroll = Math.max(0, Math.min(maxScroll(), targetScroll - (float) verticalAmount * speed));
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        boolean ctrl = (input.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!query.isEmpty()) query = "";
            else close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (ctrl) query = "";
            else if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_V && ctrl) {
            String clip = client.keyboard.getClipboard();
            if (clip != null) {
                query += clip.replaceAll("\\s+", " ");
                if (query.length() > MAX_QUERY) query = query.substring(0, MAX_QUERY);
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        int cp = input.codepoint();
        if (cp >= 32 && cp != 127 && query.length() < MAX_QUERY) {
            query += new String(Character.toChars(cp));
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
