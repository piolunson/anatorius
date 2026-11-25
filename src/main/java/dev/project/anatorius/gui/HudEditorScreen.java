package dev.project.anatorius.gui;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.RenderOverlayEvent;
import dev.project.anatorius.gui.api.Button;
import dev.project.anatorius.gui.api.Frame;
import dev.project.anatorius.gui.impl.*;
import dev.project.anatorius.hud.HudEditor;
import dev.project.anatorius.hud.HudElement;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import dev.project.anatorius.modules.impl.core.HUDModule;
import dev.project.anatorius.settings.Setting;
import dev.project.anatorius.settings.impl.*;
import dev.project.anatorius.utils.graphics.Renderer2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class HudEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 100;
    private static final int HEADER_HEIGHT = 13;
    private static final int ROW_HEIGHT = 13;

    private final HUDModule hud;
    private final HudEditor editor;

    private final Frame context = new Frame(Module.Category.CORE, 0, 0, PANEL_WIDTH, ROW_HEIGHT);
    private final List<Button> buttons = new ArrayList<>();

    private int panelX = -1;
    private int panelY = -1;
    private boolean draggingPanel;
    private float panelGrabX, panelGrabY;

    private HudElement dragging;
    private float grabX, grabY;

    private float scroll = 0f;
    private int bodyHeight = 0;
    private int contentHeight = 0;

    public HudEditorScreen() {
        super(Text.literal(Anatorius.MOD_ID + "-hud-editor"));
        this.hud = Anatorius.MODULE_MANAGER.getModule(HUDModule.class);
        this.editor = hud.getEditor();

        context.getButtons().clear();
        context.setWidth(PANEL_WIDTH);
        context.setHeight(ROW_HEIGHT);

        for (Setting setting : hud.getEditorSettings()) {
            if (setting instanceof BooleanSetting s) buttons.add(new BooleanButton(s, context, ROW_HEIGHT));
            else if (setting instanceof NumberSetting s) buttons.add(new NumberButton(s, context, ROW_HEIGHT));
            else if (setting instanceof CategorySetting s) buttons.add(new CategoryButton(s, context, ROW_HEIGHT));
            else if (setting instanceof BindSetting s) buttons.add(new BindButton(s, context, ROW_HEIGHT));
            else if (setting instanceof ModeSetting s) buttons.add(new ModeButton(s, context, ROW_HEIGHT));
            else if (setting instanceof WhitelistSetting s) buttons.add(new WhitelistButton(s, context, ROW_HEIGHT));
            else if (setting instanceof StringSetting s) buttons.add(new StringButton(s, context, ROW_HEIGHT));
            else if (setting instanceof ColorSetting s) buttons.add(new ColorButton(s, context, ROW_HEIGHT));
        }
    }

    @Override
    public void render(DrawContext drawContext, int mouseX, int mouseY, float delta) {
        super.render(drawContext, mouseX, mouseY, delta);

        editor.setEditing(true);
        editor.clearFrame();
        Anatorius.EVENT_HANDLER.post(new RenderOverlayEvent(drawContext, delta));

        if (dragging != null && dragging.isEnabled() && dragging.isRenderedThisFrame()) {
            editor.applyDrag(dragging, mouseX - grabX, mouseY - grabY);
        } else {
            editor.idle();
        }
        editor.renderGuides(drawContext, hud.getEditorColor());

        if (draggingPanel) {
            panelX = (int) (mouseX - panelGrabX);
            panelY = (int) (mouseY - panelGrabY);
        }
        renderPanel(drawContext, mouseX, mouseY, delta);
    }

    @Override
    protected void init() {
        super.init();
        if (panelX < 0 || panelY < 0) {
            panelX = Math.round(this.width * 0.15f);
            panelY = Math.round(this.height * 0.17f);
        }
    }

    private void renderPanel(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);
        context.setX(panelX);

        int headerBottom = panelY + HEADER_HEIGHT;

        String headerMode = clickGui.headerShader.getValue();
        Color headerBase = clickGui.isRainbow()
                ? dev.project.anatorius.utils.color.ColorUtils.getOffsetRainbow(panelY * 10L)
                : clickGui.color.getColor();
        if (headerMode.equals("Fluid") || headerMode.equals("Colorful Plasma")) {
            dev.project.anatorius.utils.graphics.ClickGuiShader.getInstance().render(ctx, headerMode, panelX, panelY, PANEL_WIDTH, HEADER_HEIGHT, headerBase, delta);
        } else if (clickGui.gradient.getValue()) {
            Renderer2D.renderSidewaysGradient(ctx, panelX, panelY, panelX + PANEL_WIDTH, headerBottom, ClickGuiScreen.darken(headerBase, 0.85f), headerBase);
        } else {
            Renderer2D.renderQuad(ctx, panelX, panelY, panelX + PANEL_WIDTH, headerBottom, headerBase);
        }
        Renderer2D.renderOutline(ctx, panelX, panelY, panelX + PANEL_WIDTH, headerBottom, new Color(0, 0, 0, 90));
        Anatorius.FONT_MANAGER.drawTextWithShadow(ctx, "HUD", panelX + 3, panelY + 2, clickGui.textColor.getColor());

        contentHeight = 0;
        for (Button b : buttons) contentHeight += animatedHeight(b);

        int maxAvailable = Math.max(0, (this.height - 6) - headerBottom);
        bodyHeight = Math.min(contentHeight + 1, maxAvailable);

        Color bg = clickGui.background.getColor();
        String bodyMode = clickGui.backgroundShader.getValue();
        if ((bodyMode.equals("Fluid") || bodyMode.equals("Colorful Plasma")) && bodyHeight > 0) {
            dev.project.anatorius.utils.graphics.ClickGuiShader.getInstance().render(ctx, bodyMode, panelX, headerBottom, PANEL_WIDTH, bodyHeight, bg, delta);
        } else if (clickGui.gradient.getValue()) {
            Renderer2D.renderGradient(ctx, panelX, headerBottom, panelX + PANEL_WIDTH, headerBottom + bodyHeight, bg, ClickGuiScreen.darken(bg, 0.75f));
        } else {
            Renderer2D.renderQuad(ctx, panelX, headerBottom, panelX + PANEL_WIDTH, headerBottom + bodyHeight, bg);
        }
        Renderer2D.renderOutline(ctx, panelX, headerBottom, panelX + PANEL_WIDTH, headerBottom + bodyHeight, new Color(0, 0, 0, 70));

        ctx.enableScissor(panelX, headerBottom, panelX + PANEL_WIDTH, headerBottom + bodyHeight);
        int y = headerBottom + 1 + (int) scroll;
        for (Button button : buttons) {
            int ah = animatedHeight(button);
            if (ah <= 0) continue;
            button.setX(panelX);
            button.setY(y);
            ctx.enableScissor(panelX, y, panelX + PANEL_WIDTH, y + ah);
            button.render(ctx, mouseX, mouseY, delta);
            ctx.disableScissor();
            y += ah;
        }
        ctx.disableScissor();

        clampScroll();
    }

    private int animatedHeight(Button button) {
        Setting setting = button.getSetting();
        if (setting == null || setting.getVisibility() == null) return button.getHeight();
        setting.getVisibility().update();
        float progress = setting.getVisibility().getProgress();
        if (progress <= 0.001f) return 0;
        return Math.max(1, (int) Math.ceil(button.getHeight() * progress));
    }

    private boolean isButtonVisible(Button button) {
        Setting setting = button.getSetting();
        if (setting == null || setting.getVisibility() == null) return true;
        setting.getVisibility().update();
        return setting.getVisibility().isVisible();
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, (contentHeight + 1) - bodyHeight);
        if (scroll < -maxScroll) scroll = -maxScroll;
        if (scroll > 0) scroll = 0;
    }

    private boolean overHeader(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= panelY && mouseY <= panelY + HEADER_HEIGHT;
    }

    private boolean overBody(double mouseX, double mouseY) {
        return mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH
                && mouseY > panelY + HEADER_HEIGHT && mouseY <= panelY + HEADER_HEIGHT + bodyHeight;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();
        if (overHeader(mouseX, mouseY)) {
            if (button == 0) {
                draggingPanel = true;
                panelGrabX = (float) (mouseX - panelX);
                panelGrabY = (float) (mouseY - panelY);
            }
            return true;
        }

        if (overBody(mouseX, mouseY)) {
            for (Button b : buttons) {
                if (isButtonVisible(b)) b.mouseClicked(mouseX, mouseY, button);
            }
            return true;
        }

        HudElement element = editor.elementAt(mouseX, mouseY);
        if (element != null) {
            if (button == 0) {
                dragging = element;
                grabX = (float) (mouseX - element.getRenderX());
                grabY = (float) (mouseY - element.getRenderY());
            } else if (button == 1) {
                element.resetPosition();
            }
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();
        if (button == 0) {
            dragging = null;
            draggingPanel = false;
        }
        for (Button b : buttons) b.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double deltaX, double deltaY) {
        double mouseX = click.x(), mouseY = click.y();
        int button = click.button();
        for (Button b : buttons) b.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (overHeader(mouseX, mouseY) || overBody(mouseX, mouseY)) {
            scroll += verticalAmount * 12;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        for (Button b : buttons) b.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        char chr = (char) input.codepoint();
        int modifiers = input.modifiers();
        for (Button b : buttons) b.charTyped(chr, modifiers);
        return super.charTyped(input);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (dev.project.anatorius.modules.impl.visuals.BlurModule.active()) applyBlur(context);
        Renderer2D.renderQuad(context, 0, 0, this.width, this.height, new Color(0, 0, 0, 120));
    }

    @Override
    public void close() {
        editor.setEditing(false);
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
